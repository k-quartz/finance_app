package com.financialapp.performance

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.financialapp.data.local.database.AppDatabase
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DatabaseQueryPlanTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `date range queries use phase 15 indexes`() {
        assertQueryUsesIndex(
            "SELECT * FROM transactions WHERE is_deleted = 0 AND account_id = 1 AND occurred_date_epoch_day BETWEEN 1 AND 10",
            "index_transactions_account_id_is_deleted_occurred_date_epoch_day",
        )
        assertQueryUsesIndex(
            "SELECT * FROM product_price_history WHERE product_id IN (1, 2) AND is_deleted = 0 ORDER BY product_id ASC, as_of_date_epoch_day DESC",
            "index_product_price_history_product_id_is_deleted_as_of_date_epoch_day",
        )
    }

    private fun assertQueryUsesIndex(sql: String, indexName: String) {
        val details = mutableListOf<String>()
        database.openHelper.writableDatabase.query("EXPLAIN QUERY PLAN $sql").use { cursor ->
            while (cursor.moveToNext()) details += cursor.getString(cursor.getColumnIndexOrThrow("detail"))
        }
        assertTrue("Expected $indexName in query plan: $details", details.any { it.contains(indexName) })
    }
}
