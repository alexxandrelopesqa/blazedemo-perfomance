package com.blazedemo.perf;

import java.util.Locale;

public final class Config {

    public final String targetUrl;
    public final double acceptanceRps;
    public final int acceptanceP90Ms;
    public final int topFailedSamples;
    public final boolean strictAcceptance;

    public Config() {
        this.targetUrl = env("TARGET_URL", "https://www.blazedemo.com");
        this.acceptanceRps = parseDoubleEnv("ACCEPTANCE_RPS", 22.0);
        this.acceptanceP90Ms = (int) parseDoubleEnv("ACCEPTANCE_P90_MS", 8000.0);
        this.topFailedSamples = (int) parseDoubleEnv("ALLURE_FAILED_SAMPLES", 5.0);
        String s = env("STRICT_ACCEPTANCE", "1").trim().toLowerCase(Locale.ROOT);
        this.strictAcceptance = !(s.equals("0") || s.equals("false") || s.equals("no"));
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isEmpty() ? def : v;
    }

    private static double parseDoubleEnv(String key, double def) {
        String v = System.getenv(key);
        if (v == null || v.isEmpty()) {
            return def;
        }
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
