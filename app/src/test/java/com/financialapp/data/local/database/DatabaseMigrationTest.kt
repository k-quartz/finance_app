package com.financialapp.data.local.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DatabaseMigrationTest {

    @Test
    fun `direct v5 to v6 migration preserves categories and creates the declared index`() {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            val sqlite = database.openHelper.writableDatabase
            sqlite.execSQL("DROP INDEX index_categories_parent_category_id_name_category_type")
            sqlite.execSQL("DROP INDEX index_categories_is_active_is_deleted")
            sqlite.execSQL("DROP INDEX index_categories_parent_category_id_sort_order")
            sqlite.execSQL("ALTER TABLE categories RENAME TO categories_v5_backup")
            sqlite.execSQL(
                "CREATE TABLE categories (category_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, parent_category_id INTEGER, category_type TEXT NOT NULL, name TEXT NOT NULL, is_active INTEGER NOT NULL, is_deleted INTEGER NOT NULL, deleted_at_epoch_ms INTEGER, created_at_epoch_ms INTEGER NOT NULL, updated_at_epoch_ms INTEGER NOT NULL, FOREIGN KEY(parent_category_id) REFERENCES categories(category_id) ON UPDATE NO ACTION ON DELETE NO ACTION)",
            )
            sqlite.execSQL("INSERT INTO categories (category_id, category_type, name, is_active, is_deleted, created_at_epoch_ms, updated_at_epoch_ms) VALUES (7, 'EXPENSE', 'Legacy', 1, 0, 10, 10)")

            FinancialDatabaseMigrations.MIGRATION_5_6.migrate(sqlite)

            sqlite.query("SELECT category_id, name, sort_order FROM categories WHERE category_id = 7").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Legacy", cursor.getString(cursor.getColumnIndexOrThrow("name")))
                assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("sort_order")))
            }
            assertIndex(database, "categories", "index_categories_parent_category_id_sort_order", false, listOf("parent_category_id", "sort_order"))
        } finally {
            database.close()
        }
    }

    @Test
    fun `v6 to v7 migration enforces unique root category names`() {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            database.openHelper.writableDatabase.execSQL(
                "INSERT INTO categories (category_type, name, sort_order, is_active, is_deleted, created_at_epoch_ms, updated_at_epoch_ms) VALUES ('EXPENSE', 'Legacy', 0, 1, 0, 10, 10)",
            )

            FinancialDatabaseMigrations.MIGRATION_6_7.migrate(database.openHelper.writableDatabase)

            assertThrows(android.database.SQLException::class.java) {
                database.openHelper.writableDatabase.execSQL(
                    "INSERT INTO categories (category_type, name, sort_order, is_active, is_deleted, created_at_epoch_ms, updated_at_epoch_ms) VALUES ('EXPENSE', 'Legacy', 1, 1, 0, 11, 11)",
                )
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun `migration from v1 preserves financial data and adds new required columns`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "legacy_financial_app_v1.db"
        context.deleteDatabase(databaseName)

        val legacyDb = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        legacyDb.version = 1

        legacyDb.execSQL(
            "CREATE TABLE accounts (" +
                "account_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "account_type TEXT NOT NULL, " +
                "name TEXT NOT NULL, " +
                "currency_code TEXT NOT NULL, " +
                "is_active INTEGER NOT NULL DEFAULT 1, " +
                "is_deleted INTEGER NOT NULL DEFAULT 0, " +
                "deleted_at_epoch_ms INTEGER, " +
                "created_at_epoch_ms INTEGER NOT NULL, " +
                "updated_at_epoch_ms INTEGER NOT NULL, " +
                "notes TEXT" +
                ")"
        )
        legacyDb.execSQL(
            "CREATE TABLE categories (" +
                "category_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "parent_category_id INTEGER, " +
                "category_type TEXT NOT NULL, " +
                "name TEXT NOT NULL, " +
                "is_active INTEGER NOT NULL DEFAULT 1, " +
                "is_deleted INTEGER NOT NULL DEFAULT 0, " +
                "deleted_at_epoch_ms INTEGER, " +
                "created_at_epoch_ms INTEGER NOT NULL, " +
                "updated_at_epoch_ms INTEGER NOT NULL" +
                ")"
        )
        legacyDb.execSQL(
            "CREATE TABLE products (" +
                "product_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, " +
                "default_unit TEXT NOT NULL, " +
                "default_currency_code TEXT NOT NULL, " +
                "category_id INTEGER, " +
                "is_active INTEGER NOT NULL DEFAULT 1, " +
                "is_deleted INTEGER NOT NULL DEFAULT 0, " +
                "deleted_at_epoch_ms INTEGER, " +
                "created_at_epoch_ms INTEGER NOT NULL, " +
                "updated_at_epoch_ms INTEGER NOT NULL" +
                ")"
        )
        legacyDb.execSQL(
            "CREATE TABLE transactions (" +
                "transaction_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "account_id INTEGER NOT NULL, " +
                "counterparty_account_id INTEGER, " +
                "category_id INTEGER, " +
                "transaction_type TEXT NOT NULL, " +
                "amount_minor INTEGER NOT NULL, " +
                "currency_code TEXT NOT NULL, " +
                "occurred_date_epoch_day INTEGER NOT NULL, " +
                "posted_at_epoch_ms INTEGER NOT NULL, " +
                "is_deleted INTEGER NOT NULL DEFAULT 0, " +
                "deleted_at_epoch_ms INTEGER, " +
                "created_at_epoch_ms INTEGER NOT NULL, " +
                "updated_at_epoch_ms INTEGER NOT NULL, " +
                "notes TEXT" +
                ")"
        )
        legacyDb.execSQL(
            "CREATE TABLE purchases (" +
                "purchase_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "product_id INTEGER NOT NULL, " +
                "transaction_id INTEGER, " +
                "category_id INTEGER, " +
                "merchant_name TEXT, " +
                "quantity_minor INTEGER NOT NULL, " +
                "quantity_scale INTEGER NOT NULL, " +
                "unit TEXT NOT NULL, " +
                "unit_price_minor INTEGER NOT NULL, " +
                "total_amount_minor INTEGER NOT NULL, " +
                "currency_code TEXT NOT NULL, " +
                "purchase_date_epoch_day INTEGER NOT NULL, " +
                "is_deleted INTEGER NOT NULL DEFAULT 0, " +
                "deleted_at_epoch_ms INTEGER, " +
                "created_at_epoch_ms INTEGER NOT NULL, " +
                "updated_at_epoch_ms INTEGER NOT NULL, " +
                "notes TEXT" +
                ")"
        )
        legacyDb.execSQL(
            "CREATE TABLE product_price_history (" +
                "price_history_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "product_id INTEGER NOT NULL, " +
                "as_of_date_epoch_day INTEGER NOT NULL, " +
                "unit_price_minor INTEGER NOT NULL, " +
                "currency_code TEXT NOT NULL, " +
                "source TEXT, " +
                "notes TEXT, " +
                "created_at_epoch_ms INTEGER NOT NULL, " +
                "is_deleted INTEGER NOT NULL DEFAULT 0, " +
                "deleted_at_epoch_ms INTEGER" +
                ")"
        )

        legacyDb.execSQL(
            "INSERT INTO accounts (account_type, name, currency_code, is_active, is_deleted, created_at_epoch_ms, updated_at_epoch_ms, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf("BANK", "Legacy Savings", "INR", 1, 0, 1_700_000_000_000L, 1_700_000_000_000L, "legacy account note")
        )
        legacyDb.execSQL(
            "INSERT INTO categories (parent_category_id, category_type, name, is_active, is_deleted, created_at_epoch_ms, updated_at_epoch_ms) VALUES (?, ?, ?, ?, ?, ?, ?)",
            arrayOf(null, "EXPENSE", "Groceries", 1, 0, 1_700_000_001_000L, 1_700_000_001_000L)
        )
        legacyDb.execSQL(
            "INSERT INTO products (name, default_unit, default_currency_code, category_id, is_active, is_deleted, created_at_epoch_ms, updated_at_epoch_ms) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf("Rice", "kg", "INR", 1L, 1, 0, 1_700_000_002_000L, 1_700_000_002_000L)
        )
        legacyDb.execSQL(
            "INSERT INTO transactions (account_id, category_id, transaction_type, amount_minor, currency_code, occurred_date_epoch_day, posted_at_epoch_ms, is_deleted, created_at_epoch_ms, updated_at_epoch_ms, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf(1L, 1L, "EXPENSE", 1250L, "INR", 19723L, 1_700_000_003_000L, 0, 1_700_000_003_000L, 1_700_000_003_000L, "legacy transaction note")
        )
        legacyDb.execSQL(
            "INSERT INTO purchases (product_id, transaction_id, category_id, merchant_name, quantity_minor, quantity_scale, unit, unit_price_minor, total_amount_minor, currency_code, purchase_date_epoch_day, is_deleted, created_at_epoch_ms, updated_at_epoch_ms, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf(1L, 1L, 1L, "Local Mart", 2500L, 4, "kg", 500L, 1250L, "INR", 19723L, 0, 1_700_000_004_000L, 1_700_000_004_000L, "legacy purchase note")
        )
        legacyDb.execSQL(
            "INSERT INTO product_price_history (product_id, as_of_date_epoch_day, unit_price_minor, currency_code, source, notes, created_at_epoch_ms, is_deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf(1L, 19723L, 500L, "INR", "manual", "seed price", 1_700_000_005_000L, 0)
        )
        legacyDb.close()

        val migratedDatabase = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            databaseName,
        )
            .addMigrations(
                FinancialDatabaseMigrations.MIGRATION_1_2,
                FinancialDatabaseMigrations.MIGRATION_2_3,
                FinancialDatabaseMigrations.MIGRATION_3_4,
                FinancialDatabaseMigrations.MIGRATION_4_5,
                FinancialDatabaseMigrations.MIGRATION_5_6,
                FinancialDatabaseMigrations.MIGRATION_6_7,
                FinancialDatabaseMigrations.MIGRATION_7_8,
            )
            .build()

        try {
            val accountCursor = migratedDatabase.query("SELECT account_id, account_type, name, currency_code, opening_balance_minor, is_active, is_deleted, created_at_epoch_ms, updated_at_epoch_ms, notes FROM accounts WHERE account_id = 1", null)
            accountCursor.use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getLong(c.getColumnIndexOrThrow("account_id")))
                assertEquals("BANK", c.getString(c.getColumnIndexOrThrow("account_type")))
                assertEquals("Legacy Savings", c.getString(c.getColumnIndexOrThrow("name")))
                assertEquals("INR", c.getString(c.getColumnIndexOrThrow("currency_code")))
                assertEquals(1, c.getInt(c.getColumnIndexOrThrow("is_active")))
                assertEquals(0, c.getInt(c.getColumnIndexOrThrow("is_deleted")))
                assertEquals(1_700_000_000_000L, c.getLong(c.getColumnIndexOrThrow("created_at_epoch_ms")))
                assertEquals(1_700_000_000_000L, c.getLong(c.getColumnIndexOrThrow("updated_at_epoch_ms")))
                assertEquals("legacy account note", c.getString(c.getColumnIndexOrThrow("notes")))
                assertEquals(0L, c.getLong(c.getColumnIndexOrThrow("opening_balance_minor")))
            }

            val categoryCursor = migratedDatabase.query("SELECT category_id, name, category_type FROM categories WHERE category_id = 1", null)
            categoryCursor.use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Groceries", c.getString(c.getColumnIndexOrThrow("name")))
            }

            val transactionCursor = migratedDatabase.query("SELECT transaction_id, amount_minor, currency_code, transfer_id, merchant_name, notes FROM transactions WHERE transaction_id = 1", null)
            transactionCursor.use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1250L, c.getLong(c.getColumnIndexOrThrow("amount_minor")))
                assertEquals("INR", c.getString(c.getColumnIndexOrThrow("currency_code")))
                assertEquals(null, c.getString(c.getColumnIndexOrThrow("transfer_id")))
                assertEquals(null, c.getString(c.getColumnIndexOrThrow("merchant_name")))
                assertEquals("legacy transaction note", c.getString(c.getColumnIndexOrThrow("notes")))
            }

            val purchaseCursor = migratedDatabase.query("SELECT purchase_id, quantity_minor, quantity_scale, unit_price_minor, total_amount_minor, notes FROM purchases WHERE purchase_id = 1", null)
            purchaseCursor.use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(2500L, c.getLong(c.getColumnIndexOrThrow("quantity_minor")))
                assertEquals(4, c.getInt(c.getColumnIndexOrThrow("quantity_scale")))
                assertEquals(500L, c.getLong(c.getColumnIndexOrThrow("unit_price_minor")))
                assertEquals(1250L, c.getLong(c.getColumnIndexOrThrow("total_amount_minor")))
                assertEquals("legacy purchase note", c.getString(c.getColumnIndexOrThrow("notes")))
            }

            val priceHistoryCursor = migratedDatabase.query("SELECT product_id, as_of_date_epoch_day, unit_price_minor, created_at_epoch_ms, updated_at_epoch_ms FROM product_price_history WHERE price_history_id = 1", null)
            priceHistoryCursor.use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1L, c.getLong(c.getColumnIndexOrThrow("product_id")))
                assertEquals(19723L, c.getLong(c.getColumnIndexOrThrow("as_of_date_epoch_day")))
                assertEquals(500L, c.getLong(c.getColumnIndexOrThrow("unit_price_minor")))
                assertEquals(1_700_000_005_000L, c.getLong(c.getColumnIndexOrThrow("created_at_epoch_ms")))
                assertEquals(1_700_000_005_000L, c.getLong(c.getColumnIndexOrThrow("updated_at_epoch_ms")))
            }

            val tableInfoCursor = migratedDatabase.query("PRAGMA table_info(accounts)", null)
            tableInfoCursor.use { c ->
                var foundNotesColumn = false
                while (c.moveToNext()) {
                    if (c.getString(c.getColumnIndexOrThrow("name")) == "notes") {
                        foundNotesColumn = true
                    }
                }
                assertTrue(foundNotesColumn)
            }

            val productPriceHistoryInfoCursor = migratedDatabase.query("PRAGMA table_info(product_price_history)", null)
            productPriceHistoryInfoCursor.use { c ->
                var foundUpdatedAtColumn = false
                while (c.moveToNext()) {
                    if (c.getString(c.getColumnIndexOrThrow("name")) == "updated_at_epoch_ms") {
                        foundUpdatedAtColumn = true
                    }
                }
                assertTrue(foundUpdatedAtColumn)
            }

            assertExactSchema(migratedDatabase)

            val countCursor = migratedDatabase.query("SELECT COUNT(*) FROM accounts", null)
            countCursor.use { c ->
                c.moveToFirst()
                assertEquals(1, c.getInt(0))
            }

            val productCountCursor = migratedDatabase.query("SELECT COUNT(*) FROM products", null)
            productCountCursor.use { c ->
                c.moveToFirst()
                assertEquals(1, c.getInt(0))
            }

            val transactionCountCursor = migratedDatabase.query("SELECT COUNT(*) FROM transactions", null)
            transactionCountCursor.use { c ->
                c.moveToFirst()
                assertEquals(1, c.getInt(0))
            }

            val transferIndexCursor = migratedDatabase.query("PRAGMA index_list(transactions)", null)
            transferIndexCursor.use { c ->
                var foundUniqueTransferIndex = false
                while (c.moveToNext()) {
                    if (c.getString(c.getColumnIndexOrThrow("name")) == "index_transactions_transfer_id" && c.getInt(c.getColumnIndexOrThrow("unique")) == 1) {
                        foundUniqueTransferIndex = true
                    }
                }
                assertTrue(foundUniqueTransferIndex)
            }

            val purchaseCountCursor = migratedDatabase.query("SELECT COUNT(*) FROM purchases", null)
            purchaseCountCursor.use { c ->
                c.moveToFirst()
                assertEquals(1, c.getInt(0))
            }

            val priceHistoryCountCursor = migratedDatabase.query("SELECT COUNT(*) FROM product_price_history", null)
            priceHistoryCountCursor.use { c ->
                c.moveToFirst()
                assertEquals(1, c.getInt(0))
            }
        } finally {
            migratedDatabase.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun assertExactSchema(database: AppDatabase) {
        val expectedTables = setOf("accounts", "categories", "transactions", "products", "purchases", "product_price_history", "mutual_funds", "mutual_fund_holdings", "mutual_fund_transactions", "mutual_fund_nav_history")
        val actualTables = mutableSetOf<String>()
        database.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata', 'room_master_table')", null).use { c ->
            while (c.moveToNext()) actualTables += c.getString(0)
        }
        assertEquals(expectedTables, actualTables)

        assertColumns(database, "accounts", setOf("account_id", "account_type", "name", "currency_code", "opening_balance_minor", "is_active", "is_deleted", "deleted_at_epoch_ms", "created_at_epoch_ms", "updated_at_epoch_ms", "notes"), "account_id")
        assertColumns(database, "categories", setOf("category_id", "parent_category_id", "category_type", "name", "sort_order", "is_active", "is_deleted", "deleted_at_epoch_ms", "created_at_epoch_ms", "updated_at_epoch_ms"), "category_id")
        assertColumns(database, "transactions", setOf("transaction_id", "account_id", "counterparty_account_id", "transfer_id", "category_id", "transaction_type", "amount_minor", "currency_code", "occurred_date_epoch_day", "posted_at_epoch_ms", "notes", "merchant_name", "is_deleted", "deleted_at_epoch_ms", "created_at_epoch_ms", "updated_at_epoch_ms"), "transaction_id")
        assertColumns(database, "products", setOf("product_id", "name", "default_unit", "default_currency_code", "category_id", "is_active", "is_deleted", "deleted_at_epoch_ms", "created_at_epoch_ms", "updated_at_epoch_ms"), "product_id")
        assertColumns(database, "purchases", setOf("purchase_id", "product_id", "transaction_id", "category_id", "merchant_name", "quantity_minor", "quantity_scale", "unit", "unit_price_minor", "total_amount_minor", "currency_code", "purchase_date_epoch_day", "notes", "is_deleted", "deleted_at_epoch_ms", "created_at_epoch_ms", "updated_at_epoch_ms"), "purchase_id")
        assertColumns(database, "product_price_history", setOf("price_history_id", "product_id", "as_of_date_epoch_day", "unit_price_minor", "currency_code", "source", "notes", "created_at_epoch_ms", "is_deleted", "deleted_at_epoch_ms", "updated_at_epoch_ms"), "price_history_id")
        assertColumns(database, "mutual_funds", setOf("fund_id", "name", "code", "currency_code", "is_active", "is_deleted", "created_at_epoch_ms", "updated_at_epoch_ms"), "fund_id")
        assertColumns(database, "mutual_fund_holdings", setOf("holding_id", "fund_id", "investment_account_id", "units_minor", "units_scale", "updated_at_epoch_ms"), "holding_id")
        assertColumns(database, "mutual_fund_transactions", setOf("investment_transaction_id", "fund_id", "investment_account_id", "cash_account_id", "transaction_type", "units_minor", "units_scale", "nav_minor", "total_amount_minor", "currency_code", "transaction_date_epoch_day", "linked_cash_transaction_id", "notes", "created_at_epoch_ms"), "investment_transaction_id")
        assertColumns(database, "mutual_fund_nav_history", setOf("nav_id", "fund_id", "as_of_date_epoch_day", "nav_minor", "currency_code", "source", "notes", "created_at_epoch_ms"), "nav_id")

        assertIndex(database, "accounts", "index_accounts_name_currency_code", true, listOf("name", "currency_code"))
        assertIndex(database, "accounts", "index_accounts_is_active_is_deleted", false, listOf("is_active", "is_deleted"))
        assertIndex(database, "categories", "index_categories_parent_category_id_name_category_type", true, listOf("parent_category_id", "name", "category_type"))
        assertIndex(database, "categories", "index_categories_is_active_is_deleted", false, listOf("is_active", "is_deleted"))
        assertIndex(database, "categories", "index_categories_parent_category_id_sort_order", false, listOf("parent_category_id", "sort_order"))
        assertIndex(database, "transactions", "index_transactions_account_id_occurred_date_epoch_day", false, listOf("account_id", "occurred_date_epoch_day"))
        assertIndex(database, "transactions", "index_transactions_category_id_occurred_date_epoch_day", false, listOf("category_id", "occurred_date_epoch_day"))
        assertIndex(database, "transactions", "index_transactions_account_id_transaction_type", false, listOf("account_id", "transaction_type"))
        assertIndex(database, "transactions", "index_transactions_counterparty_account_id", false, listOf("counterparty_account_id"))
        assertIndex(database, "transactions", "index_transactions_transfer_id", true, listOf("transfer_id"))
        assertIndex(database, "products", "index_products_name", true, listOf("name"))
        assertIndex(database, "products", "index_products_category_id", false, listOf("category_id"))
        assertIndex(database, "products", "index_products_is_active_is_deleted", false, listOf("is_active", "is_deleted"))
        assertIndex(database, "purchases", "index_purchases_product_id_purchase_date_epoch_day", false, listOf("product_id", "purchase_date_epoch_day"))
        assertIndex(database, "purchases", "index_purchases_transaction_id", false, listOf("transaction_id"))
        assertIndex(database, "purchases", "index_purchases_category_id_purchase_date_epoch_day", false, listOf("category_id", "purchase_date_epoch_day"))
        assertIndex(database, "purchases", "index_purchases_merchant_name", false, listOf("merchant_name"))
        assertIndex(database, "product_price_history", "index_product_price_history_product_id_as_of_date_epoch_day", true, listOf("product_id", "as_of_date_epoch_day"))
        assertIndex(database, "product_price_history", "index_product_price_history_product_id_as_of_date_epoch_day_currency_code", false, listOf("product_id", "as_of_date_epoch_day", "currency_code"))
        assertIndex(database, "product_price_history", "index_product_price_history_is_deleted", false, listOf("is_deleted"))
        assertIndex(database, "mutual_funds", "index_mutual_funds_code", true, listOf("code"))
        assertIndex(database, "mutual_funds", "index_mutual_funds_is_active_is_deleted", false, listOf("is_active", "is_deleted"))
        assertIndex(database, "mutual_fund_holdings", "index_mutual_fund_holdings_fund_id_investment_account_id", true, listOf("fund_id", "investment_account_id"))
        assertIndex(database, "mutual_fund_holdings", "index_mutual_fund_holdings_investment_account_id", false, listOf("investment_account_id"))
        assertIndex(database, "mutual_fund_transactions", "index_mutual_fund_transactions_fund_id_investment_account_id_transaction_date_epoch_day", false, listOf("fund_id", "investment_account_id", "transaction_date_epoch_day"))
        assertIndex(database, "mutual_fund_transactions", "index_mutual_fund_transactions_investment_account_id", false, listOf("investment_account_id"))
        assertIndex(database, "mutual_fund_transactions", "index_mutual_fund_transactions_cash_account_id", false, listOf("cash_account_id"))
        assertIndex(database, "mutual_fund_transactions", "index_mutual_fund_transactions_linked_cash_transaction_id", true, listOf("linked_cash_transaction_id"))
        assertIndex(database, "mutual_fund_nav_history", "index_mutual_fund_nav_history_fund_id_as_of_date_epoch_day", true, listOf("fund_id", "as_of_date_epoch_day"))

        assertForeignKeys(database, "categories", listOf("categories|parent_category_id|category_id|NO ACTION"))
        assertForeignKeys(database, "transactions", listOf("accounts|account_id|account_id|RESTRICT", "accounts|counterparty_account_id|account_id|RESTRICT", "categories|category_id|category_id|RESTRICT"))
        assertForeignKeys(database, "products", listOf("categories|category_id|category_id|RESTRICT"))
        assertForeignKeys(database, "purchases", listOf("products|product_id|product_id|RESTRICT", "transactions|transaction_id|transaction_id|SET NULL", "categories|category_id|category_id|RESTRICT"))
        assertForeignKeys(database, "accounts", emptyList())
        assertForeignKeys(database, "product_price_history", listOf("products|product_id|product_id|RESTRICT"))
        assertForeignKeys(database, "mutual_fund_holdings", listOf("mutual_funds|fund_id|fund_id|RESTRICT", "accounts|investment_account_id|account_id|RESTRICT"))
        assertForeignKeys(database, "mutual_fund_transactions", listOf("mutual_funds|fund_id|fund_id|RESTRICT", "accounts|investment_account_id|account_id|RESTRICT", "accounts|cash_account_id|account_id|RESTRICT", "transactions|linked_cash_transaction_id|transaction_id|SET NULL"))
        assertForeignKeys(database, "mutual_fund_nav_history", listOf("mutual_funds|fund_id|fund_id|RESTRICT"))
    }

    private fun assertColumns(database: AppDatabase, table: String, expected: Set<String>, primaryKey: String) {
        val actual = mutableSetOf<String>()
        var actualPrimaryKey: String? = null
        database.query("PRAGMA table_info(`$table`)", null).use { c ->
            while (c.moveToNext()) {
                actual += c.getString(c.getColumnIndexOrThrow("name"))
                if (c.getInt(c.getColumnIndexOrThrow("pk")) == 1) actualPrimaryKey = c.getString(c.getColumnIndexOrThrow("name"))
            }
        }
        assertEquals(expected, actual)
        assertEquals(primaryKey, actualPrimaryKey)
    }

    private fun assertIndex(database: AppDatabase, table: String, name: String, unique: Boolean, columns: List<String>) {
        var found = false
        var actualUnique = false
        val actualColumns = mutableListOf<String>()
        database.query("PRAGMA index_list(`$table`)", null).use { c ->
            while (c.moveToNext()) {
                if (c.getString(c.getColumnIndexOrThrow("name")) == name) {
                    found = true
                    actualUnique = c.getInt(c.getColumnIndexOrThrow("unique")) == 1
                }
            }
        }
        database.query("PRAGMA index_info(`$name`)", null).use { c ->
            while (c.moveToNext()) actualColumns += c.getString(c.getColumnIndexOrThrow("name"))
        }
        assertTrue(found)
        assertEquals(unique, actualUnique)
        assertEquals(columns, actualColumns)
    }

    private fun assertForeignKeys(database: AppDatabase, table: String, expected: List<String>) {
        val actual = mutableListOf<String>()
        database.query("PRAGMA foreign_key_list(`$table`)", null).use { c ->
            while (c.moveToNext()) actual += listOf(c.getString(2), c.getString(3), c.getString(4), c.getString(6)).joinToString("|")
        }
        assertEquals(expected.toSet(), actual.toSet())
    }
}
