package com.financialapp.data.repository

import androidx.room.withTransaction
import com.financialapp.data.local.dao.AccountDao
import com.financialapp.data.local.dao.CategoryDao
import com.financialapp.data.local.dao.MutualFundDao
import com.financialapp.data.local.dao.ProductDao
import com.financialapp.data.local.dao.ProductPriceHistoryDao
import com.financialapp.data.local.dao.PurchaseDao
import com.financialapp.data.local.dao.TransactionDao
import com.financialapp.data.local.database.AppDatabase
import com.financialapp.data.local.entity.AccountEntity
import com.financialapp.data.local.entity.CategoryEntity
import com.financialapp.data.local.entity.MutualFundEntity
import com.financialapp.data.local.entity.MutualFundHoldingEntity
import com.financialapp.data.local.entity.MutualFundNavEntity
import com.financialapp.data.local.entity.MutualFundTransactionEntity
import com.financialapp.data.local.entity.ProductEntity
import com.financialapp.data.local.entity.ProductPriceHistoryEntity
import com.financialapp.data.local.entity.PurchaseEntity
import com.financialapp.data.local.entity.TransactionEntity
import java.time.Clock
import org.json.JSONArray
import org.json.JSONObject

interface BackupRepository {
    suspend fun exportBackup(): String
    suspend fun restoreBackup(json: String)
}

class LocalBackupRepository(
    private val database: AppDatabase,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val productDao: ProductDao,
    private val productPriceHistoryDao: ProductPriceHistoryDao,
    private val purchaseDao: PurchaseDao,
    private val mutualFundDao: MutualFundDao,
    private val clock: Clock,
) : BackupRepository {
    override suspend fun exportBackup(): String {
        val snapshot = JSONObject()
            .put("formatVersion", BACKUP_FORMAT_VERSION)
            .put("databaseVersion", BACKUP_DATABASE_VERSION)
            .put("exportedAtEpochMs", clock.instant().toEpochMilli())
            .put("accounts", accountDao.getAll().toJsonArray { account: AccountEntity -> account.toJson() })
            .put("categories", categoryDao.getAll().toJsonArray { category: CategoryEntity -> category.toJson() })
            .put("transactions", transactionDao.getAll().toJsonArray { transaction: TransactionEntity -> transaction.toJson() })
            .put("products", productDao.getAll().toJsonArray { product: ProductEntity -> product.toJson() })
            .put("priceHistory", productPriceHistoryDao.getAll().toJsonArray { history: ProductPriceHistoryEntity -> history.toJson() })
            .put("purchases", purchaseDao.getAll().toJsonArray { purchase: PurchaseEntity -> purchase.toJson() })
            .put("mutualFunds", mutualFundDao.getAllFunds().toJsonArray { fund: MutualFundEntity -> fund.toJson() })
            .put("mutualFundHoldings", mutualFundDao.getAllHoldingsList().toJsonArray { holding: MutualFundHoldingEntity -> holding.toJson() })
            .put("mutualFundTransactions", mutualFundDao.getAllTransactions().toJsonArray { transaction: MutualFundTransactionEntity -> transaction.toJson() })
            .put("mutualFundNavHistory", mutualFundDao.getAllNavHistory().toJsonArray { nav: MutualFundNavEntity -> nav.toJson() })
        return snapshot.toString(2)
    }

    override suspend fun restoreBackup(json: String) {
        require(json.isNotBlank()) { "Backup data is required" }
        val snapshot = parseSnapshot(json)
        require(snapshot.formatVersion == BACKUP_FORMAT_VERSION) { "Unsupported backup format version" }
        require(snapshot.databaseVersion == BACKUP_DATABASE_VERSION) { "Backup database version does not match the current schema" }
        try {
            database.withTransaction {
                clearTables()
                snapshot.accounts.forEach { accountDao.upsert(it) }
                snapshot.categories.forEach { categoryDao.upsert(it) }
                snapshot.transactions.forEach { transactionDao.upsert(it) }
                snapshot.products.forEach { productDao.upsert(it) }
                snapshot.priceHistory.forEach { productPriceHistoryDao.insert(it) }
                snapshot.purchases.forEach { purchaseDao.insert(it) }
                snapshot.mutualFunds.forEach { mutualFundDao.insertFund(it) }
                snapshot.mutualFundHoldings.forEach { mutualFundDao.insertHolding(it) }
                snapshot.mutualFundTransactions.forEach { mutualFundDao.insertTransaction(it) }
                snapshot.mutualFundNavHistory.forEach { mutualFundDao.insertNav(it) }
            }
        } catch (exception: Exception) {
            throw IllegalArgumentException("Backup data could not be restored", exception)
        }
    }

    private fun clearTables() {
        val writable = database.openHelper.writableDatabase
        writable.execSQL("DELETE FROM mutual_fund_nav_history")
        writable.execSQL("DELETE FROM mutual_fund_transactions")
        writable.execSQL("DELETE FROM mutual_fund_holdings")
        writable.execSQL("DELETE FROM purchases")
        writable.execSQL("DELETE FROM product_price_history")
        writable.execSQL("DELETE FROM transactions")
        writable.execSQL("DELETE FROM products")
        writable.execSQL("DELETE FROM mutual_funds")
        writable.execSQL("DELETE FROM categories")
        writable.execSQL("DELETE FROM accounts")
    }

    private fun parseSnapshot(json: String): BackupSnapshot {
        return try {
            val root = JSONObject(json)
            BackupSnapshot(
                formatVersion = root.getInt("formatVersion"),
                databaseVersion = root.getInt("databaseVersion"),
                exportedAtEpochMs = root.getLong("exportedAtEpochMs"),
                accounts = root.getJSONArray("accounts").toEntityList(::accountFromJson),
                categories = root.getJSONArray("categories").toEntityList(::categoryFromJson),
                transactions = root.getJSONArray("transactions").toEntityList(::transactionFromJson),
                products = root.getJSONArray("products").toEntityList(::productFromJson),
                priceHistory = root.getJSONArray("priceHistory").toEntityList(::priceHistoryFromJson),
                purchases = root.getJSONArray("purchases").toEntityList(::purchaseFromJson),
                mutualFunds = root.getJSONArray("mutualFunds").toEntityList(::mutualFundFromJson),
                mutualFundHoldings = root.getJSONArray("mutualFundHoldings").toEntityList(::mutualFundHoldingFromJson),
                mutualFundTransactions = root.getJSONArray("mutualFundTransactions").toEntityList(::mutualFundTransactionFromJson),
                mutualFundNavHistory = root.getJSONArray("mutualFundNavHistory").toEntityList(::mutualFundNavFromJson),
            )
        } catch (exception: Exception) {
            throw IllegalArgumentException("Backup data is malformed", exception)
        }
    }

    private data class BackupSnapshot(
        val formatVersion: Int,
        val databaseVersion: Int,
        val exportedAtEpochMs: Long,
        val accounts: List<AccountEntity>,
        val categories: List<CategoryEntity>,
        val transactions: List<TransactionEntity>,
        val products: List<ProductEntity>,
        val priceHistory: List<ProductPriceHistoryEntity>,
        val purchases: List<PurchaseEntity>,
        val mutualFunds: List<MutualFundEntity>,
        val mutualFundHoldings: List<MutualFundHoldingEntity>,
        val mutualFundTransactions: List<MutualFundTransactionEntity>,
        val mutualFundNavHistory: List<MutualFundNavEntity>,
    )
}

private const val BACKUP_FORMAT_VERSION = 1
private const val BACKUP_DATABASE_VERSION = 8

private fun <T> List<T>.toJsonArray(mapper: (T) -> JSONObject): JSONArray = JSONArray().apply {
    forEach { put(mapper(it)) }
}

private fun <T> JSONArray.toEntityList(parser: (JSONObject) -> T): List<T> = buildList(length()) {
    for (index in 0 until length()) {
        add(parser(getJSONObject(index)))
    }
}

private fun AccountEntity.toJson(): JSONObject = JSONObject()
    .put("accountId", accountId)
    .put("accountType", accountType.name)
    .put("name", name)
    .put("currencyCode", currencyCode)
    .put("openingBalanceMinor", openingBalanceMinor)
    .put("isActive", isActive)
    .put("isDeleted", isDeleted)
    .put("deletedAtEpochMs", deletedAtEpochMs)
    .put("createdAtEpochMs", createdAtEpochMs)
    .put("updatedAtEpochMs", updatedAtEpochMs)
    .put("notes", notes)

private fun CategoryEntity.toJson(): JSONObject = JSONObject()
    .put("categoryId", categoryId)
    .put("parentCategoryId", parentCategoryId)
    .put("categoryType", categoryType.name)
    .put("name", name)
    .put("sortOrder", sortOrder)
    .put("isActive", isActive)
    .put("isDeleted", isDeleted)
    .put("deletedAtEpochMs", deletedAtEpochMs)
    .put("createdAtEpochMs", createdAtEpochMs)
    .put("updatedAtEpochMs", updatedAtEpochMs)

private fun TransactionEntity.toJson(): JSONObject = JSONObject()
    .put("transactionId", transactionId)
    .put("accountId", accountId)
    .put("counterpartyAccountId", counterpartyAccountId)
    .put("transferId", transferId)
    .put("categoryId", categoryId)
    .put("transactionType", transactionType.name)
    .put("amountMinor", amountMinor)
    .put("currencyCode", currencyCode)
    .put("occurredDateEpochDay", occurredDateEpochDay)
    .put("postedAtEpochMs", postedAtEpochMs)
    .put("notes", notes)
    .put("merchantName", merchantName)
    .put("isDeleted", isDeleted)
    .put("deletedAtEpochMs", deletedAtEpochMs)
    .put("createdAtEpochMs", createdAtEpochMs)
    .put("updatedAtEpochMs", updatedAtEpochMs)

private fun ProductEntity.toJson(): JSONObject = JSONObject()
    .put("productId", productId)
    .put("name", name)
    .put("defaultUnit", defaultUnit)
    .put("defaultCurrencyCode", defaultCurrencyCode)
    .put("categoryId", categoryId)
    .put("isActive", isActive)
    .put("isDeleted", isDeleted)
    .put("deletedAtEpochMs", deletedAtEpochMs)
    .put("createdAtEpochMs", createdAtEpochMs)
    .put("updatedAtEpochMs", updatedAtEpochMs)

private fun ProductPriceHistoryEntity.toJson(): JSONObject = JSONObject()
    .put("priceHistoryId", priceHistoryId)
    .put("productId", productId)
    .put("asOfDateEpochDay", asOfDateEpochDay)
    .put("unitPriceMinor", unitPriceMinor)
    .put("currencyCode", currencyCode)
    .put("source", source)
    .put("notes", notes)
    .put("createdAtEpochMs", createdAtEpochMs)
    .put("isDeleted", isDeleted)
    .put("deletedAtEpochMs", deletedAtEpochMs)
    .put("updatedAtEpochMs", updatedAtEpochMs)

private fun PurchaseEntity.toJson(): JSONObject = JSONObject()
    .put("purchaseId", purchaseId)
    .put("productId", productId)
    .put("transactionId", transactionId)
    .put("categoryId", categoryId)
    .put("merchantName", merchantName)
    .put("quantityMinor", quantityMinor)
    .put("quantityScale", quantityScale)
    .put("unit", unit)
    .put("unitPriceMinor", unitPriceMinor)
    .put("totalAmountMinor", totalAmountMinor)
    .put("currencyCode", currencyCode)
    .put("purchaseDateEpochDay", purchaseDateEpochDay)
    .put("notes", notes)
    .put("isDeleted", isDeleted)
    .put("deletedAtEpochMs", deletedAtEpochMs)
    .put("createdAtEpochMs", createdAtEpochMs)
    .put("updatedAtEpochMs", updatedAtEpochMs)

private fun MutualFundEntity.toJson(): JSONObject = JSONObject()
    .put("fundId", fundId)
    .put("name", name)
    .put("code", code)
    .put("currencyCode", currencyCode)
    .put("isActive", isActive)
    .put("isDeleted", isDeleted)
    .put("createdAtEpochMs", createdAtEpochMs)
    .put("updatedAtEpochMs", updatedAtEpochMs)

private fun MutualFundHoldingEntity.toJson(): JSONObject = JSONObject()
    .put("holdingId", holdingId)
    .put("fundId", fundId)
    .put("investmentAccountId", investmentAccountId)
    .put("unitsMinor", unitsMinor)
    .put("unitsScale", unitsScale)
    .put("updatedAtEpochMs", updatedAtEpochMs)

private fun MutualFundTransactionEntity.toJson(): JSONObject = JSONObject()
    .put("investmentTransactionId", investmentTransactionId)
    .put("fundId", fundId)
    .put("investmentAccountId", investmentAccountId)
    .put("cashAccountId", cashAccountId)
    .put("transactionType", transactionType.name)
    .put("unitsMinor", unitsMinor)
    .put("unitsScale", unitsScale)
    .put("navMinor", navMinor)
    .put("totalAmountMinor", totalAmountMinor)
    .put("currencyCode", currencyCode)
    .put("transactionDateEpochDay", transactionDateEpochDay)
    .put("linkedCashTransactionId", linkedCashTransactionId)
    .put("notes", notes)
    .put("createdAtEpochMs", createdAtEpochMs)

private fun MutualFundNavEntity.toJson(): JSONObject = JSONObject()
    .put("navId", navId)
    .put("fundId", fundId)
    .put("asOfDateEpochDay", asOfDateEpochDay)
    .put("navMinor", navMinor)
    .put("currencyCode", currencyCode)
    .put("source", source)
    .put("notes", notes)
    .put("createdAtEpochMs", createdAtEpochMs)

private fun accountFromJson(json: JSONObject): AccountEntity = AccountEntity(
    accountId = json.getLong("accountId"),
    accountType = com.financialapp.data.local.entity.AccountType.valueOf(json.getString("accountType")),
    name = json.getString("name"),
    currencyCode = json.getString("currencyCode"),
    openingBalanceMinor = json.getLong("openingBalanceMinor"),
    isActive = json.getBoolean("isActive"),
    isDeleted = json.getBoolean("isDeleted"),
    deletedAtEpochMs = json.optLongOrNull("deletedAtEpochMs"),
    createdAtEpochMs = json.getLong("createdAtEpochMs"),
    updatedAtEpochMs = json.getLong("updatedAtEpochMs"),
    notes = json.optStringOrNull("notes"),
)

private fun categoryFromJson(json: JSONObject): CategoryEntity = CategoryEntity(
    categoryId = json.getLong("categoryId"),
    parentCategoryId = json.optLongOrNull("parentCategoryId"),
    categoryType = com.financialapp.data.local.entity.CategoryType.valueOf(json.getString("categoryType")),
    name = json.getString("name"),
    sortOrder = json.getInt("sortOrder"),
    isActive = json.getBoolean("isActive"),
    isDeleted = json.getBoolean("isDeleted"),
    deletedAtEpochMs = json.optLongOrNull("deletedAtEpochMs"),
    createdAtEpochMs = json.getLong("createdAtEpochMs"),
    updatedAtEpochMs = json.getLong("updatedAtEpochMs"),
)

private fun transactionFromJson(json: JSONObject): TransactionEntity = TransactionEntity(
    transactionId = json.getLong("transactionId"),
    accountId = json.getLong("accountId"),
    counterpartyAccountId = json.optLongOrNull("counterpartyAccountId"),
    transferId = json.optStringOrNull("transferId"),
    categoryId = json.optLongOrNull("categoryId"),
    transactionType = com.financialapp.data.local.entity.TransactionType.valueOf(json.getString("transactionType")),
    amountMinor = json.getLong("amountMinor"),
    currencyCode = json.getString("currencyCode"),
    occurredDateEpochDay = json.getLong("occurredDateEpochDay"),
    postedAtEpochMs = json.getLong("postedAtEpochMs"),
    notes = json.optStringOrNull("notes"),
    merchantName = json.optStringOrNull("merchantName"),
    isDeleted = json.getBoolean("isDeleted"),
    deletedAtEpochMs = json.optLongOrNull("deletedAtEpochMs"),
    createdAtEpochMs = json.getLong("createdAtEpochMs"),
    updatedAtEpochMs = json.getLong("updatedAtEpochMs"),
)

private fun productFromJson(json: JSONObject): ProductEntity = ProductEntity(
    productId = json.getLong("productId"),
    name = json.getString("name"),
    defaultUnit = json.getString("defaultUnit"),
    defaultCurrencyCode = json.getString("defaultCurrencyCode"),
    categoryId = json.optLongOrNull("categoryId"),
    isActive = json.getBoolean("isActive"),
    isDeleted = json.getBoolean("isDeleted"),
    deletedAtEpochMs = json.optLongOrNull("deletedAtEpochMs"),
    createdAtEpochMs = json.getLong("createdAtEpochMs"),
    updatedAtEpochMs = json.getLong("updatedAtEpochMs"),
)

private fun priceHistoryFromJson(json: JSONObject): ProductPriceHistoryEntity = ProductPriceHistoryEntity(
    priceHistoryId = json.getLong("priceHistoryId"),
    productId = json.getLong("productId"),
    asOfDateEpochDay = json.getLong("asOfDateEpochDay"),
    unitPriceMinor = json.getLong("unitPriceMinor"),
    currencyCode = json.getString("currencyCode"),
    source = json.optStringOrNull("source"),
    notes = json.optStringOrNull("notes"),
    createdAtEpochMs = json.getLong("createdAtEpochMs"),
    isDeleted = json.getBoolean("isDeleted"),
    deletedAtEpochMs = json.optLongOrNull("deletedAtEpochMs"),
    updatedAtEpochMs = json.getLong("updatedAtEpochMs"),
)

private fun purchaseFromJson(json: JSONObject): PurchaseEntity = PurchaseEntity(
    purchaseId = json.getLong("purchaseId"),
    productId = json.getLong("productId"),
    transactionId = json.optLongOrNull("transactionId"),
    categoryId = json.optLongOrNull("categoryId"),
    merchantName = json.optStringOrNull("merchantName"),
    quantityMinor = json.getLong("quantityMinor"),
    quantityScale = json.getInt("quantityScale"),
    unit = json.getString("unit"),
    unitPriceMinor = json.getLong("unitPriceMinor"),
    totalAmountMinor = json.getLong("totalAmountMinor"),
    currencyCode = json.getString("currencyCode"),
    purchaseDateEpochDay = json.getLong("purchaseDateEpochDay"),
    notes = json.optStringOrNull("notes"),
    isDeleted = json.getBoolean("isDeleted"),
    deletedAtEpochMs = json.optLongOrNull("deletedAtEpochMs"),
    createdAtEpochMs = json.getLong("createdAtEpochMs"),
    updatedAtEpochMs = json.getLong("updatedAtEpochMs"),
)

private fun mutualFundFromJson(json: JSONObject): MutualFundEntity = MutualFundEntity(
    fundId = json.getLong("fundId"),
    name = json.getString("name"),
    code = json.getString("code"),
    currencyCode = json.getString("currencyCode"),
    isActive = json.getBoolean("isActive"),
    isDeleted = json.getBoolean("isDeleted"),
    createdAtEpochMs = json.getLong("createdAtEpochMs"),
    updatedAtEpochMs = json.getLong("updatedAtEpochMs"),
)

private fun mutualFundHoldingFromJson(json: JSONObject): MutualFundHoldingEntity = MutualFundHoldingEntity(
    holdingId = json.getLong("holdingId"),
    fundId = json.getLong("fundId"),
    investmentAccountId = json.getLong("investmentAccountId"),
    unitsMinor = json.getLong("unitsMinor"),
    unitsScale = json.getInt("unitsScale"),
    updatedAtEpochMs = json.getLong("updatedAtEpochMs"),
)

private fun mutualFundTransactionFromJson(json: JSONObject): MutualFundTransactionEntity = MutualFundTransactionEntity(
    investmentTransactionId = json.getLong("investmentTransactionId"),
    fundId = json.getLong("fundId"),
    investmentAccountId = json.getLong("investmentAccountId"),
    cashAccountId = json.optLongOrNull("cashAccountId"),
    transactionType = com.financialapp.data.local.entity.InvestmentTransactionType.valueOf(json.getString("transactionType")),
    unitsMinor = json.getLong("unitsMinor"),
    unitsScale = json.getInt("unitsScale"),
    navMinor = json.getLong("navMinor"),
    totalAmountMinor = json.getLong("totalAmountMinor"),
    currencyCode = json.getString("currencyCode"),
    transactionDateEpochDay = json.getLong("transactionDateEpochDay"),
    linkedCashTransactionId = json.optLongOrNull("linkedCashTransactionId"),
    notes = json.optStringOrNull("notes"),
    createdAtEpochMs = json.getLong("createdAtEpochMs"),
)

private fun mutualFundNavFromJson(json: JSONObject): MutualFundNavEntity = MutualFundNavEntity(
    navId = json.getLong("navId"),
    fundId = json.getLong("fundId"),
    asOfDateEpochDay = json.getLong("asOfDateEpochDay"),
    navMinor = json.getLong("navMinor"),
    currencyCode = json.getString("currencyCode"),
    source = json.optStringOrNull("source"),
    notes = json.optStringOrNull("notes"),
    createdAtEpochMs = json.getLong("createdAtEpochMs"),
)

private fun JSONObject.optStringOrNull(name: String): String? = if (has(name) && !isNull(name)) getString(name) else null

private fun JSONObject.optLongOrNull(name: String): Long? = if (has(name) && !isNull(name)) getLong(name) else null