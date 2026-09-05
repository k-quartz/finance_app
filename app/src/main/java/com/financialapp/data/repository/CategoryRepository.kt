package com.financialapp.data.repository

import androidx.room.withTransaction
import com.financialapp.data.local.database.AppDatabase
import com.financialapp.data.local.entity.CategoryEntity
import com.financialapp.domain.model.Category
import com.financialapp.domain.model.CategoryTreeNode
import com.financialapp.domain.model.toTitleCase
import com.financialapp.domain.validation.FinancialValidators
import java.time.Instant

interface CategoryRepository {
    suspend fun create(category: Category): Long
    suspend fun update(category: Category): Long
    suspend fun getCategory(categoryId: Long): Category?
    suspend fun getTree(includeInactive: Boolean = false): List<CategoryTreeNode>
    suspend fun activate(categoryId: Long): Boolean
    suspend fun deactivate(categoryId: Long): Boolean
    suspend fun delete(categoryId: Long): Boolean
    suspend fun reorder(categoryId: Long, newPosition: Int)
}

class LocalCategoryRepository(
    private val database: AppDatabase,
) : CategoryRepository {
    private val categoryDao get() = database.categoryDao()

    override suspend fun create(category: Category): Long {
        require(category.id == 0L) { "New category must not have an id" }
        require(!category.isDeleted) { "New category cannot be deleted" }
        val titleCased = category.copy(name = category.name.toTitleCase())
        return database.withTransaction {
            val ancestors = ancestorsOf(titleCased.parentId)
            FinancialValidators.validateCategory(titleCased, ancestors.firstOrNull(), ancestors.drop(1))
            val timestamp = now()
            categoryDao.insertIfUnique(titleCased.toEntity(timestamp, timestamp))
        }
    }

    override suspend fun update(category: Category): Long {
        require(category.id > 0L) { "Existing category id is required" }
        val titleCased = category.copy(name = category.name.toTitleCase())
        return database.withTransaction {
            val existing = categoryDao.getById(titleCased.id) ?: throw IllegalArgumentException("Category does not exist")
            require(!existing.isDeleted) { "Deleted categories cannot be updated" }
            require(!titleCased.isDeleted) { "Use delete to delete a category" }
            if (existing.categoryType.name != titleCased.type.name) {
                require(categoryDao.countChildren(titleCased.id) == 0) {
                    "Category type cannot change while it has children"
                }
            }
            val ancestors = ancestorsOf(titleCased.parentId)
            FinancialValidators.validateCategory(titleCased, ancestors.firstOrNull(), ancestors.drop(1))
            require(!categoryDao.hasDuplicate(titleCased.parentId, titleCased.name, titleCased.type.name, titleCased.id)) {
                "A category with the same name and type already exists at this level"
            }
            require(categoryDao.update(titleCased.toEntity(existing.createdAtEpochMs, now())) == 1) { "Category update failed" }
            titleCased.id
        }
    }

    override suspend fun getCategory(categoryId: Long): Category? = categoryDao.getById(categoryId)?.toDomain()

    override suspend fun getTree(includeInactive: Boolean): List<CategoryTreeNode> {
        val categories = categoryDao.getAll()
            .filter { !it.isDeleted }
            .map { it.toDomain() }
        validateHierarchy(categories)
        val byParent = categories.groupBy { it.parentId }
        val visiting = mutableSetOf<Long>()
        fun build(category: Category): CategoryTreeNode? {
            if (!includeInactive && !category.isActive) return null
            require(visiting.add(category.id)) { "Category hierarchy contains a cycle" }
            val children = byParent[category.id].orEmpty()
                .sortedWith(compareBy<Category> { it.sortOrder }.thenBy { it.name }.thenBy { it.id })
                .mapNotNull(::build)
            visiting.remove(category.id)
            return CategoryTreeNode(category, children)
        }
        val roots = byParent[null].orEmpty()
            .sortedWith(compareBy<Category> { it.type }.thenBy { it.sortOrder }.thenBy { it.name }.thenBy { it.id })
            .mapNotNull(::build)
        return roots
    }

    override suspend fun activate(categoryId: Long): Boolean {
        val category = categoryDao.getById(categoryId) ?: throw IllegalArgumentException("Category does not exist")
        require(!category.isDeleted) { "Deleted categories cannot be activated" }
        val parent = category.parentCategoryId?.let { categoryDao.getById(it) }
        require(parent == null || (parent.isActive && !parent.isDeleted)) { "Category parent must be active" }
        return categoryDao.setActive(categoryId, true, now()) == 1
    }

    override suspend fun deactivate(categoryId: Long): Boolean {
        val category = categoryDao.getById(categoryId) ?: throw IllegalArgumentException("Category does not exist")
        require(!category.isDeleted) { "Deleted categories cannot be deactivated" }
        return categoryDao.setActive(categoryId, false, now()) == 1
    }

    override suspend fun delete(categoryId: Long): Boolean {
        val category = categoryDao.getById(categoryId) ?: throw IllegalArgumentException("Category does not exist")
        require(!category.isDeleted) { "Category is already deleted" }
        require(categoryDao.countChildren(categoryId) == 0) { "Category with children cannot be deleted" }
        val timestamp = now()
        return categoryDao.softDelete(categoryId, timestamp, timestamp) == 1
    }

    override suspend fun reorder(categoryId: Long, newPosition: Int) {
        require(newPosition >= 0) { "Category position cannot be negative" }
        database.withTransaction {
            val category = categoryDao.getById(categoryId) ?: throw IllegalArgumentException("Category does not exist")
            require(!category.isDeleted) { "Deleted categories cannot be reordered" }
            val siblings = categoryDao.getAll()
                .filter { !it.isDeleted && it.parentCategoryId == category.parentCategoryId }
                .sortedWith(compareBy<CategoryEntity> { it.sortOrder }.thenBy { it.name }.thenBy { it.categoryId })
                .toMutableList()
            val oldIndex = siblings.indexOfFirst { it.categoryId == categoryId }
            require(oldIndex >= 0) { "Category is not in the active sibling list" }
            val targetIndex = newPosition.coerceAtMost(siblings.lastIndex)
            val moved = siblings.removeAt(oldIndex)
            siblings.add(targetIndex, moved)
            categoryDao.setSortOrders(siblings.map { it.categoryId }, now())
        }
    }

    private suspend fun ancestorsOf(parentId: Long?): List<Category> {
        val ancestors = mutableListOf<Category>()
        val visited = mutableSetOf<Long>()
        var currentId = parentId
        while (currentId != null) {
            require(visited.add(currentId)) { "Category hierarchy contains a cycle" }
            val parent = categoryDao.getById(currentId) ?: throw IllegalArgumentException("Parent category does not exist")
            require(parent.isActive && !parent.isDeleted) { "Parent category must be active and not deleted" }
            ancestors += parent.toDomain()
            currentId = parent.parentCategoryId
        }
        return ancestors
    }

    private fun now(): Long = Instant.now().toEpochMilli()

    private fun validateHierarchy(categories: List<Category>) {
        val byId = categories.associateBy { it.id }
        categories.forEach { category ->
            val visited = mutableSetOf<Long>()
            var parentId = category.parentId
            while (parentId != null) {
                require(visited.add(parentId)) { "Category hierarchy contains a cycle" }
                require(parentId != category.id) { "Category hierarchy contains a cycle" }
                val parent = byId[parentId]
                    ?: throw IllegalArgumentException("Category hierarchy contains a disconnected node")
                parentId = parent.parentId
            }
        }
    }

}

private fun Category.toEntity(createdAtEpochMs: Long, updatedAtEpochMs: Long): CategoryEntity = CategoryEntity(
    categoryId = id,
    parentCategoryId = parentId,
    categoryType = com.financialapp.data.local.entity.CategoryType.valueOf(type.name),
    name = name,
    sortOrder = sortOrder,
    isActive = isActive,
    isDeleted = isDeleted,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)

private fun CategoryEntity.toDomain(): Category = Category(
    id = categoryId,
    parentId = parentCategoryId,
    type = com.financialapp.domain.model.CategoryType.valueOf(categoryType.name),
    name = name,
    sortOrder = sortOrder,
    isActive = isActive,
    isDeleted = isDeleted,
)
