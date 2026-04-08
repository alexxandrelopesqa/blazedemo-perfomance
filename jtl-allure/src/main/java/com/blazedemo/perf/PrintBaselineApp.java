package com.blazedemo.perf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PrintBaselineApp {

    public static void main(String[] args) throws Exception {
        boolean jsonOut = false;
        for (String a : args) {
            if ("--json".equals(a)) {
                jsonOut = true;
            }
        }
        Path repo = resolveRepoRoot(Paths.get(System.getProperty("user.dir")));
        Path loadPath = repo.resolve("results/load/load.jtl");
        Path peakPath = repo.resolve("results/peak/peak.jtl");

        if (!Files.exists(loadPath)) {
            System.err.println("Ficheiro em falta: " + loadPath);
            System.exit(1);
        }
        if (!Files.exists(peakPath)) {
            System.err.println("Ficheiro em falta: " + peakPath);
            System.exit(1);
        }

        List<ObjectNode> out = new ArrayList<>();
        out.add(aggregate("Load 250 RPS", loadPath, repo));
        out.add(aggregate("Peak 350 RPS", peakPath, repo));

        ObjectMapper mapper = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
        if (jsonOut) {
            ArrayNode arr = mapper.createArrayNode();
            for (ObjectNode o : out) {
                arr.add(o);
            }
            System.out.println(mapper.writeValueAsString(arr));
            return;
        }

        System.out.println("Métricas (mesma base que jtl-allure / elapsed p90):\n");
        for (ObjectNode row : out) {
            String scenario = row.get("scenario").asText();
            String jtl = row.get("jtl").asText();
            System.out.println("**" + scenario + "** (`" + jtl + "`)");
            System.out.printf(
                    Locale.ROOT,
                    "- throughput: **%s RPS**, p90: **%s ms**, falhas: **%s / %s**, latência média (elapsed): **%s ms**%n%n",
                    row.get("rps").asText(),
                    row.get("p90_ms").asText(),
                    row.get("failed").asText(),
                    row.get("samples").asText(),
                    row.get("avg_ms").asText()
            );
        }
    }

    static Path resolveRepoRoot(Path start) {
        Path p = start.toAbsolutePath().normalize();
        while (p != null) {
            if (Files.isRegularFile(p.resolve("scripts/load_test.jmx"))) {
                return p;
            }
            p = p.getParent();
        }
        return start.toAbsolutePath().normalize();
    }

    private static ObjectNode aggregate(String label, Path path, Path repo) throws Exception {
        List<Map<String, String>> rows = JtlCsvReader.readAll(path);
        MetricsResult m = MetricsCalculator.computeMetrics(rows);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode n = mapper.createObjectNode();
        n.put("scenario", label);
        n.put("jtl", repo.relativize(path).normalize().toString().replace('\\', '/'));
        n.put("samples", m.total());
        n.put("failed", m.failed());
        n.put("error_pct", round4(m.errorPct()));
        n.put("avg_ms", round2(m.avgMs()));
        n.put("avg_latency_ms", round2(m.avgLatencyMs()));
        n.put("p90_ms", round2(m.p90Ms()));
        n.put("rps", round2(m.rps()));
        return n;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private PrintBaselineApp() {}
}
