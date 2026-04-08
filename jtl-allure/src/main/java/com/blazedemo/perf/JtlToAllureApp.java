package com.blazedemo.perf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class JtlToAllureApp {

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Uso: java -jar jtl-allure.jar <jtl_input> <allure_results_dir> <test_name>");
            System.exit(1);
        }
        Path jtlInput = Paths.get(args[0]);
        Path allureResults = Paths.get(args[1]);
        String testName = args[2];

        if (!Files.exists(jtlInput)) {
            System.err.println("Nao foi possivel encontrar o JTL: " + jtlInput);
            System.exit(2);
        }

        try {
            Config config = new Config();
            JtlToAllure.run(jtlInput, allureResults, testName, config);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private JtlToAllureApp() {}
}
