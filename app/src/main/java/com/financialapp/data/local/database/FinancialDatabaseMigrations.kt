package com.financialapp.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object FinancialDatabaseMigrations {
    private fun tableExists(database: SupportSQLiteDatabase, tableName: String): Boolean {
        val cursor = database.query(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(tableName),
        )
        return try {
            cursor.moveToFirst()
        } finally {
            cursor.close()
        }
    }

    private fun indexExists(database: SupportSQLiteDatabase, indexName: String): Boolean {
        val cursor = database.query(
            "SELECT 1 FROM sqlite_master WHERE type = 'index' AND name = ?",
            arrayOf(indexName),
        )
        return try {
            cursor.moveToFirst()
        } finally {
            cursor.close()
        }
    }

    private fun columnExists(database: SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
        val cursor = database.query(
            "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(tableName),
        )
        return try {
            if (!cursor.moveToFirst()) return false
            val schemaSql = cursor.getString(cursor.getColumnIndexOrThrow("sql"))
            schemaSql.contains(columnName)
        } finally {
            cursor.close()
        }
    }

    private fun legacyColumnExpression(database: SupportSQLiteDatabase, tableName: String, columnName: String): String =
        if (columnExists(database, tableName, columnName)) "`$columnName`" else "NULL"

    private fun recreateTable(
        database: SupportSQLiteDatabase,
        legacyTableName: String,
        v2CreateSql: String,
        copySql: String,
    ) {
        val tempTableName = "${legacyTableName}_migration_backup"
        val triggerSql = mutableListOf<String>()
        database.query(
            "SELECT sql FROM sqlite_master WHERE type = 'trigger' AND tbl_name = ? AND sql IS NOT NULL",
            arrayOf(legacyTableName),
        ).use { cursor ->
            while (cursor.moveToNext()) triggerSql += cursor.getString(0)
        }
        database.execSQL("ALTER TABLE $legacyTableName RENAME TO $tempTableName")
        database.execSQL(v2CreateSql)
        database.execSQL(copySql)
        database.execSQL("DROP TABLE $tempTableName")
        triggerSql.forEach(database::execSQL)
    }

    private fun ensureIndex(database: SupportSQLiteDatabase, indexName: String, createSql: String) {
        if (!indexExists(database, indexName)) {
            database.execSQL(createSql)
        }
    }

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            if (tableExists(database, "accounts") && (!columnExists(database, "accounts", "notes") || !indexExists(database, "index_accounts_name_currency_code") || !indexExists(database, "index_accounts_is_active_is_deleted"))) {
                recreateTable(
                    database,
                    "accounts",
                    """
                    CREATE TABLE accounts (
                        account_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        account_type TEXT NOT NULL,
                        name TEXT NOT NULL,
                        currency_code TEXT NOT NULL,
                        is_active INTEGER NOT NULL,
                        is_deleted INTEGER NOT NULL,
                        deleted_at_epoch_ms INTEGER,
                        created_at_epoch_ms INTEGER NOT NULL,
                        updated_at_epoch_ms INTEGER NOT NULL,
                        notes TEXT
                    )
                    """.trimIndent(),
                    """
                    INSERT INTO accounts (
                        account_id,
                        account_type,
                        name,
                        currency_code,
                        is_active,
                        is_deleted,
                        deleted_at_epoch_ms,
                        created_at_epoch_ms,
                        updated_at_epoch_ms,
                        notes
                    )
                    SELECT
                        account_id,
                        account_type,
                        name,
                        currency_code,
                        is_active,
                        is_deleted,
                        deleted_at_epoch_ms,
                        created_at_epoch_ms,
                        updated_at_epoch_ms,
                        ${legacyColumnExpression(database, "accounts", "notes")}
                    FROM accounts_migration_backup
                    """.trimIndent(),
                )
                ensureIndex(database, "index_accounts_is_active_is_deleted", "CREATE INDEX index_accounts_is_active_is_deleted ON accounts (is_active, is_deleted)")
                ensureIndex(database, "index_accounts_name_currency_code", "CREATE UNIQUE INDEX index_accounts_name_currency_code ON accounts (name, currency_code)")
            }

            if (tableExists(database, "categories") && (!columnExists(database, "categories", "notes") || !indexExists(database, "index_categories_parent_category_id_name_category_type") || !indexExists(database, "index_categories_is_active_is_deleted"))) {
                recreateTable(
                    database,
                    "categories",
                    """
                    CREATE TABLE categories (
                        category_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        parent_category_id INTEGER,
                        category_type TEXT NOT NULL,
                        name TEXT NOT NULL,
                        is_active INTEGER NOT NULL,
                        is_deleted INTEGER NOT NULL,
                        deleted_at_epoch_ms INTEGER,
                        created_at_epoch_ms INTEGER NOT NULL,
                        updated_at_epoch_ms INTEGER NOT NULL,
                        FOREIGN KEY(parent_category_id) REFERENCES categories(category_id) ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                    """.trimIndent(),
                    """
                    INSERT INTO categories (
                        category_id,
                        parent_category_id,
                        category_type,
                        name,
                        is_active,
                        is_deleted,
                        deleted_at_epoch_ms,
                        created_at_epoch_ms,
                        updated_at_epoch_ms
                    )
                    SELECT
                        category_id,
                        parent_category_id,
                        category_type,
                        name,
                        is_active,
                        is_deleted,
                        deleted_at_epoch_ms,
                        created_at_epoch_ms,
                        updated_at_epoch_ms
                    FROM categories_migration_backup
                    """.trimIndent(),
                )
                ensureIndex(database, "index_categories_parent_category_id_name_category_type", "CREATE UNIQUE INDEX index_categories_parent_category_id_name_category_type ON categories (parent_category_id, name, category_type)")
                ensureIndex(database, "index_categories_is_active_is_deleted", "CREATE INDEX index_categories_is_active_is_deleted ON categories (is_active, is_deleted)")
            }

            if (tableExists(database, "transactions") && (!columnExists(database, "transactions", "notes") || !indexExists(database, "index_transactions_account_id_occurred_date_epoch_day") || !indexExists(database, "index_transactions_category_id_occurred_date_epoch_day") || !indexExists(database, "index_transactions_account_id_transaction_type") || !indexExists(database, "index_transactions_counterparty_account_id"))) {
                recreateTable(
                    database,
                    "transactions",
                    """
                    CREATE TABLE transactions (
                        transaction_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        account_id INTEGER NOT NULL,
                        counterparty_account_id INTEGER,
                        category_id INTEGER,
                        transaction_type TEXT NOT NULL,
                        amount_minor INTEGER NOT NULL,
                        currency_code TEXT NOT NULL,
                        occurred_date_epoch_day INTEGER NOT NULL,
                        posted_at_epoch_ms INTEGER NOT NULL,
                        notes TEXT,
                        is_deleted INTEGER NOT NULL,
                        deleted_at_epoch_ms INTEGER,
                        created_at_epoch_ms INTEGER NOT NULL,
                        updated_at_epoch_ms INTEGER NOT NULL,
                        FOREIGN KEY(account_id) REFERENCES accounts(account_id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(counterparty_account_id) REFERENCES accounts(account_id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(category_id) REFERENCES categories(category_id) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent(),
                    """
                    INSERT INTO transactions (
                        transaction_id,
                        account_id,
                        counterparty_account_id,
                        category_id,
                        transaction_type,
                        amount_minor,
                        currency_code,
                        occurred_date_epoch_day,
                        posted_at_epoch_ms,
                        notes,
                        is_deleted,
                        deleted_at_epoch_ms,
                        created_at_epoch_ms,
                        updated_at_epoch_ms
                    )
                    SELECT
                        transaction_id,
                        account_id,
                        counterparty_account_id,
                        category_id,
                        transaction_type,
                        amount_minor,
                        currency_code,
                        occurred_date_epoch_day,
                        posted_at_epoch_ms,
                        ${legacyColumnExpression(database, "transactions", "notes")},
                        is_deleted,
                        deleted_at_epoch_ms,
                        created_at_epoch_ms,
                        updated_at_epoch_ms
                    FROM transactions_migration_backup
                    """.trimIndent(),
                )
                ensureIndex(database, "index_transactions_account_id_occurred_date_epoch_day", "CREATE INDEX index_transactions_account_id_occurred_date_epoch_day ON transactions (account_id, occurred_date_epoch_day)")
                ensureIndex(database, "index_transactions_category_id_occurred_date_epoch_day", "CREATE INDEX index_transactions_category_id_occurred_date_epoch_day ON transactions (category_id, occurred_date_epoch_day)")
                ensureIndex(database, "index_transactions_account_id_transaction_type", "CREATE INDEX index_transactions_account_id_transaction_type ON transactions (account_id, transaction_type)")
                ensureIndex(database, "index_transactions_counterparty_account_id", "CREATE INDEX index_transactions_counterparty_account_id ON transactions (counterparty_account_id)")
            }

            if (tableExists(database, "products") && (!columnExists(database, "products", "notes") || !indexExists(database, "index_products_name") || !indexExists(database, "index_products_category_id") || !indexExists(database, "index_products_is_active_is_deleted"))) {
                recreateTable(
                    database,
                    "products",
                    """
                    CREATE TABLE products (
                        product_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        default_unit TEXT NOT NULL,
                        default_currency_code TEXT NOT NULL,
                        category_id INTEGER,
                        is_active INTEGER NOT NULL,
                        is_deleted INTEGER NOT NULL,
                        deleted_at_epoch_ms INTEGER,
                        created_at_epoch_ms INTEGER NOT NULL,
                        updated_at_epoch_ms INTEGER NOT NULL,
                        FOREIGN KEY(category_id) REFERENCES categories(category_id) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent(),
                    """
                    INSERT INTO products (
                        product_id,
                        name,
                        default_unit,
                        default_currency_code,
                        category_id,
                        is_active,
                        is_deleted,
                        deleted_at_epoch_ms,
                        created_at_epoch_ms,
                        updated_at_epoch_ms
                    )
                    SELECT
                        product_id,
                        name,
                        default_unit,
                        default_currency_code,
                        category_id,
                        is_active,
                        is_deleted,
                        deleted_at_epoch_ms,
                        created_at_epoch_ms,
                        updated_at_epoch_ms
                    FROM products_migration_backup
                    """.trimIndent(),
                )
                ensureIndex(database, "index_products_name", "CREATE UNIQUE INDEX index_products_name ON products (name)")
                ensureIndex(database, "index_products_category_id", "CREATE INDEX index_products_category_id ON products (category_id)")
                ensureIndex(database, "index_products_is_active_is_deleted", "CREATE INDEX index_products_is_active_is_deleted ON products (is_active, is_deleted)")
            }

            if (tableExists(database, "purchases") && (!columnExists(database, "purchases", "notes") || !indexExists(database, "index_purchases_product_id_purchase_date_epoch_day") || !indexExists(database, "index_purchases_category_id_purchase_date_epoch_day") || !indexExists(database, "index_purchases_merchant_name"))) {
                recreateTable(
                    database,
                    "purchases",
                    """
                    CREATE TABLE purchases (
                        purchase_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        product_id INTEGER NOT NULL,
                        transaction_id INTEGER,
                        category_id INTEGER,
                        merchant_name TEXT,
                        quantity_minor INTEGER NOT NULL,
                        quantity_scale INTEGER NOT NULL,
                        unit TEXT NOT NULL,
                        unit_price_minor INTEGER NOT NULL,
                        total_amount_minor INTEGER NOT NULL,
                        currency_code TEXT NOT NULL,
                        purchase_date_epoch_day INTEGER NOT NULL,
                        notes TEXT,
                        is_deleted INTEGER NOT NULL,
                        deleted_at_epoch_ms INTEGER,
                        created_at_epoch_ms INTEGER NOT NULL,
                        updated_at_epoch_ms INTEGER NOT NULL,
                        FOREIGN KEY(product_id) REFERENCES products(product_id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(transaction_id) REFERENCES transactions(transaction_id) ON UPDATE NO ACTION ON DELETE SET NULL,
                        FOREIGN KEY(category_id) REFERENCES categories(category_id) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent(),
                    """
                    INSERT INTO purchases (
                        purchase_id,
                        product_id,
                        transaction_id,
                        category_id,
                        merchant_name,
                        quantity_minor,
                        quantity_scale,
                        unit,
                        unit_price_minor,
                        total_amount_minor,
                        currency_code,
                        purchase_date_epoch_day,
                        notes,
                        is_deleted,
                        deleted_at_epoch_ms,
                        created_at_epoch_ms,
                        updated_at_epoch_ms
                    )
                    SELECT
                        purchase_id,
                        product_id,
                        transaction_id,
                        category_id,
                        merchant_name,
                        quantity_minor,
                        quantity_scale,
                        unit,
                        unit_price_minor,
                        total_amount_minor,
                        currency_code,
                        purchase_date_epoch_day,
                        ${legacyColumnExpression(database, "purchases", "notes")},
                        is_deleted,
                        deleted_at_epoch_ms,
                        created_at_epoch_ms,
                        updated_at_epoch_ms
                    FROM purchases_migration_backup
                    """.trimIndent(),
                )
                ensureIndex(database, "index_purchases_product_id_purchase_date_epoch_day", "CREATE INDEX index_purchases_product_id_purchase_date_epoch_day ON purchases (product_id, purchase_date_epoch_day)")
                ensureIndex(database, "index_purchases_category_id_purchase_date_epoch_day", "CREATE INDEX index_purchases_category_id_purchase_date_epoch_day ON purchases (category_id, purchase_date_epoch_day)")
                ensureIndex(database, "index_purchases_merchant_name", "CREATE INDEX index_purchases_merchant_name ON purchases (merchant_name)")
            }

            if (tableExists(database, "product_price_history") && (!columnExists(database, "product_price_history", "updated_at_epoch_ms") || !indexExists(database, "index_product_price_history_product_id_as_of_date_epoch_day") || !indexExists(database, "index_product_price_history_product_id_as_of_date_epoch_day_currency_code") || !indexExists(database, "index_product_price_history_is_deleted"))) {
                recreateTable(
                    database,
                    "product_price_history",
                    """
                    CREATE TABLE product_price_history (
                        price_history_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        product_id INTEGER NOT NULL,
                        as_of_date_epoch_day INTEGER NOT NULL,
                        unit_price_minor INTEGER NOT NULL,
                        currency_code TEXT NOT NULL,
                        source TEXT,
                        notes TEXT,
                        created_at_epoch_ms INTEGER NOT NULL,
                        is_deleted INTEGER NOT NULL,
                        deleted_at_epoch_ms INTEGER,
                        updated_at_epoch_ms INTEGER NOT NULL,
                        FOREIGN KEY(product_id) REFERENCES products(product_id) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent(),
                    """
                    INSERT INTO product_price_history (
                        price_history_id,
                        product_id,
                        as_of_date_epoch_day,
                        unit_price_minor,
                        currency_code,
                        source,
                        notes,
                        created_at_epoch_ms,
                        is_deleted,
                        deleted_at_epoch_ms,
                        updated_at_epoch_ms
                    )
                    SELECT
                        price_history_id,
                        product_id,
                        as_of_date_epoch_day,
                        unit_price_minor,
                        currency_code,
                        source,
                        notes,
                        created_at_epoch_ms,
                        is_deleted,
                        deleted_at_epoch_ms,
                        created_at_epoch_ms
                    FROM product_price_history_migration_backup
                    """.trimIndent(),
                )
                ensureIndex(database, "index_product_price_history_product_id_as_of_date_epoch_day", "CREATE UNIQUE INDEX index_product_price_history_product_id_as_of_date_epoch_day ON product_price_history (product_id, as_of_date_epoch_day)")
                ensureIndex(database, "index_product_price_history_product_id_as_of_date_epoch_day_currency_code", "CREATE INDEX index_product_price_history_product_id_as_of_date_epoch_day_currency_code ON product_price_history (product_id, as_of_date_epoch_day, currency_code)")
                ensureIndex(database, "index_product_price_history_is_deleted", "CREATE INDEX index_product_price_history_is_deleted ON product_price_history (is_deleted)")
            }
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            ensureIndex(
                database,
                "index_purchases_transaction_id",
                "CREATE INDEX index_purchases_transaction_id ON purchases (transaction_id)",
            )
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE accounts ADD COLUMN opening_balance_minor INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE transactions ADD COLUMN transfer_id TEXT")
            database.execSQL("ALTER TABLE transactions ADD COLUMN merchant_name TEXT")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_transfer_id ON transactions (transfer_id)")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                UPDATE transactions
                SET transfer_id = transfer_id || ':legacy:' || transaction_id
                WHERE transfer_id IS NOT NULL
                  AND transaction_id NOT IN (
                      SELECT MIN(transaction_id)
                      FROM transactions
                      WHERE transfer_id IS NOT NULL
                      GROUP BY transfer_id
                  )
                """.trimIndent(),
            )
            database.execSQL("DROP INDEX IF EXISTS index_transactions_transfer_id")
            database.execSQL("CREATE UNIQUE INDEX index_transactions_transfer_id ON transactions (transfer_id)")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE categories ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_categories_parent_category_id_sort_order ON categories (parent_category_id, sort_order)")
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_categories_root_name_category_type ON categories (name, category_type) WHERE parent_category_id IS NULL",
            )
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE mutual_funds (
                    fund_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    code TEXT NOT NULL,
                    currency_code TEXT NOT NULL,
                    is_active INTEGER NOT NULL,
                    is_deleted INTEGER NOT NULL,
                    created_at_epoch_ms INTEGER NOT NULL,
                    updated_at_epoch_ms INTEGER NOT NULL
                )
            """.trimIndent())
            database.execSQL("CREATE UNIQUE INDEX index_mutual_funds_code ON mutual_funds (code)")
            database.execSQL("CREATE INDEX index_mutual_funds_is_active_is_deleted ON mutual_funds (is_active, is_deleted)")
            database.execSQL("""
                CREATE TABLE mutual_fund_holdings (
                    holding_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    fund_id INTEGER NOT NULL,
                    investment_account_id INTEGER NOT NULL,
                    units_minor INTEGER NOT NULL,
                    units_scale INTEGER NOT NULL,
                    updated_at_epoch_ms INTEGER NOT NULL,
                    FOREIGN KEY(fund_id) REFERENCES mutual_funds(fund_id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(investment_account_id) REFERENCES accounts(account_id) ON UPDATE NO ACTION ON DELETE RESTRICT
                )
            """.trimIndent())
            database.execSQL("CREATE UNIQUE INDEX index_mutual_fund_holdings_fund_id_investment_account_id ON mutual_fund_holdings (fund_id, investment_account_id)")
            database.execSQL("CREATE INDEX index_mutual_fund_holdings_investment_account_id ON mutual_fund_holdings (investment_account_id)")
            database.execSQL("""
                CREATE TABLE mutual_fund_transactions (
                    investment_transaction_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    fund_id INTEGER NOT NULL,
                    investment_account_id INTEGER NOT NULL,
                    cash_account_id INTEGER,
                    transaction_type TEXT NOT NULL,
                    units_minor INTEGER NOT NULL,
                    units_scale INTEGER NOT NULL,
                    nav_minor INTEGER NOT NULL,
                    total_amount_minor INTEGER NOT NULL,
                    currency_code TEXT NOT NULL,
                    transaction_date_epoch_day INTEGER NOT NULL,
                    linked_cash_transaction_id INTEGER,
                    notes TEXT,
                    created_at_epoch_ms INTEGER NOT NULL,
                    FOREIGN KEY(fund_id) REFERENCES mutual_funds(fund_id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(investment_account_id) REFERENCES accounts(account_id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(cash_account_id) REFERENCES accounts(account_id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(linked_cash_transaction_id) REFERENCES transactions(transaction_id) ON UPDATE NO ACTION ON DELETE SET NULL
                )
            """.trimIndent())
            database.execSQL("CREATE INDEX index_mutual_fund_transactions_fund_id_investment_account_id_transaction_date_epoch_day ON mutual_fund_transactions (fund_id, investment_account_id, transaction_date_epoch_day)")
            database.execSQL("CREATE INDEX index_mutual_fund_transactions_investment_account_id ON mutual_fund_transactions (investment_account_id)")
            database.execSQL("CREATE INDEX index_mutual_fund_transactions_cash_account_id ON mutual_fund_transactions (cash_account_id)")
            database.execSQL("CREATE UNIQUE INDEX index_mutual_fund_transactions_linked_cash_transaction_id ON mutual_fund_transactions (linked_cash_transaction_id)")
            database.execSQL("""
                CREATE TABLE mutual_fund_nav_history (
                    nav_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    fund_id INTEGER NOT NULL,
                    as_of_date_epoch_day INTEGER NOT NULL,
                    nav_minor INTEGER NOT NULL,
                    currency_code TEXT NOT NULL,
                    source TEXT,
                    notes TEXT,
                    created_at_epoch_ms INTEGER NOT NULL,
                    FOREIGN KEY(fund_id) REFERENCES mutual_funds(fund_id) ON UPDATE NO ACTION ON DELETE RESTRICT
                )
            """.trimIndent())
            database.execSQL("CREATE UNIQUE INDEX index_mutual_fund_nav_history_fund_id_as_of_date_epoch_day ON mutual_fund_nav_history (fund_id, as_of_date_epoch_day)")
        }
    }
}
