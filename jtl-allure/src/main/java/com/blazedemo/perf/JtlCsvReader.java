package com.blazedemo.perf;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class JtlCsvReader {

    private JtlCsvReader() {}

    public static List<Map<String, String>> readAll(Path jtlPath) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(false)
                .build();
        try (CSVParser parser = CSVParser.parse(jtlPath, StandardCharsets.UTF_8, format)) {
            for (CSVRecord rec : parser) {
                rows.add(new HashMap<>(rec.toMap()));
            }
        }
        return rows;
    }
}
