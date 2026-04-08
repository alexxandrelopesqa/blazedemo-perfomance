package com.blazedemo.perf;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class JtlToAllure {

    public static final int EXIT_ACCEPTANCE_FAILED = 3;

    private JtlToAllure() {}

    public static void run(Path jtlInput, Path allureResults, String testName, Config config) throws IOException {
        List<Map<String, String>> rows = JtlCsvReader.readAll(jtlInput);
        MetricsResult metrics = MetricsCalculator.computeMetrics(rows);
        Map<String, List<Map<String, String>>> grouped = groupRowsByLabel(rows);

        Files.createDirectories(allureResults);
        writeTestSummaryCase(allureResults, testName, jtlInput.toString(), metrics, rows, config);
        writeLabelCases(allureResults, testName, grouped, config);
        writeEnvironmentProperties(allureResults, config);
        writeExecutorJson(allureResults);
        writeCategoriesJson(allureResults);
        writeSummaryFile(allureResults, testName, metrics, config);

        StatusResult sr = buildStatus(metrics, config);
        if ("failed".equals(sr.status()) && config.strictAcceptance) {
            System.err.println(sr.message());
            System.exit(EXIT_ACCEPTANCE_FAILED);
        }
    }

    private record StatusResult(String status, String message) {}

    private static StatusResult buildStatus(MetricsResult m, Config config) {
        if (m.failed() > 0) {
            return new StatusResult(
                    "failed",
                    "Foram detectadas falhas funcionais: " + m.failed() + " amostras com success=false."
            );
        }
        if (m.p90Ms() >= config.acceptanceP90Ms || m.rps() < config.acceptanceRps) {
            return new StatusResult(
                    "failed",
                    String.format(
                            Locale.ROOT,
                            "SLO nao atendido. rps=%.2f (meta >= %.0f), p90=%.2fms (meta < %dms).",
                            m.rps(),
                            config.acceptanceRps,
                            m.p90Ms(),
                            config.acceptanceP90Ms
                    )
            );
        }
        return new StatusResult("passed", "Execucao concluida dentro do SLO esperado.");
    }

    private static Map<String, List<Map<String, String>>> groupRowsByLabel(List<Map<String, String>> rows) {
        Map<String, List<Map<String, String>>> grouped = new LinkedHashMap<>();
        for (Map<String, String> r : rows) {
            String label = r.getOrDefault("label", "unknown");
            grouped.computeIfAbsent(label, k -> new ArrayList<>()).add(r);
        }
        return grouped;
    }

    private static Map<String, Object> writeAttachment(Path outputDir, String name, String content, String mimeType)
            throws IOException {
        String suffix = "application/json".equals(mimeType) ? ".json" : ".txt";
        String source = UUID.randomUUID() + "-attachment" + suffix;
        Files.writeString(outputDir.resolve(source), content, StandardCharsets.UTF_8);
        Map<String, Object> att = new HashMap<>();
        att.put("name", name);
        att.put("source", source);
        att.put("type", mimeType);
        return att;
    }

    private static void writeAllureResult(Path outputDir, ObjectNode result) throws IOException {
        String file = UUID.randomUUID() + "-result.json";
        Files.writeString(outputDir.resolve(file), JsonHelper.mapper().writeValueAsString(result), StandardCharsets.UTF_8);
    }

    private static List<Map<String, String>> buildCommonLabels(String testName) {
        List<Map<String, String>> labels = new ArrayList<>();
        labels.add(Map.of("name", "framework", "value", "jmeter"));
        labels.add(Map.of("name", "language", "value", "groovy"));
        labels.add(Map.of("name", "suite", "value", "BlazeDemo"));
        labels.add(Map.of("name", "parentSuite", "value", "Performance"));
        labels.add(Map.of("name", "epic", "value", "BlazeDemo"));
        labels.add(Map.of("name", "feature", "value", "Checkout"));
        labels.add(Map.of("name", "story", "value", testName));
        return labels;
    }

    private static void writeTestSummaryCase(
            Path outputDir,
            String testName,
            String sourceFile,
            MetricsResult metrics,
            List<Map<String, String>> rows,
            Config config
    ) throws IOException {
        StatusResult sr = buildStatus(metrics, config);
        List<Map<String, String>> labels = new ArrayList<>(buildCommonLabels(testName));
        Map<String, String> sev = new HashMap<>();
        sev.put("name", "severity");
        sev.put("value", "failed".equals(sr.status()) ? "critical" : "normal");
        labels.add(sev);

        List<Map<String, String>> failedRows = rows.stream()
                .filter(r -> {
                    String s = r.get("success");
                    return s == null || !s.toLowerCase(Locale.ROOT).equals("true");
                })
                .limit(config.topFailedSamples)
                .collect(Collectors.toList());

        List<Map<String, Object>> sampleErrors = new ArrayList<>();
        for (Map<String, String> r : failedRows) {
            Map<String, Object> err = new HashMap<>();
            err.put("label", r.getOrDefault("label", "unknown"));
            err.put("responseCode", r.getOrDefault("responseCode", ""));
            err.put("responseMessage", r.getOrDefault("responseMessage", ""));
            err.put("elapsed", MetricsCalculator.safeInt(r.get("elapsed"), 0));
            err.put("url", r.getOrDefault("URL", ""));
            sampleErrors.add(err);
        }

        var metricsSummary = new HashMap<String, Object>();
        metricsSummary.put("test_name", testName);
        metricsSummary.put("source_file", sourceFile);
        metricsSummary.put("target_url", config.targetUrl);
        metricsSummary.put("total_samples", metrics.total());
        metricsSummary.put("failed_samples", metrics.failed());
        metricsSummary.put("error_pct", round4(metrics.errorPct()));
        metricsSummary.put("avg_ms", round2(metrics.avgMs()));
        metricsSummary.put("avg_latency_ms", round2(metrics.avgLatencyMs()));
        metricsSummary.put("p90_ms", round2(metrics.p90Ms()));
        metricsSummary.put("rps", round2(metrics.rps()));
        metricsSummary.put("acceptance_rps", config.acceptanceRps);
        metricsSummary.put("acceptance_p90_ms", config.acceptanceP90Ms);

        List<Map<String, Object>> attachments = new ArrayList<>();
        attachments.add(writeAttachment(
                outputDir,
                "metrics-summary",
                JsonHelper.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(metricsSummary),
                "application/json"
        ));
        attachments.add(writeAttachment(
                outputDir,
                "failed-sample-extract",
                JsonHelper.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(sampleErrors),
                "application/json"
        ));

        ObjectNode statusDetails = JsonHelper.mapper().createObjectNode();
        statusDetails.put("message", sr.message());

        ObjectNode result = JsonHelper.mapper().createObjectNode();
        result.put("uuid", UUID.randomUUID().toString());
        result.put("name", testName);
        result.put("fullName", "BlazeDemo::" + testName);
        result.put("historyId", testName.replace(" ", "_").toLowerCase(Locale.ROOT));
        result.put("status", sr.status());
        result.set("statusDetails", statusDetails);
        result.put("stage", "finished");
        result.put("start", metrics.minTs());
        result.put("stop", metrics.maxTs());
        result.set("labels", JsonHelper.mapper().valueToTree(labels));

        ArrayNode parameters = JsonHelper.mapper().createArrayNode();
        parameters.add(param("target_url", config.targetUrl));
        parameters.add(param("source_file", sourceFile));
        parameters.add(param("total_samples", String.valueOf(metrics.total())));
        parameters.add(param("failed_samples", String.valueOf(metrics.failed())));
        parameters.add(param("error_pct", String.format(Locale.ROOT, "%.4f", metrics.errorPct())));
        parameters.add(param("avg_ms", String.format(Locale.ROOT, "%.2f", metrics.avgMs())));
        parameters.add(param("avg_latency_ms", String.format(Locale.ROOT, "%.2f", metrics.avgLatencyMs())));
        parameters.add(param("p90_ms", String.format(Locale.ROOT, "%.2f", metrics.p90Ms())));
        parameters.add(param("rps", String.format(Locale.ROOT, "%.2f", metrics.rps())));
        parameters.add(param("acceptance_rps", String.format(Locale.ROOT, "%.0f", config.acceptanceRps)));
        parameters.add(param("acceptance_p90_ms", String.valueOf(config.acceptanceP90Ms)));
        result.set("parameters", parameters);
        result.set("attachments", JsonHelper.mapper().valueToTree(attachments));

        writeAllureResult(outputDir, result);
    }

    private static ObjectNode param(String name, String value) {
        ObjectNode o = JsonHelper.mapper().createObjectNode();
        o.put("name", name);
        o.put("value", value);
        return o;
    }

    private static void writeLabelCases(
            Path outputDir,
            String testName,
            Map<String, List<Map<String, String>>> grouped,
            Config config
    ) throws IOException {
        for (Map.Entry<String, List<Map<String, String>>> e : grouped.entrySet()) {
            String label = e.getKey();
            List<Map<String, String>> labelRows = e.getValue();
            MetricsResult metrics = MetricsCalculator.computeMetrics(labelRows);
            StatusResult sr = buildStatus(metrics, config);

            Map<String, Long> responseCodes = new HashMap<>();
            Map<String, Long> errorsByMessage = new HashMap<>();
            for (Map<String, String> r : labelRows) {
                String code = r.getOrDefault("responseCode", "unknown");
                responseCodes.merge(code, 1L, Long::sum);
                String success = r.get("success");
                if (success == null || !success.toLowerCase(Locale.ROOT).equals("true")) {
                    String msg = r.getOrDefault("responseMessage", "unknown");
                    errorsByMessage.merge(msg, 1L, Long::sum);
                }
            }

            List<List<Object>> topErrorMessages = errorsByMessage.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(5)
                    .map(en -> List.<Object>of(en.getKey(), en.getValue()))
                    .collect(Collectors.toList());

            List<Map<String, String>> labels = new ArrayList<>(buildCommonLabels(testName));
            labels.add(Map.of("name", "subSuite", "value", "Samplers"));
            labels.add(Map.of("name", "story", "value", label));
            labels.add(Map.of("name", "severity", "value", "normal"));

            Map<String, Object> detailPayload = new HashMap<>();
            detailPayload.put("test_name", testName);
            detailPayload.put("sampler_label", label);
            detailPayload.put("samples", metrics.total());
            detailPayload.put("failed", metrics.failed());
            detailPayload.put("error_pct", round4(metrics.errorPct()));
            detailPayload.put("rps", round2(metrics.rps()));
            detailPayload.put("avg_ms", round2(metrics.avgMs()));
            detailPayload.put("avg_latency_ms", round2(metrics.avgLatencyMs()));
            detailPayload.put("p90_ms", round2(metrics.p90Ms()));
            detailPayload.put("response_codes", responseCodes);
            detailPayload.put("top_error_messages", topErrorMessages);

            List<Map<String, Object>> attachments = new ArrayList<>();
            attachments.add(writeAttachment(
                    outputDir,
                    "sampler-metrics",
                    JsonHelper.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(detailPayload),
                    "application/json"
            ));

            ObjectNode statusDetails = JsonHelper.mapper().createObjectNode();
            statusDetails.put("message", sr.message());

            ObjectNode result = JsonHelper.mapper().createObjectNode();
            result.put("uuid", UUID.randomUUID().toString());
            result.put("name", testName + " :: " + label);
            result.put("fullName", "BlazeDemo::" + testName + "::" + label);
            result.put("historyId", (testName + "-" + label).replace(" ", "_").toLowerCase(Locale.ROOT));
            result.put("status", sr.status());
            result.set("statusDetails", statusDetails);
            result.put("stage", "finished");
            result.put("start", metrics.minTs());
            result.put("stop", metrics.maxTs());
            result.set("labels", JsonHelper.mapper().valueToTree(labels));

            ArrayNode parameters = JsonHelper.mapper().createArrayNode();
            parameters.add(param("sampler", label));
            parameters.add(param("samples", String.valueOf(metrics.total())));
            parameters.add(param("failed", String.valueOf(metrics.failed())));
            parameters.add(param("error_pct", String.format(Locale.ROOT, "%.4f", metrics.errorPct())));
            parameters.add(param("p90_ms", String.format(Locale.ROOT, "%.2f", metrics.p90Ms())));
            parameters.add(param("rps", String.format(Locale.ROOT, "%.2f", metrics.rps())));
            result.set("parameters", parameters);
            result.set("attachments", JsonHelper.mapper().valueToTree(attachments));

            writeAllureResult(outputDir, result);
        }
    }

    private static void writeEnvironmentProperties(Path outputDir, Config config) throws IOException {
        Path envFile = outputDir.resolve("environment.properties");
        Map<String, String> existing = new HashMap<>();
        if (Files.exists(envFile)) {
            for (String line : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
                int eq = line.indexOf('=');
                if (eq > 0) {
                    existing.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                }
            }
        }
        String newRps = String.format(Locale.ROOT, "%.0f", config.acceptanceRps);
        if (existing.containsKey("acceptance.rps") && !newRps.equals(existing.get("acceptance.rps"))) {
            existing.put("acceptance.rps.load", existing.remove("acceptance.rps"));
            existing.put("acceptance.rps.peak", newRps);
        } else {
            existing.put("acceptance.rps", newRps);
        }
        existing.put("target.url", config.targetUrl);
        existing.put("acceptance.p90.ms", String.valueOf(config.acceptanceP90Ms));
        existing.put("jmeter.version", envOrDefault("JMETER_VERSION", "5.6.3"));
        existing.put("java.version", System.getProperty("java.version", "unknown"));
        existing.put("runner.os", envOrDefault("RUNNER_OS", System.getProperty("os.name", "")));
        existing.put("runner.arch", envOrDefault("RUNNER_ARCH", envOrDefault("PROCESSOR_ARCHITECTURE", "unknown")));
        existing.put("github.repository", envOrDefault("GITHUB_REPOSITORY", "local"));
        existing.put("github.ref", envOrDefault("GITHUB_REF_NAME", "local"));
        existing.put("github.sha", envOrDefault("GITHUB_SHA", "local"));

        List<String> lines = existing.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(en -> en.getKey() + "=" + en.getValue())
                .collect(Collectors.toList());
        Files.writeString(envFile, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
    }

    private static String envOrDefault(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isEmpty() ? def : v;
    }

    private static void writeExecutorJson(Path outputDir) throws IOException {
        boolean gh = "true".equalsIgnoreCase(System.getenv("GITHUB_ACTIONS"));
        ObjectNode executor = JsonHelper.mapper().createObjectNode();
        executor.put("name", gh ? "GitHub Actions" : "Local Execution");
        executor.put("type", "github");
        executor.put("url", envOrDefault("GITHUB_SERVER_URL", ""));
        executor.put("buildOrder", MetricsCalculator.safeInt(System.getenv("GITHUB_RUN_NUMBER"), 0));
        executor.put("buildName", envOrDefault("GITHUB_WORKFLOW", "local-run"));
        String buildUrl = "";
        if (gh) {
            String server = envOrDefault("GITHUB_SERVER_URL", "");
            String repo = envOrDefault("GITHUB_REPOSITORY", "");
            String runId = envOrDefault("GITHUB_RUN_ID", "");
            buildUrl = server + "/" + repo + "/actions/runs/" + runId;
        }
        executor.put("buildUrl", buildUrl);
        executor.put("reportUrl", "");
        executor.put("reportName", "BlazeDemo performance");
        Files.writeString(
                outputDir.resolve("executor.json"),
                JsonHelper.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(executor),
                StandardCharsets.UTF_8
        );
    }

    private static void writeCategoriesJson(Path outputDir) throws IOException {
        ArrayNode categories = JsonHelper.mapper().createArrayNode();
        ObjectNode c1 = JsonHelper.mapper().createObjectNode();
        c1.put("name", "SLO breach (latency/throughput)");
        c1.set("matchedStatuses", JsonHelper.mapper().valueToTree(List.of("failed")));
        c1.put("messageRegex", "SLO nao atendido.*");
        categories.add(c1);
        ObjectNode c2 = JsonHelper.mapper().createObjectNode();
        c2.put("name", "Functional failure");
        c2.set("matchedStatuses", JsonHelper.mapper().valueToTree(List.of("failed")));
        c2.put("messageRegex", "Foram detectadas falhas funcionais.*");
        categories.add(c2);
        ObjectNode c3 = JsonHelper.mapper().createObjectNode();
        c3.put("name", "Infrastructure timeout/network");
        c3.set("matchedStatuses", JsonHelper.mapper().valueToTree(List.of("broken", "failed")));
        c3.put("traceRegex", ".*(SocketTimeoutException|Read timed out|ConnectException).*");
        categories.add(c3);
        Files.writeString(
                outputDir.resolve("categories.json"),
                JsonHelper.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(categories),
                StandardCharsets.UTF_8
        );
    }

    private static void writeSummaryFile(Path outputDir, String testName, MetricsResult metrics, Config config)
            throws IOException {
        ObjectNode summary = JsonHelper.mapper().createObjectNode();
        summary.put("test_name", testName);
        summary.put("samples", metrics.total());
        summary.put("failed", metrics.failed());
        summary.put("error_pct", round4(metrics.errorPct()));
        summary.put("avg_ms", round2(metrics.avgMs()));
        summary.put("avg_latency_ms", round2(metrics.avgLatencyMs()));
        summary.put("p90_ms", round2(metrics.p90Ms()));
        summary.put("rps", round2(metrics.rps()));
        summary.put("acceptance_rps", config.acceptanceRps);
        summary.put("acceptance_p90_ms", config.acceptanceP90Ms);

        String filename = testName.replace(" ", "_").toLowerCase(Locale.ROOT) + "-summary.json";
        var compact = JsonHelper.mapper().copy().disable(SerializationFeature.INDENT_OUTPUT);
        String json = compact.writeValueAsString(summary);
        Files.writeString(outputDir.resolve(filename), json, StandardCharsets.UTF_8);
        System.out.println(json);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
