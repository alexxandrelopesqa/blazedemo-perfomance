package com.blazedemo.perf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MetricsCalculator {

    private MetricsCalculator() {}

    public static int safeInt(String value, int defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return (int) Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static long safeLong(String value, long defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return (long) Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static double percentile(List<Integer> values, double pct) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        List<Integer> ordered = new ArrayList<>(values);
        Collections.sort(ordered);
        int idx = Math.max(0, Math.min((int) (ordered.size() * pct) - 1, ordered.size() - 1));
        return ordered.get(idx);
    }

    public static MetricsResult computeMetrics(List<Map<String, String>> rows) {
        if (rows == null || rows.isEmpty()) {
            return MetricsResult.empty();
        }
        List<Integer> elapsed = new ArrayList<>();
        List<Integer> latency = new ArrayList<>();
        List<Long> timestamps = new ArrayList<>();
        int failed = 0;
        for (Map<String, String> r : rows) {
            elapsed.add(safeInt(r.get("elapsed"), 0));
            latency.add(safeInt(r.get("Latency"), 0));
            timestamps.add(safeLong(r.get("timeStamp"), 0L));
            String success = r.get("success");
            if (success == null || !success.toLowerCase(Locale.ROOT).equals("true")) {
                failed++;
            }
        }
        long minTs = Collections.min(timestamps);
        long maxTs = Collections.max(timestamps);
        double totalSeconds = Math.max((maxTs - minTs) / 1000.0, 1.0);
        int total = rows.size();

        long sumE = 0;
        for (int e : elapsed) {
            sumE += e;
        }
        long sumL = 0;
        for (int l : latency) {
            sumL += l;
        }

        double errorPct = total > 0 ? (failed * 100.0) / total : 0.0;
        double avgMs = total > 0 ? (double) sumE / total : 0.0;
        double avgLatencyMs = total > 0 ? (double) sumL / total : 0.0;
        double p90 = percentile(elapsed, 0.9);
        double rps = total / totalSeconds;

        return new MetricsResult(total, failed, errorPct, avgMs, avgLatencyMs, p90, rps, minTs, maxTs);
    }
}
