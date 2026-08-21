package com.financialapp.domain

import com.financialapp.domain.calculation.BalanceCalculator
import com.financialapp.domain.model.Account
import com.financialapp.domain.model.AccountType
import com.financialapp.domain.model.Category
import com.financialapp.domain.model.CategoryType
import com.financialapp.domain.model.DomainValidationException
import com.financialapp.domain.model.FinancialTransaction
import com.financialapp.domain.model.Money
import com.financialapp.domain.model.TransactionType
import com.financialapp.domain.model.Transfer
import com.financialapp.domain.validation.FinancialValidators
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FinancialDomainTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val transactionDate = LocalDate.of(2026, 1, 1)
    private val inr = "INR"

    @Test
    fun `money arithmetic remains exact for representative values`() {
        assertEquals(30L, (Money(10, inr) + Money(20, inr)).minorUnits)
        assertEquals(99_999_001L, (Money(99_999_001L, inr) + Money(0, inr)).minorUnits)
        assertEquals(100_000_01L, Money(100_000_01L, inr).minorUnits)
    }

    @Test
    fun `money accepts INR paise and rejects unsupported currencies`() {
        assertEquals(100L, Money(100L, "INR").minorUnits)
        assertThrows(IllegalArgumentException::class.java) { Money(100L, "USD") }
    }

    @Test
    fun `money arithmetic fails safely at Long boundaries`() {
        assertThrows(ArithmeticException::class.java) { Money(Long.MAX_VALUE, inr) + Money(1, inr) }
        assertThrows(ArithmeticException::class.java) { Money(Long.MIN_VALUE, inr) - Money(1, inr) }
    }

    @Test
    fun `account validation rejects blank name and negative opening balance`() {
        val account = Account(name = "", type = AccountType.BANK, openingBalance = Money(-1, inr), createdAt = now, updatedAt = now)
        assertThrows(DomainValidationException::class.java) { FinancialValidators.validateAccount(account) }
    }

    @Test
    fun `income and expense affect normal account balance`() {
        val account = Account(1L, "Bank", AccountType.BANK, Money(10_000, inr), createdAt = now, updatedAt = now)
        val incomeCategory = Category(1L, type = CategoryType.INCOME, name = "Salary")
        val expenseCategory = Category(2L, type = CategoryType.EXPENSE, name = "Food")
        val transactions = listOf(
            FinancialTransaction(accountId = 1L, type = TransactionType.INCOME, amount = Money(2_000, inr), categoryId = incomeCategory.id, transactionDate = transactionDate, createdAt = now),
            FinancialTransaction(accountId = 1L, type = TransactionType.EXPENSE, amount = Money(999, inr), categoryId = expenseCategory.id, transactionDate = transactionDate, createdAt = now),
        )
        assertEquals(11_001L, BalanceCalculator.calculate(account, transactions).amount.minorUnits)
    }

    @Test
    fun `credit card expense increases amount owed`() {
        val account = Account(1L, "Card", AccountType.CREDIT_CARD, Money(0, inr), createdAt = now, updatedAt = now)
        val expense = FinancialTransaction( accountId = 1L, type = TransactionType.EXPENSE, amount = Money(100_000, inr), categoryId = 1L, transactionDate = transactionDate, createdAt = now)
        assertEquals(100_000L, BalanceCalculator.calculate(account, listOf(expense)).amount.minorUnits)
    }

    @Test
    fun `credit card refund decreases amount owed`() {
        val account = Account(1L, "Card", AccountType.CREDIT_CARD, Money(100_000, inr), createdAt = now, updatedAt = now)
        val refund = FinancialTransaction(1L, 1L, TransactionType.INCOME, Money(25_000, inr), categoryId = 1L, transactionDate = transactionDate, createdAt = now)
        assertEquals(75_000L, BalanceCalculator.calculate(account, listOf(refund)).amount.minorUnits)
    }

    @Test
    fun `invalid transaction amounts and transfer self reference are rejected`() {
        val category = Category(1L, type = CategoryType.EXPENSE, name = "Food")
        val invalid = FinancialTransaction(1L, 1L, TransactionType.EXPENSE, Money(0, inr), categoryId = 1L, transactionDate = transactionDate, createdAt = now)
        assertThrows(DomainValidationException::class.java) { FinancialValidators.validateTransaction(invalid, category) }
        assertThrows(DomainValidationException::class.java) {
            FinancialValidators.validateTransfer(Transfer("t1", 1L, 1L, Money(1, inr), transactionDate, now))
        }
    }

    @Test
    fun `transfer changes source and destination without income or expense classification`() {
        val source = com.financialapp.domain.model.AccountBalance(1L, Money(10_000, inr))
        val destination = com.financialapp.domain.model.AccountBalance(2L, Money(0, inr))
        val result = BalanceCalculator.applyTransfer(
            source,
            destination,
            Transfer("transfer-1", 1L, 2L, Money(5_000, inr), transactionDate, now),
        )
        assertEquals(5_000L, result.first.amount.minorUnits)
        assertEquals(5_000L, result.second.amount.minorUnits)
    }

    @Test
    fun `credit card transfer is a payment and reduces amount owed`() {
        val source = com.financialapp.domain.model.AccountBalance(1L, Money(10_000, inr), AccountType.BANK)
        val destination = com.financialapp.domain.model.AccountBalance(2L, Money(10_000, inr), AccountType.CREDIT_CARD)
        val result = BalanceCalculator.applyTransfer(
            source,
            destination,
            Transfer("payment-1", 1L, 2L, Money(1_000, inr), transactionDate, now),
        )
        assertEquals(9_000L, result.first.amount.minorUnits)
        assertEquals(9_000L, result.second.amount.minorUnits)
    }

    @Test
    fun `category hierarchy rejects mismatched types and ancestor cycles`() {
        val parent = Category(1L, type = CategoryType.EXPENSE, name = "Food")
        val child = Category(2L, parentId = 1L, type = CategoryType.INCOME, name = "Salary")
        assertThrows(DomainValidationException::class.java) { FinancialValidators.validateCategory(child, parent) }
        val cycleParent = Category(2L, parentId = 1L, type = CategoryType.EXPENSE, name = "Child")
        val cycleChild = Category(1L, parentId = 2L, type = CategoryType.EXPENSE, name = "Food")
        assertThrows(DomainValidationException::class.java) { FinancialValidators.validateCategory(cycleChild, cycleParent) }

        val grandparent = Category(10L, type = CategoryType.EXPENSE, name = "Needs")
        val middle = Category(11L, parentId = 10L, type = CategoryType.EXPENSE, name = "Food")
        val leaf = Category(12L, parentId = 11L, type = CategoryType.EXPENSE, name = "Groceries")
        FinancialValidators.validateCategory(leaf, middle, listOf(grandparent))

        val threeNodeCycle = Category(20L, parentId = 22L, type = CategoryType.EXPENSE, name = "A")
        assertThrows(DomainValidationException::class.java) {
            FinancialValidators.validateCategory(threeNodeCycle, Category(22L, parentId = 21L, type = CategoryType.EXPENSE, name = "C"), listOf(Category(21L, parentId = 20L, type = CategoryType.EXPENSE, name = "B")))
        }
    }
}
