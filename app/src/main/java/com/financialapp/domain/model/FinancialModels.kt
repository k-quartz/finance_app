package com.financialapp.domain.model

import java.time.Instant
import java.time.LocalDate
import java.math.BigDecimal
import java.math.RoundingMode

/** Exact currency amount stored as minor units, such as paise for INR. */
data class Money(
    val minorUnits: Long,
    val currencyCode: String,
) {
    init {
        require(currencyCode == "INR") {
            "Phase 4 supports INR only"
        }
    }

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return copy(minorUnits = Math.addExact(minorUnits, other.minorUnits))
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return copy(minorUnits = Math.subtractExact(minorUnits, other.minorUnits))
    }

    private fun requireSameCurrency(other: Money) {
        require(currencyCode == other.currencyCode) { "Money values must use the same currency" }
    }
}

enum class AccountType { BANK, CASH, CREDIT_CARD, WALLET, INVESTMENT, OTHER }
enum class TransactionType { INCOME, EXPENSE, TRANSFER }
enum class CategoryType { EXPENSE, INCOME, TRANSFER, OTHER }

data class Account(
    val id: Long = 0L,
    val name: String,
    val type: AccountType,
    val openingBalance: Money,
    val isActive: Boolean = true,
    val isDeleted: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class Category(
    val id: Long = 0L,
    val parentId: Long? = null,
    val type: CategoryType,
    val name: String,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
    val isDeleted: Boolean = false,
)

data class CategoryTreeNode(
    val category: Category,
    val children: List<CategoryTreeNode>,
)

data class FinancialTransaction(
    val id: Long = 0L,
    val accountId: Long,
    val type: TransactionType,
    val amount: Money,
    val categoryId: Long? = null,
    val transactionDate: LocalDate,
    val createdAt: Instant,
    val notes: String? = null,
    val merchantOrPayee: String? = null,
    val transferId: String? = null,
)

data class TransactionSummary(
    val income: Money,
    val expense: Money,
    val expenseByCategory: Map<Long, Money> = emptyMap(),
)

data class DuplicateTransaction(
    val transaction: FinancialTransaction,
    val matchingFields: Set<String>,
)

enum class RecurringExpenseCadence { WEEKLY, MONTHLY }

data class RecurringExpensePattern(
    val accountId: Long,
    val categoryId: Long?,
    val merchantOrPayee: String,
    val cadence: RecurringExpenseCadence,
    val transactions: List<FinancialTransaction>,
    val averageAmount: Money,
    val confidencePercent: Int,
)

data class Transfer(
    val transferId: String,
    val sourceAccountId: Long,
    val destinationAccountId: Long,
    val amount: Money,
    val transactionDate: LocalDate,
    val createdAt: Instant,
)

data class AccountBalance(
    val accountId: Long,
    val amount: Money,
    val accountType: AccountType = AccountType.OTHER,
)

class DomainValidationException(message: String) : IllegalArgumentException(message)

data class Product(
    val id: Long = 0L,
    val name: String,
    val defaultUnit: String,
    val defaultCurrencyCode: String = "INR",
    val categoryId: Long? = null,
    val isActive: Boolean = true,
    val isDeleted: Boolean = false,
)

data class Quantity(
    val minorUnits: Long,
    val scale: Int,
    val unit: String,
) {
    init {
        require(minorUnits > 0) { "Quantity must be positive" }
        require(scale in 0..9) { "Quantity scale must be between 0 and 9" }
        require(unit.isNotBlank()) { "Quantity unit is required" }
    }

    fun asDecimal(): BigDecimal = BigDecimal.valueOf(minorUnits, scale)
}

data class Purchase(
    val id: Long = 0L,
    val productId: Long,
    val transactionId: Long? = null,
    val categoryId: Long? = null,
    val merchantName: String? = null,
    val quantity: Quantity,
    val unitPrice: Money,
    val totalAmount: Money,
    val purchaseDate: LocalDate,
    val notes: String? = null,
    val createdAt: Instant,
)

data class PurchaseSummary(
    val totalSpend: Money,
    val purchaseCount: Int,
    val quantityByProduct: Map<Long, Quantity> = emptyMap(),
    val spendByProduct: Map<Long, Money> = emptyMap(),
)

fun calculatePurchaseTotal(unitPrice: Money, quantity: Quantity): Money {
    val total = unitPrice.minorUnits.toBigDecimal()
        .multiply(quantity.asDecimal())
        .setScale(0, RoundingMode.HALF_UP)
        .longValueExact()
    return Money(total, unitPrice.currencyCode)
}
