package com.financialapp.data.repository

import com.financialapp.data.local.dao.ProductDao
import com.financialapp.data.local.dao.CategoryDao
import com.financialapp.data.local.entity.ProductEntity
import com.financialapp.domain.model.Product
import com.financialapp.domain.model.toTitleCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ProductRepository {
    suspend fun create(product: Product): Long
    suspend fun update(product: Product): Long
    fun observeAll(): Flow<List<Product>>
    suspend fun getById(productId: Long): Product?
    suspend fun getActiveProducts(): List<Product>
    suspend fun getProductsByCategory(categoryId: Long): List<Product>
    suspend fun softDelete(productId: Long, deletedAtEpochMs: Long, updatedAtEpochMs: Long): Int
    suspend fun setActive(productId: Long, isActive: Boolean): Int
}

class LocalProductRepository(
    private val productDao: ProductDao,
    private val categoryDao: CategoryDao,
) : ProductRepository {
    override suspend fun create(product: Product): Long {
        require(product.id == 0L) { "New product must not have an id" }
        require(!product.isDeleted && product.isActive) { "New product must be active" }
        require(product.name.isNotBlank()) { "Product name is required" }
        require(product.defaultUnit.isNotBlank()) { "Product unit is required" }
        require(product.defaultCurrencyCode == "INR") { "Phase 4 supports INR only" }
        val titleCased = product.copy(name = product.name.toTitleCase())
        titleCased.categoryId?.let { categoryId ->
            val category = categoryDao.getById(categoryId)
                ?: throw IllegalArgumentException("Category does not exist")
            require(category.isActive && !category.isDeleted) { "Product category must be active" }
        }
        require(!productDao.hasDuplicateName(titleCased.name, 0L)) { "A product with this name already exists" }
        val now = System.currentTimeMillis()
        return productDao.upsert(titleCased.toEntity(now, now))
    }

    override suspend fun update(product: Product): Long {
        require(product.id > 0L) { "Existing product id is required" }
        val existing = productDao.getById(product.id) ?: throw IllegalArgumentException("Product does not exist")
        require(!existing.isDeleted) { "Deleted products cannot be updated" }
        require(!product.isDeleted) { "Use delete to delete a product" }
        require(product.name.isNotBlank()) { "Product name is required" }
        require(product.defaultUnit.isNotBlank()) { "Product unit is required" }
        require(product.defaultCurrencyCode == "INR") { "Phase 4 supports INR only" }
        val titleCased = product.copy(name = product.name.toTitleCase())
        titleCased.categoryId?.let { categoryId ->
            val category = categoryDao.getById(categoryId)
                ?: throw IllegalArgumentException("Category does not exist")
            require(category.isActive && !category.isDeleted) { "Product category must be active" }
        }
        require(!productDao.hasDuplicateName(titleCased.name, titleCased.id)) { "A product with this name already exists" }
        return productDao.upsert(titleCased.toEntity(existing.createdAtEpochMs, System.currentTimeMillis()))
    }

    override fun observeAll(): Flow<List<Product>> = productDao.observeAll().map { products -> products.map(ProductEntity::toDomain) }

    override suspend fun getById(productId: Long): Product? = productDao.getById(productId)?.toDomain()

    override suspend fun getActiveProducts(): List<Product> = productDao.getActiveProducts().map(ProductEntity::toDomain)

    override suspend fun getProductsByCategory(categoryId: Long): List<Product> = productDao.getProductsByCategory(categoryId).map(ProductEntity::toDomain)

    override suspend fun softDelete(productId: Long, deletedAtEpochMs: Long, updatedAtEpochMs: Long): Int =
        productDao.softDelete(productId, deletedAtEpochMs, updatedAtEpochMs)

    override suspend fun setActive(productId: Long, isActive: Boolean): Int {
        require(productDao.getById(productId) != null) { "Product does not exist" }
        return productDao.setActive(productId, isActive, System.currentTimeMillis())
    }
}

private fun Product.toEntity(createdAtEpochMs: Long, updatedAtEpochMs: Long) = ProductEntity(
    productId = id,
    name = name.trim(),
    defaultUnit = defaultUnit.trim(),
    defaultCurrencyCode = defaultCurrencyCode,
    categoryId = categoryId,
    isActive = isActive,
    isDeleted = isDeleted,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)

private fun ProductEntity.toDomain() = Product(
    id = productId,
    name = name,
    defaultUnit = defaultUnit,
    defaultCurrencyCode = defaultCurrencyCode,
    categoryId = categoryId,
    isActive = isActive,
    isDeleted = isDeleted,
)
