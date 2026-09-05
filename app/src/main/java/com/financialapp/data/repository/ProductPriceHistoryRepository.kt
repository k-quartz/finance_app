package com.financialapp.data.repository

import com.financialapp.data.local.dao.ProductDao
import com.financialapp.data.local.dao.ProductPriceHistoryDao
import com.financialapp.data.local.entity.ProductEntity
import com.financialapp.data.local.entity.ProductPriceHistoryEntity
import com.financialapp.domain.calculation.CommodityPriceAnalytics
import com.financialapp.domain.model.CommodityPriceComparison
import com.financialapp.domain.model.CommodityPricePoint
import com.financialapp.domain.model.CommodityPriceTrend
import com.financialapp.domain.model.Money
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ProductPriceHistoryRepository {
    suspend fun recordPrice(priceEntry: CommodityPricePoint): Long
    suspend fun getHistoryForProduct(productId: Long): List<CommodityPricePoint>
    suspend fun getPriceForDate(productId: Long, dateEpochDay: Long): CommodityPricePoint?
    suspend fun getLatestPriceBeforeOrOnDate(productId: Long, dateEpochDay: Long): CommodityPricePoint?
    fun observePriceHistoryRange(startDay: Long, endDay: Long): Flow<List<CommodityPricePoint>>
    suspend fun analyzeTrend(productId: Long, startDate: LocalDate, endDate: LocalDate): CommodityPriceTrend
    suspend fun compareLatestPrices(productIds: List<Long>, asOfDate: LocalDate): List<CommodityPriceComparison>
}

class LocalProductPriceHistoryRepository(
    private val productDao: ProductDao,
    private val priceHistoryDao: ProductPriceHistoryDao,
) : ProductPriceHistoryRepository {
    override suspend fun recordPrice(priceEntry: CommodityPricePoint): Long {
        require(priceEntry.id == 0L) { "New price history entries must not have an id" }
        val product = productDao.getById(priceEntry.productId)?.toDomain()
            ?: throw IllegalArgumentException("Product does not exist")
        require(product.isActive && !product.isDeleted) { "Inactive or deleted products cannot receive price history" }
        require(priceEntry.price.currencyCode == product.defaultCurrencyCode) { "Price currency must match the product currency" }
        require(priceEntry.price.minorUnits > 0L) { "Price must be positive" }
        return priceHistoryDao.insert(priceEntry.toEntity())
    }

    override suspend fun getHistoryForProduct(productId: Long): List<CommodityPricePoint> =
        priceHistoryDao.getHistoryForProduct(productId).map(ProductPriceHistoryEntity::toDomain)

    override suspend fun getPriceForDate(productId: Long, dateEpochDay: Long): CommodityPricePoint? =
        priceHistoryDao.getPriceForDate(productId, dateEpochDay)?.toDomain()

    override suspend fun getLatestPriceBeforeOrOnDate(productId: Long, dateEpochDay: Long): CommodityPricePoint? =
        priceHistoryDao.getLatestPriceBeforeOrOnDate(productId, dateEpochDay)?.toDomain()

    override fun observePriceHistoryRange(startDay: Long, endDay: Long): Flow<List<CommodityPricePoint>> =
        priceHistoryDao.observePriceHistoryRange(startDay, endDay).map { history -> history.map(ProductPriceHistoryEntity::toDomain) }

    override suspend fun analyzeTrend(productId: Long, startDate: LocalDate, endDate: LocalDate): CommodityPriceTrend {
        val points = getHistoryForProduct(productId)
        return CommodityPriceAnalytics.analyzeTrend(productId, points, startDate, endDate)
    }

    override suspend fun compareLatestPrices(productIds: List<Long>, asOfDate: LocalDate): List<CommodityPriceComparison> {
        require(productIds.isNotEmpty()) { "At least one product is required" }
        val uniqueProductIds = productIds.distinct()
        val productNames = uniqueProductIds.associateWith { productId ->
            val product = productDao.getById(productId)?.toDomain() ?: throw IllegalArgumentException("Product does not exist")
            require(product.isActive && !product.isDeleted) { "Inactive or deleted products cannot be compared" }
            product.name
        }
        val histories = uniqueProductIds.associateWith { productId -> getHistoryForProduct(productId) }
        return CommodityPriceAnalytics.compareLatestPrices(productNames, histories, asOfDate)
    }
}

private fun CommodityPricePoint.toEntity() = ProductPriceHistoryEntity(
    priceHistoryId = id,
    productId = productId,
    asOfDateEpochDay = asOfDate.toEpochDay(),
    unitPriceMinor = price.minorUnits,
    currencyCode = price.currencyCode,
    source = source,
    notes = notes,
    createdAtEpochMs = createdAt.toEpochMilli(),
)

private fun ProductPriceHistoryEntity.toDomain() = CommodityPricePoint(
    id = priceHistoryId,
    productId = productId,
    asOfDate = LocalDate.ofEpochDay(asOfDateEpochDay),
    price = Money(unitPriceMinor, currencyCode),
    source = source,
    notes = notes,
    createdAt = Instant.ofEpochMilli(createdAtEpochMs),
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
