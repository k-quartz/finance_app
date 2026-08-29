# Performance Baseline

Phase 15 keeps deterministic performance checks in the unit-test suite. The checks are intentionally bounded rather than tied to one workstation's absolute timing.

## Baselines

| Workload | Dataset | Guardrail | Test |
| --- | ---: | ---: | --- |
| Transaction analytics | 50,000 transactions | under 5,000 ms | `PerformanceBaselineTest` |
| Transaction date-range query | indexed Room query | phase 15 composite index selected | `DatabaseQueryPlanTest` |
| Product price history comparison | batched product/history query | phase 15 composite index selected | `DatabaseQueryPlanTest` |

The analytics test prints `PERFORMANCE_BASELINE transaction_analytics_50k_ms=<value>` into the Gradle test output. Record that value with the verification artifact when comparing runs; the pass/fail guardrail remains the stable regression contract.

Run the baseline checks with:

```text
.\gradlew.bat :app:testDebugUnitTest --tests com.financialapp.performance.* --no-daemon
```

The Room query plan checks guard against accidental removal of the date/filter indexes. Product price comparisons load product metadata and all requested histories in two bounded queries instead of issuing one metadata and one history query per product.
