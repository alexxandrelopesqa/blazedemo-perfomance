package com.blazedemo.perf;

public record MetricsResult(
        int total,
        int failed,
        double errorPct,
        double avgMs,
        double avgLatencyMs,
        double p90Ms,
        double rps,
        long minTs,
        long maxTs
) {
    static MetricsResult empty() {
        return new MetricsResult(0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0L, 0L);
    }
}
