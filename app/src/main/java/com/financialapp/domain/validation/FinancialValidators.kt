package com.financialapp.domain.validation

import com.financialapp.domain.model.Account
import com.financialapp.domain.model.Category
import com.financialapp.domain.model.CategoryType
import com.financialapp.domain.model.DomainValidationException
import com.financialapp.domain.model.FinancialTransaction
import com.financialapp.domain.model.Product
import com.financialapp.domain.model.Purchase
import com.financialapp.domain.model.Quantity
import com.financialapp.domain.model.TransactionType
import com.financialapp.domain.model.Transfer
import com.financialapp.domain.model.calculatePurchaseTotal

object FinancialValidators {
    fun validateProduct(product: Product) {
        if (product.id != 0L) throw DomainValidationException("New product must not have an id")
        if (product.name.isBlank()) throw DomainValidationException("Product name is required")
        if (product.defaultUnit.isBlank()) throw DomainValidationException("Product unit is required")
        if (product.isDeleted || !product.isActive) throw DomainValidationException("New product must be active")
        validateInr(product.defaultCurrencyCode)
        if (product.categoryId != null && product.categoryId <= 0) {
            throw DomainValidationException("Product category is invalid")
        }
    }

    fun validateQuantity(quantity: Quantity) {
        if (quantity.minorUnits <= 0) throw DomainValidationException("Quantity must be positive")
        if (quantity.scale !in 0..9) throw DomainValidationException("Quantity scale must be between 0 and 9")
        if (quantity.unit.isBlank()) throw DomainValidationException("Quantity unit is required")
    }

    fun validatePurchase(
        purchase: Purchase,
        product: Product,
        category: Category? = null,
        categoryAncestors: List<Category> = emptyList(),
    ) {
        if (purchase.id != 0L) throw DomainValidationException("New purchase must not have an id")
        if (purchase.productId <= 0 || purchase.productId != product.id) {
            throw DomainValidationException("Purchase product is required")
        }
        if (!product.isActive || product.isDeleted) throw DomainValidationException("Inactive or deleted products cannot be purchased")
        validateQuantity(purchase.quantity)
        validateInr(purchase.unitPrice.currencyCode)
        validateInr(purchase.totalAmount.currencyCode)
        if (purchase.unitPrice.minorUnits <= 0) throw DomainValidationException("Unit price must be positive")
        val expectedTotal = calculatePurchaseTotal(purchase.unitPrice, purchase.quantity)
        if (purchase.totalAmount != expectedTotal) throw DomainValidationException("Purchase total does not match quantity and unit price")
        if (purchase.quantity.unit != product.defaultUnit) throw DomainValidationException("Purchase unit must match product unit")
        if (purchase.categoryId != null && category == null) throw DomainValidationException("Category does not exist")
        if (category != null) validateCategory(category, categoryAncestors.firstOrNull(), categoryAncestors.drop(1), requireActive = true)
    }

    fun validateAccount(account: Account) {
        if (account.name.isBlank()) throw DomainValidationException("Account name is required")
        validateInr(account.openingBalance.currencyCode)
        if (account.openingBalance.minorUnits < 0) {
            throw DomainValidationException("Opening balance cannot be negative")
        }
    }

    fun validateTransaction(
        transaction: FinancialTransaction,
        category: Category? = null,
        categoryAncestors: List<Category> = emptyList(),
    ) {
        if (transaction.accountId <= 0) throw DomainValidationException("Account is required")
        if (transaction.id != 0L) throw DomainValidationException("New transaction must not have an id")
        if (transaction.transferId != null) throw DomainValidationException("Transfers must use transfer creation")
        validateInr(transaction.amount.currencyCode)
        if (transaction.amount.minorUnits <= 0) throw DomainValidationException("Transaction amount must be positive")
        if (transaction.type == TransactionType.TRANSFER) {
            throw DomainValidationException("Use transfer validation for transfer transactions")
        }
        if (transaction.type != TransactionType.TRANSFER && category != null && category.type != categoryTypeFor(transaction.type)) {
            throw DomainValidationException("Category type does not match transaction type")
        }
        if (transaction.type != TransactionType.TRANSFER && transaction.categoryId == null) {
            throw DomainValidationException("Category is required for income and expense transactions")
        }
        if (category != null) {
            validateCategory(category, categoryAncestors.firstOrNull(), categoryAncestors.drop(1), requireActive = true)
        }
    }

    fun validateTransfer(transfer: Transfer) {
        if (transfer.transferId.isBlank()) {
            throw DomainValidationException("Transfer identity is required")
        }
        if (transfer.sourceAccountId <= 0 || transfer.destinationAccountId <= 0) {
            throw DomainValidationException("Source and destination accounts are required")
        }
        if (transfer.sourceAccountId == transfer.destinationAccountId) {
            throw DomainValidationException("Transfer source and destination must differ")
        }
        validateInr(transfer.amount.currencyCode)
        if (transfer.amount.minorUnits <= 0) throw DomainValidationException("Transfer amount must be positive")
    }

    fun validateCategory(
        category: Category,
        parent: Category? = null,
        ancestors: List<Category> = emptyList(),
        requireActive: Boolean = false,
    ) {
        if (category.name.isBlank()) throw DomainValidationException("Category name is required")
        if (requireActive && (!category.isActive || category.isDeleted)) {
            throw DomainValidationException("Inactive or deleted categories cannot be used by new transactions")
        }
        if (category.parentId == category.id && category.id != 0L) {
            throw DomainValidationException("Category cannot be its own parent")
        }
        if (category.parentId != null && parent == null) {
            throw DomainValidationException("Parent category is required")
        }
        if (parent != null && parent.id == category.id) {
            throw DomainValidationException("Category cannot be its own parent")
        }
        val chain = listOfNotNull(parent) + ancestors
        val visited = mutableSetOf<Long>()
        chain.forEach { ancestor ->
            if (!visited.add(ancestor.id) || ancestor.id == category.id) {
                throw DomainValidationException("Category hierarchy contains a cycle")
            }
            if (ancestor.parentId == category.id) {
                throw DomainValidationException("Category hierarchy contains a cycle")
            }
            if (ancestor.type != category.type) {
                throw DomainValidationException("Parent and child category types must match")
            }
            if (requireActive && (!ancestor.isActive || ancestor.isDeleted)) {
                throw DomainValidationException("Inactive or deleted parent categories cannot be used")
            }
        }
    }

    private fun categoryTypeFor(transactionType: TransactionType): CategoryType = when (transactionType) {
        TransactionType.INCOME -> CategoryType.INCOME
        TransactionType.EXPENSE -> CategoryType.EXPENSE
        TransactionType.TRANSFER -> CategoryType.TRANSFER
    }

    private fun validateInr(currencyCode: String) {
        if (currencyCode != "INR") throw DomainValidationException("Phase 4 supports INR only")
    }
}
