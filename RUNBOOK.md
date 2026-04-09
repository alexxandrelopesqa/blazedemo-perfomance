# Runbook de execucao

Guia rápido para executar, validar e diagnosticar o projeto.

## 1) Execucao recomendada (Docker Compose)

```bash
docker compose build
docker compose run --rm perf-load
docker compose run --rm perf-peak
docker compose run --rm perf-all
```

Saídas esperadas:

- `results/load/dashboard/index.html`
- `results/peak/dashboard/index.html`
- `results/allure-report/index.html`
- `allure-results/*-summary.json`

## 2) Validacao rapida do criterio

Abrir:

- `results/load/dashboard/index.html` (Aggregate Report)

Checar (para os perfis atuais nos `.jmx`, ~30 e ~70 RPS):

- Throughput coerente com o cenário (ver tabela no README)
- `90% Line` abaixo do gate (`ACCEPTANCE_P90_MS`, padrão `8000` ms no projeto)
- Taxa de erro no JTL (`success=false`) dentro do limiar (`ACCEPTANCE_MAX_ERROR_PCT`, no CI `0.01`)
- `Error %` próximo de `0`

Se qualquer item falhar, considerar baseline reprovado no critério.

## 3) Comandos de apoio (CLI)

```bash
mvn -f jtl-allure/pom.xml -q package -DskipTests
ACCEPTANCE_P90_MS=8000 ACCEPTANCE_RPS=22 java -jar jtl-allure/target/jtl-allure-1.0.0.jar results/load/load.jtl allure-results "Load 30 RPS"
ACCEPTANCE_P90_MS=8000 ACCEPTANCE_RPS=50 java -jar jtl-allure/target/jtl-allure-1.0.0.jar results/peak/peak.jtl allure-results "Peak 70 RPS"
```

Se o critério não for atendido, o processo termina com código `3` (use `STRICT_ACCEPTANCE=0` para só gerar o relatório).

Para listar métricas agregadas dos JTL atuais (mesma lógica do Allure) e copiar para o README:

```bash
java -cp jtl-allure/target/jtl-allure-1.0.0.jar com.blazedemo.perf.PrintBaselineApp
```

## 4) Problemas comuns

### Dashboard nao gera

- Verificar se o diretório de saída já existe com conteúdo antigo.
- O JMeter exige pasta nova ou vazia para `-e -o`.

### RPS baixo na máquina local

- Máquina geradora de carga pode estar saturada (CPU/RAM).
- Ajustar ramp-up e quantidade de threads antes de subir throughput.

### Muitos timeouts

- Revisar `connect_timeout_ms` e `response_timeout_ms` no `.jmx`.
- Validar estabilidade da rede para `www.blazedemo.com`.

## 5) GitHub Actions (CI e rodada diaria)

- **Quando roda**: em cada `push`/`pull_request` nas branches configuradas, manualmente (`workflow_dispatch`) e **todo dia às 08:00, 12:00 e 18:00** horário de Brasília (crons `0 11 * * *`, `0 15 * * *`, `0 21 * * *` em UTC no workflow).
- **Onde ver**: aba *Actions* do repositório; artefatos (`blazedemo-performance-reports`) e, em `main`/`master`, deploy para GitHub Pages quando aplicável.
- Rodadas agendadas executam no **branch padrão**; o horário no YAML é sempre **UTC** — para mudar o horário local, ajuste o cron ou use uma tabela de conversão.

## 6) Coleta para anexar em avaliação técnica

Salvar/compartilhar:

- `results/load/dashboard/*`
- `results/peak/dashboard/*`
- `allure-results/*-summary.json`
- trecho de `results/load/jmeter.log` e `results/peak/jmeter.log` com erros principais
