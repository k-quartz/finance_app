package com.financialapp.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.financialapp.data.local.database.AppDatabase
import com.financialapp.data.local.entity.AccountEntity
import com.financialapp.data.local.entity.AccountType
import com.financialapp.data.local.entity.CategoryEntity
import com.financialapp.data.local.entity.CategoryType
import com.financialapp.data.local.entity.ProductEntity
import com.financialapp.data.local.entity.PurchaseEntity
import com.financialapp.data.local.entity.TransactionEntity
import com.financialapp.data.local.entity.TransactionType
import com.financialapp.domain.model.Category
import com.financialapp.domain.model.CategoryType as DomainCategoryType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CategoryRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: CategoryRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = LocalCategoryRepository(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `crud builds an ordered hierarchy and filters inactive nodes`() = runBlocking {
        val needsId = repository.create(Category(type = DomainCategoryType.EXPENSE, name = "Needs", sortOrder = 1))
        val wantsId = repository.create(Category(type = DomainCategoryType.EXPENSE, name = "Wants", sortOrder = 0))
        val groceriesId = repository.create(Category(parentId = needsId, type = DomainCategoryType.EXPENSE, name = "Groceries", sortOrder = 1))
        repository.create(Category(parentId = needsId, type = DomainCategoryType.EXPENSE, name = "Rent", sortOrder = 0))
        repository.update(Category(id = wantsId, type = DomainCategoryType.EXPENSE, name = "Discretionary", sortOrder = 0))

        assertEquals(listOf("Discretionary", "Needs"), repository.getTree().map { it.category.name })
        assertEquals(listOf("Rent", "Groceries"), repository.getTree().last().children.map { it.category.name })

        repository.deactivate(groceriesId)
        assertEquals(listOf("Rent"), repository.getTree().last().children.map { it.category.name })
        assertEquals(listOf("Rent", "Groceries"), repository.getTree(includeInactive = true).last().children.map { it.category.name })
    }

    @Test
    fun `reorder normalizes sibling positions`() = runBlocking {
        val first = repository.create(Category(type = DomainCategoryType.EXPENSE, name = "First"))
        val second = repository.create(Category(type = DomainCategoryType.EXPENSE, name = "Second"))
        val third = repository.create(Category(type = DomainCategoryType.EXPENSE, name = "Third"))

        repository.reorder(third, 0)

        assertEquals(listOf(third, first, second), repository.getTree().map { it.category.id })
        assertEquals(listOf(0, 1, 2), database.categoryDao().getAll().sortedBy { it.sortOrder }.map { it.sortOrder })
    }

    @Test
    fun `delete rejects children but preserves historical references`() = runBlocking {
        val parent = repository.create(Category(type = DomainCategoryType.EXPENSE, name = "Parent"))
        val child = repository.create(Category(parentId = parent, type = DomainCategoryType.EXPENSE, name = "Child"))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.delete(parent) } }

        repository.delete(child)
        database.accountDao().upsert(
            AccountEntity(
                name = "Bank",
                accountType = AccountType.BANK,
                currencyCode = "INR",
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L,
            ),
        )
        database.transactionDao().upsert(
            TransactionEntity(
                accountId = 1L,
                categoryId = child,
                transactionType = TransactionType.EXPENSE,
                amountMinor = 100L,
                currencyCode = "INR",
                occurredDateEpochDay = 1L,
                postedAtEpochMs = 1L,
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L,
            ),
        )
        val productId = database.productDao().upsert(
            ProductEntity(
                name = "Rice",
                defaultUnit = "kg",
                defaultCurrencyCode = "INR",
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L,
            ),
        )
        database.purchaseDao().upsert(
            PurchaseEntity(
                productId = productId,
                categoryId = child,
                quantityMinor = 1L,
                quantityScale = 0,
                unit = "kg",
                unitPriceMinor = 100L,
                totalAmountMinor = 100L,
                currencyCode = "INR",
                purchaseDateEpochDay = 1L,
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L,
            ),
        )
        assertTrue(repository.getCategory(child)!!.isDeleted)
        assertEquals(1, database.transactionDao().getTransactionsForCategory(child).size)
        assertEquals(child, database.purchaseDao().getPurchaseById(1L)!!.categoryId)
    }

    @Test
    fun `activation requires an active parent and deleted categories stay deleted`() = runBlocking {
        val parent = repository.create(Category(type = DomainCategoryType.EXPENSE, name = "Parent"))
        val child = repository.create(Category(parentId = parent, type = DomainCategoryType.EXPENSE, name = "Child"))

        repository.deactivate(parent)
        assertTrue(repository.getTree().isEmpty())
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.activate(child) } }
        repository.activate(parent)
        assertTrue(repository.activate(child))
        repository.delete(child)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.activate(child) } }
        assertFalse(repository.getCategory(child)!!.isActive)
    }

    @Test
    fun `create rejects inactive and deleted parents`(): Unit = runBlocking {
        val inactive = repository.create(Category(type = DomainCategoryType.EXPENSE, name = "Inactive", isActive = false))
        val deleted = repository.create(Category(type = DomainCategoryType.EXPENSE, name = "Deleted"))
        repository.delete(deleted)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.create(Category(parentId = inactive, type = DomainCategoryType.EXPENSE, name = "Child")) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.create(Category(parentId = deleted, type = DomainCategoryType.EXPENSE, name = "Child")) }
        }
    }

    @Test
    fun `update rejects inactive deleted and descendant parents`(): Unit = runBlocking {
        val root = repository.create(Category(type = DomainCategoryType.EXPENSE, name = "Root"))
        val inactive = repository.create(Category(type = DomainCategoryType.EXPENSE, name = "Inactive", isActive = false))
        val deleted = repository.create(Category(type = DomainCategoryType.EXPENSE, name = "Deleted"))
        repository.delete(deleted)
        val child = repository.create(Category(parentId = root, type = DomainCategoryType.EXPENSE, name = "Child"))
        val grandchild = repository.create(Category(parentId = child, type = DomainCategoryType.EXPENSE, name = "Grandchild"))

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.update(Category(id = child, parentId = inactive, type = DomainCategoryType.EXPENSE, name = "Child")) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.update(Category(id = child, parentId = deleted, type = DomainCategoryType.EXPENSE, name = "Child")) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.update(Category(id = root, parentId = grandchild, type = DomainCategoryType.EXPENSE, name = "Root")) }
        }
    }

    @Test
    fun `update rejects changing a parent type when it has children`(): Unit = runBlocking {
        val parent = repository.create(Category(type = DomainCategoryType.EXPENSE, name = "Parent"))
        repository.create(Category(parentId = parent, type = DomainCategoryType.EXPENSE, name = "Child"))

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.update(Category(id = parent, type = DomainCategoryType.INCOME, name = "Parent"))
            }
        }
    }

    @Test
    fun `duplicate roots are rejected while child uniqueness is preserved`(): Unit = runBlocking {
        val root = repository.create(Category(type = DomainCategoryType.EXPENSE, name = "Shared"))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.create(Category(type = DomainCategoryType.EXPENSE, name = "Shared")) }
        }

        repository.create(Category(parentId = root, type = DomainCategoryType.EXPENSE, name = "Shared"))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.create(Category(parentId = root, type = DomainCategoryType.EXPENSE, name = "Shared")) }
        }
    }

    @Test
    fun `malformed disconnected hierarchy is rejected`(): Unit = runBlocking {
        val first = repository.create(Category(type = DomainCategoryType.EXPENSE, name = "First"))
        val second = repository.create(Category(type = DomainCategoryType.EXPENSE, name = "Second"))
        val firstEntity = database.categoryDao().getById(first)!!
        val secondEntity = database.categoryDao().getById(second)!!
        database.categoryDao().update(firstEntity.copy(parentCategoryId = second))
        database.categoryDao().update(secondEntity.copy(parentCategoryId = first))

        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.getTree() } }
    }
}