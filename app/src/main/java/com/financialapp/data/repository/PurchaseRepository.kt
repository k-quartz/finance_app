package com.financialapp.data.repository

import com.financialapp.data.local.dao.PurchaseDao
import com.financialapp.data.local.dao.ProductDao
import com.financialapp.data.local.dao.CategoryDao
import com.financialapp.data.local.dao.TransactionDao
import com.financialapp.data.local.entity.PurchaseEntity
import com.financialapp.data.local.entity.ProductEntity
import com.financialapp.data.local.entity.CategoryEntity
import com.financialapp.domain.model.Money
import com.financialapp.domain.model.Purchase
import com.financialapp.domain.model.PurchaseSummary
import com.financialapp.domain.model.Quantity
import com.financialapp.domain.validation.FinancialValidators
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface PurchaseRepository {
    suspend fun create(purchase: Purchase): Long
    suspend fun getPurchasesForProduct(productId: Long): List<Purchase>
    fun observePurchasesForDateRange(startDay: Long, endDay: Long): Flow<List<Purchase>>
    suspend fun getById(purchaseId: Long): Purchase?
    suspend fun summarize(startDate: LocalDate, endDate: LocalDate, productId: Long? = null): PurchaseSummary
    suspend fun softDelete(purchaseId: Long, deletedAtEpochMs: Long, updatedAtEpochMs: Long): Int
}

class LocalPurchaseRepository(
    private val purchaseDao: PurchaseDao,
    private val productDao: ProductDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
) : PurchaseRepository {
    override suspend fun create(purchase: Purchase): Long {
        val product = productDao.getById(purchase.productId)?.toDomain()
            ?: throw IllegalArgumentException("Product does not exist")
        val category = purchase.categoryId?.let { categoryId ->
            categoryDao.getById(categoryId)?.toDomain()
                ?: throw IllegalArgumentException("Category does not exist")
        }
        if (purchase.transactionId != null) {
            val transaction = transactionDao.getById(purchase.transactionId)
                ?: throw IllegalArgumentException("Purchase transaction does not exist")
            require(transaction.transactionType.name == "EXPENSE") { "Purchase transaction must be an expense" }
            require(transaction.amountMinor == purchase.totalAmount.minorUnits) { "Purchase total must match transaction amount" }
            require(transaction.currencyCode == purchase.totalAmount.currencyCode) { "Purchase currency must match transaction currency" }
        }
        val ancestors = category?.let { categoryAncestors(it.parentId) } ?: emptyList()
        FinancialValidators.validatePurchase(purchase, product, category, ancestors)
        val now = purchase.createdAt.toEpochMilli()
        return purchaseDao.insert(purchase.toEntity(now))
    }

    override suspend fun getPurchasesForProduct(productId: Long): List<Purchase> = purchaseDao.getPurchasesForProduct(productId).map(PurchaseEntity::toDomain)

    override fun observePurchasesForDateRange(startDay: Long, endDay: Long): Flow<List<Purchase>> =
        purchaseDao.observePurchasesForDateRange(startDay, endDay).map { purchases -> purchases.map(PurchaseEntity::toDomain) }

    override suspend fun getById(purchaseId: Long): Purchase? = purchaseDao.getPurchaseById(purchaseId)?.takeUnless { it.isDeleted }?.toDomain()

    override suspend fun summarize(startDate: LocalDate, endDate: LocalDate, productId: Long?): PurchaseSummary {
        require(!endDate.isBefore(startDate)) { "End date cannot precede start date" }
        val purchases = purchaseDao.getPurchasesForDateRange(startDate.toEpochDay(), endDate.toEpochDay())
            .filter { productId == null || it.productId == productId }
        var total = 0L
        val spendByProduct = linkedMapOf<Long, Long>()
        val quantityByProduct = linkedMapOf<Long, Triple<BigDecimal, String, Int>>()
        purchases.forEach { purchase ->
            total = Math.addExact(total, purchase.totalAmountMinor)
            spendByProduct[purchase.productId] = Math.addExact(spendByProduct[purchase.productId] ?: 0L, purchase.totalAmountMinor)
            val quantity = purchase.quantityMinor.toBigDecimal().movePointLeft(purchase.quantityScale)
            val previous = quantityByProduct[purchase.productId]
            quantityByProduct[purchase.productId] = Triple(
                (previous?.first ?: BigDecimal.ZERO).add(quantity),
                previous?.second ?: purchase.unit,
                maxOf(previous?.third ?: 0, purchase.quantityScale),
            )
        }
        return PurchaseSummary(
            totalSpend = Money(total, "INR"),
            purchaseCount = purchases.size,
            quantityByProduct = quantityByProduct.mapValues { (_, value) ->
                Quantity(value.first.movePointRight(value.third).longValueExact(), value.third, value.second)
            },
            spendByProduct = spendByProduct.mapValues { Money(it.value, "INR") },
        )
    }

    override suspend fun softDelete(purchaseId: Long, deletedAtEpochMs: Long, updatedAtEpochMs: Long): Int =
        purchaseDao.softDelete(purchaseId, deletedAtEpochMs, updatedAtEpochMs)

    private suspend fun categoryAncestors(parentId: Long?): List<com.financialapp.domain.model.Category> {
        val ancestors = mutableListOf<com.financialapp.domain.model.Category>()
        val visited = mutableSetOf<Long>()
        var currentId = parentId
        while (currentId != null) {
            require(visited.add(currentId)) { "Category hierarchy contains a cycle" }
            val current = categoryDao.getById(currentId)?.toDomain()
                ?: throw IllegalArgumentException("Parent category does not exist")
            require(current.isActive && !current.isDeleted) { "Parent category must be active" }
            ancestors += current
            currentId = current.parentId
        }
        return ancestors
    }
}

private fun Purchase.toEntity(now: Long) = PurchaseEntity(
    purchaseId = id,
    productId = productId,
    transactionId = transactionId,
    categoryId = categoryId,
    merchantName = merchantName?.trim()?.takeIf { it.isNotEmpty() },
    quantityMinor = quantity.minorUnits,
    quantityScale = quantity.scale,
    unit = quantity.unit.trim(),
    unitPriceMinor = unitPrice.minorUnits,
    totalAmountMinor = totalAmount.minorUnits,
    currencyCode = totalAmount.currencyCode,
    purchaseDateEpochDay = purchaseDate.toEpochDay(),
    notes = notes,
    createdAtEpochMs = now,
    updatedAtEpochMs = now,
)

private fun PurchaseEntity.toDomain() = Purchase(
    id = purchaseId,
    productId = productId,
    transactionId = transactionId,
    categoryId = categoryId,
    merchantName = merchantName,
    quantity = Quantity(quantityMinor, quantityScale, unit),
    unitPrice = Money(unitPriceMinor, currencyCode),
    totalAmount = Money(totalAmountMinor, currencyCode),
    purchaseDate = LocalDate.ofEpochDay(purchaseDateEpochDay),
    notes = notes,
    createdAt = java.time.Instant.ofEpochMilli(createdAtEpochMs),
)

private fun ProductEntity.toDomain() = com.financialapp.domain.model.Product(
    id = productId,
    name = name,
    defaultUnit = defaultUnit,
    defaultCurrencyCode = defaultCurrencyCode,
    categoryId = categoryId,
    isActive = isActive,
    isDeleted = isDeleted,
)

private fun CategoryEntity.toDomain() = com.financialapp.domain.model.Category(
    id = categoryId,
    parentId = parentCategoryId,
    type = com.financialapp.domain.model.CategoryType.valueOf(categoryType.name),
    name = name,
    isActive = isActive,
    isDeleted = isDeleted,
)
