#!/usr/bin/env sh
set -eu

mkdir -p /workspace/results/load /workspace/results/peak /workspace/results/allure-report /workspace/allure-results

echo "[run-all] Executando load_test.jmx"
jmeter -n -t /workspace/scripts/load_test.jmx -l /workspace/results/load/load.jtl -j /workspace/results/load/jmeter.log -e -o /workspace/results/load/dashboard

echo "[run-all] Executando peak_test.jmx"
jmeter -n -t /workspace/scripts/peak_test.jmx -l /workspace/results/peak/peak.jtl -j /workspace/results/peak/jmeter.log -e -o /workspace/results/peak/dashboard

echo "[run-all] Convertendo JTL para Allure"
python3 /workspace/scripts/jtl_to_allure.py /workspace/results/load/load.jtl /workspace/allure-results "Load Test 250 RPS"
python3 /workspace/scripts/jtl_to_allure.py /workspace/results/peak/peak.jtl /workspace/allure-results "Peak Test 350 RPS"

echo "[run-all] Gerando relatorio Allure"
allure generate /workspace/allure-results --clean -o /workspace/results/allure-report

echo "[run-all] Execucao finalizada com sucesso"
