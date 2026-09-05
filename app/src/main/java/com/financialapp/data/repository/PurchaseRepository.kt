package com.financialapp.data.repository

import androidx.room.withTransaction
import com.financialapp.data.local.database.AppDatabase
import com.financialapp.data.local.entity.PurchaseEntity
import com.financialapp.data.local.entity.ProductEntity
import com.financialapp.data.local.entity.CategoryEntity
import com.financialapp.domain.model.FinancialTransaction
import com.financialapp.domain.model.Money
import com.financialapp.domain.model.Purchase
import com.financialapp.domain.model.PurchaseSummary
import com.financialapp.domain.model.Quantity
import com.financialapp.domain.model.TransactionType
import com.financialapp.domain.validation.FinancialValidators
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface PurchaseRepository {
    suspend fun create(purchase: Purchase): Long
    suspend fun recordWithExpense(accountId: Long, purchase: Purchase): Long
    suspend fun correct(purchaseId: Long, accountId: Long, replacement: Purchase): Long
    suspend fun getPurchasesForProduct(productId: Long): List<Purchase>
    fun observePurchasesForDateRange(startDay: Long, endDay: Long): Flow<List<Purchase>>
    suspend fun getById(purchaseId: Long): Purchase?
    suspend fun summarize(startDate: LocalDate, endDate: LocalDate, productId: Long? = null): PurchaseSummary
    suspend fun softDelete(purchaseId: Long, deletedAtEpochMs: Long, updatedAtEpochMs: Long): Int
}

class LocalPurchaseRepository(
    private val database: AppDatabase,
    private val financialRepository: FinancialRepository,
) : PurchaseRepository {
    private val purchaseDao get() = database.purchaseDao()
    private val productDao get() = database.productDao()
    private val categoryDao get() = database.categoryDao()
    private val transactionDao get() = database.transactionDao()

    override suspend fun create(purchase: Purchase): Long {
        validateLinkedTransaction(purchase)
        val product = requireProduct(purchase.productId)
        val category = purchase.categoryId?.let { requireCategory(it) }
        val ancestors = category?.let { categoryAncestors(it.parentId) } ?: emptyList()
        FinancialValidators.validatePurchase(purchase, product, category, ancestors)
        val now = purchase.createdAt.toEpochMilli()
        return purchaseDao.insert(purchase.toEntity(now, now))
    }

    override suspend fun recordWithExpense(accountId: Long, purchase: Purchase): Long {
        require(purchase.id == 0L) { "New purchase must not have an id" }
        require(purchase.transactionId == null) { "Expense-linked purchase creates its own transaction" }
        return database.withTransaction {
            val product = requireProduct(purchase.productId)
            val categoryId = purchase.categoryId ?: product.categoryId
                ?: throw IllegalArgumentException("Expense category is required")
            val category = requireCategory(categoryId)
            val ancestors = categoryAncestors(category.parentId)
            val linked = purchase.copy(categoryId = categoryId)
            FinancialValidators.validatePurchase(linked, product, category, ancestors)
            val transactionId = financialRepository.createTransaction(
                FinancialTransaction(
                    accountId = accountId,
                    type = TransactionType.EXPENSE,
                    amount = linked.totalAmount,
                    categoryId = categoryId,
                    transactionDate = linked.purchaseDate,
                    createdAt = linked.createdAt,
                    notes = linked.notes,
                    merchantOrPayee = linked.merchantName,
                ),
            )
            val now = linked.createdAt.toEpochMilli()
            purchaseDao.insert(linked.copy(transactionId = transactionId).toEntity(now, now))
        }
    }

    override suspend fun correct(purchaseId: Long, accountId: Long, replacement: Purchase): Long {
        require(purchaseId > 0L) { "Existing purchase id is required" }
        require(replacement.id == 0L) { "Corrected purchase must be new" }
        require(replacement.transactionId == null) { "Corrected purchase creates its own transaction link" }
        return database.withTransaction {
            val existing = purchaseDao.getPurchaseById(purchaseId)
                ?: throw IllegalArgumentException("Purchase does not exist")
            require(!existing.isDeleted) { "Deleted purchases cannot be updated" }
            val product = requireProduct(replacement.productId)
            val categoryId = replacement.categoryId ?: product.categoryId
                ?: throw IllegalArgumentException("Expense category is required")
            val category = requireCategory(categoryId)
            val ancestors = categoryAncestors(category.parentId)
            val draft = replacement.copy(categoryId = categoryId)
            FinancialValidators.validatePurchase(draft, product, category, ancestors)
            val expense = FinancialTransaction(
                accountId = accountId,
                type = TransactionType.EXPENSE,
                amount = draft.totalAmount,
                categoryId = categoryId,
                transactionDate = draft.purchaseDate,
                createdAt = draft.createdAt,
                notes = draft.notes,
                merchantOrPayee = draft.merchantName,
            )
            val transactionId = if (existing.transactionId != null) {
                financialRepository.correctTransaction(existing.transactionId, expense)
            } else {
                financialRepository.createTransaction(expense)
            }
            val now = draft.createdAt.toEpochMilli()
            purchaseDao.upsert(draft.copy(id = purchaseId, transactionId = transactionId).toEntity(existing.createdAtEpochMs, now))
            purchaseId
        }
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
        database.withTransaction {
            val existing = purchaseDao.getPurchaseById(purchaseId) ?: throw IllegalArgumentException("Purchase does not exist")
            require(!existing.isDeleted) { "Purchase is already deleted" }
            val deleted = purchaseDao.softDelete(purchaseId, deletedAtEpochMs, updatedAtEpochMs)
            existing.transactionId?.let { transactionId ->
                val linked = transactionDao.getById(transactionId)
                if (linked != null && !linked.isDeleted) {
                    financialRepository.deleteTransaction(transactionId, Instant.ofEpochMilli(deletedAtEpochMs))
                }
            }
            deleted
        }

    private suspend fun validateLinkedTransaction(purchase: Purchase) {
        if (purchase.transactionId == null) return
        val transaction = transactionDao.getById(purchase.transactionId)
            ?: throw IllegalArgumentException("Purchase transaction does not exist")
        require(transaction.transactionType.name == "EXPENSE") { "Purchase transaction must be an expense" }
        require(transaction.amountMinor == purchase.totalAmount.minorUnits) { "Purchase total must match transaction amount" }
        require(transaction.currencyCode == purchase.totalAmount.currencyCode) { "Purchase currency must match transaction currency" }
    }

    private suspend fun requireProduct(productId: Long) =
        productDao.getById(productId)?.toDomain() ?: throw IllegalArgumentException("Product does not exist")

    private suspend fun requireCategory(categoryId: Long) =
        categoryDao.getById(categoryId)?.toDomain() ?: throw IllegalArgumentException("Category does not exist")

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

private fun Purchase.toEntity(createdAtEpochMs: Long, updatedAtEpochMs: Long) = PurchaseEntity(
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
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
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
