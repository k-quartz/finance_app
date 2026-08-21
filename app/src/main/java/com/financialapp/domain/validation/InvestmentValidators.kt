package com.financialapp.domain.validation

import com.financialapp.domain.model.Account
import com.financialapp.domain.model.FundUnits
import com.financialapp.domain.model.DomainValidationException
import com.financialapp.domain.model.Money
import com.financialapp.domain.model.MutualFund
import com.financialapp.domain.model.MutualFundTransaction
import com.financialapp.domain.model.calculateInvestmentAmount

object InvestmentValidators {
    fun validateFund(fund: MutualFund) {
        if (fund.id != 0L) throw DomainValidationException("New mutual fund must not have an id")
        if (fund.name.isBlank()) throw DomainValidationException("Mutual fund name is required")
        if (fund.code.isBlank()) throw DomainValidationException("Mutual fund code is required")
        if (!fund.isActive || fund.isDeleted) throw DomainValidationException("New mutual fund must be active")
        if (fund.currencyCode != "INR") throw DomainValidationException("Phase 10 supports INR only")
    }

    fun validateUnits(units: FundUnits) {
        if (units.minorUnits <= 0L) throw DomainValidationException("Fund units must be positive")
        if (units.scale !in 0..9) throw DomainValidationException("Fund unit scale must be between 0 and 9")
    }

    fun validateTransaction(
        transaction: MutualFundTransaction,
        fund: MutualFund,
        investmentAccount: Account,
        cashAccount: Account?,
        linkedCashAmount: Money?,
    ) {
        if (transaction.id != 0L) throw DomainValidationException("New investment transaction must not have an id")
        if (transaction.fundId != fund.id || transaction.fundId <= 0L) throw DomainValidationException("Mutual fund is required")
        if (!fund.isActive || fund.isDeleted) throw DomainValidationException("Inactive or deleted mutual funds cannot be used")
        if (investmentAccount.type != com.financialapp.domain.model.AccountType.INVESTMENT) throw DomainValidationException("Investment account is required")
        if (!investmentAccount.isActive || investmentAccount.isDeleted) throw DomainValidationException("Investment account must be active")
        if (cashAccount != null) {
            if (cashAccount.id == investmentAccount.id) throw DomainValidationException("Cash and investment accounts must differ")
            if (!cashAccount.isActive || cashAccount.isDeleted) throw DomainValidationException("Cash account must be active")
        }
        if (cashAccount == null && linkedCashAmount != null) throw DomainValidationException("Linked cash transaction requires a cash account")
        validateUnits(transaction.units)
        if (transaction.nav.currencyCode != fund.currencyCode || transaction.totalAmount.currencyCode != fund.currencyCode) throw DomainValidationException("Investment currency must match fund currency")
        if (transaction.nav.minorUnits <= 0L) throw DomainValidationException("NAV must be positive")
        if (transaction.totalAmount != calculateInvestmentAmount(transaction.nav, transaction.units)) throw DomainValidationException("Investment amount does not match units and NAV")
        if (linkedCashAmount != null && linkedCashAmount != transaction.totalAmount) throw DomainValidationException("Linked cash amount must match investment amount")
    }
}