package com.financialapp.domain.calculation

import com.financialapp.domain.model.Account
import com.financialapp.domain.model.AccountBalance
import com.financialapp.domain.model.FinancialTransaction
import com.financialapp.domain.model.Money
import com.financialapp.domain.model.TransactionType
import com.financialapp.domain.model.Transfer

object BalanceCalculator {
    fun calculate(account: Account, transactions: List<FinancialTransaction>): AccountBalance {
        var balance = account.openingBalance
        transactions.forEach { transaction ->
            if (transaction.amount.currencyCode != account.openingBalance.currencyCode) {
                error("Transaction currency does not match account currency")
            }
            balance = balance + effect(account, transaction)
        }
        return AccountBalance(account.id, balance, account.type)
    }

    fun applyTransfer(source: AccountBalance, destination: AccountBalance, transfer: Transfer): Pair<AccountBalance, AccountBalance> {
        require(source.amount.currencyCode == transfer.amount.currencyCode)
        require(destination.amount.currencyCode == transfer.amount.currencyCode)
        return Pair(
            source.copy(amount = source.amount - transfer.amount),
            destination.copy(
                amount = if (destination.accountType == com.financialapp.domain.model.AccountType.CREDIT_CARD) {
                    destination.amount - transfer.amount
                } else {
                    destination.amount + transfer.amount
                },
            ),
        )
    }

    private fun effect(account: Account, transaction: FinancialTransaction): Money = when (transaction.type) {
        TransactionType.INCOME -> if (account.type == com.financialapp.domain.model.AccountType.CREDIT_CARD) {
            Money(-transaction.amount.minorUnits, transaction.amount.currencyCode)
        } else {
            transaction.amount
        }
        TransactionType.EXPENSE -> if (account.type == com.financialapp.domain.model.AccountType.CREDIT_CARD) transaction.amount else Money(-transaction.amount.minorUnits, transaction.amount.currencyCode)
        TransactionType.TRANSFER -> error("Transfers require both account sides and are not standalone transactions")
    }
}
