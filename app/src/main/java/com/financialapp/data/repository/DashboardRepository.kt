package com.financialapp.data.repository

import com.financialapp.domain.calculation.DashboardCalculator
import com.financialapp.domain.model.DashboardPeriod
import com.financialapp.domain.model.DashboardSummary
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

interface DashboardRepository {
    suspend fun loadDashboard(period: DashboardPeriod, asOfDate: LocalDate): DashboardSummary
}

class LocalDashboardRepository @Inject constructor(
    private val financialRepository: FinancialRepository,
    private val categoryRepository: CategoryRepository,
    private val mutualFundRepository: MutualFundRepository,
    private val productRepository: ProductRepository,
    private val productPriceHistoryRepository: ProductPriceHistoryRepository,
) : DashboardRepository {
    override suspend fun loadDashboard(period: DashboardPeriod, asOfDate: LocalDate): DashboardSummary = coroutineScope {
        val reportingPeriod = DashboardCalculator.combinedReportingPeriod(period, asOfDate)
        val reportDeferred = async { financialRepository.analyze(reportingPeriod) }
        val balancesDeferred = async { financialRepository.getActiveAccountBalances() }
        val portfolioDeferred = async { mutualFundRepository.getPortfolioSummary(asOfDate) }
        val categoriesDeferred = async { flattenCategoryNames(categoryRepository.getTree(includeInactive = false)) }
        val productsDeferred = async { productRepository.getActiveProducts() }

        val report = reportDeferred.await()
        val balances = balancesDeferred.await()
        val portfolio = portfolioDeferred.await()
        val categoryNames = categoriesDeferred.await()
        val activeProducts = productsDeferred.await()
        val priceMovers = if (activeProducts.isEmpty()) {
            emptyList()
        } else {
            productPriceHistoryRepository.compareLatestPrices(activeProducts.map { it.id }, asOfDate).take(3)
        }

        DashboardCalculator.summarize(
            period = period,
            asOfDate = asOfDate,
            report = report,
            accountBalances = balances,
            portfolio = portfolio,
            categoryNames = categoryNames,
            priceMovers = priceMovers,
        )
    }

    private fun flattenCategoryNames(nodes: List<com.financialapp.domain.model.CategoryTreeNode>): Map<Long, String> {
        val names = linkedMapOf<Long, String>()
        fun visit(node: com.financialapp.domain.model.CategoryTreeNode) {
            names[node.category.id] = node.category.name
            node.children.forEach(::visit)
        }
        nodes.forEach(::visit)
        return names
    }
}