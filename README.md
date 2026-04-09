# BlazeDemo — teste de performance (JMeter)

Teste de carga no [BlazeDemo](https://www.blazedemo.com): fluxo HTTP de compra ponta a ponta, relatório HTML do JMeter e consolidação em Allure.

Repo: [alexxandrelopesqa/blazedemo-perfomance](https://github.com/alexxandrelopesqa/blazedemo-perfomance.git)

## Critério de aceite

- **Referência do desafio:** `250 RPS` sustentados e `p90 < 2s` (alvo conceitual; os JTL versionados em `results/` não atingiram isso com a carga usada na medição).
- **Gate do `jtl-allure` com os `.jmx` atuais (~30 / ~70 RPS):** RPS ≥ `22` (load) e ≥ `50` (peak); p90 < `8000` ms; falhas `success=false` só reprovam se `error_pct` &gt; `ACCEPTANCE_MAX_ERROR_PCT` (no CI `0.01` = 0,01% para ruído do host público). Overrides: `ACCEPTANCE_RPS`, `ACCEPTANCE_P90_MS`, `STRICT_ACCEPTANCE=0` para só gerar relatório. Código `3` em falha.

Checagem no dashboard de carga (`Aggregate Report`) para o plano em execução:

- Throughput coerente com o timer (~30 ou ~70 req/s nos perfis atuais)
- `90% Line` abaixo do gate (`ACCEPTANCE_P90_MS`)
- `Error %` ≈ `0`

## Fluxo exercitado

1. `GET /`
2. `POST /reserve.php`
3. escolha de voo (`flight`, `price`, `airline` extraídos da resposta)
4. `POST /purchase.php`
5. `POST /confirmation.php`

Validações: HTTP 200 em cada passo; corpo final com `Thank you for your purchase today!`.

## Cenários JMeter

| Arquivo | Objetivo | Throughput alvo | Threads | Ramp-up | Duração |
|---------|----------|-----------------|---------|---------|---------|
| `scripts/load_test.jmx` | carga estável | `1800/min` (~30 RPS) | 60 | 60s | 400s |
| `scripts/peak_test.jmx` | pico | `4200/min` (~70 RPS) | 120 | 30s | 150s |

## Execução

### Docker (recomendado)

Requer Docker Desktop + Compose.

```bash
docker compose build
docker compose run --rm perf-load
docker compose run --rm perf-peak
docker compose run --rm perf-all
```

Saídas típicas:

- `results/load/dashboard/index.html`
- `results/peak/dashboard/index.html`
- `results/allure-report/index.html`
- `allure-results/*-summary.json`

### CLI local

Java 17+, Maven 3.9+ (para compilar `jtl-allure`), JMeter 5.6.3; Allure CLI opcional.

```bash
mvn -f jtl-allure/pom.xml -q package -DskipTests
jmeter -n -t scripts/load_test.jmx -l results/load/load.jtl -e -o results/load/dashboard
jmeter -n -t scripts/peak_test.jmx -l results/peak/peak.jtl -e -o results/peak/dashboard
ACCEPTANCE_P90_MS=8000 ACCEPTANCE_RPS=22 java -jar jtl-allure/target/jtl-allure-1.0.0.jar results/load/load.jtl allure-results "Load 30 RPS"
ACCEPTANCE_P90_MS=8000 ACCEPTANCE_RPS=50 java -jar jtl-allure/target/jtl-allure-1.0.0.jar results/peak/peak.jtl allure-results "Peak 70 RPS"
allure generate allure-results --clean -o results/allure-report
```

Resumos JSON (nome derivado do rótulo do teste no comando):

- `allure-results/load_30_rps-summary.json`
- `allure-results/peak_70_rps-summary.json`

## Evidências

- dashboards JMeter acima
- `results/*/jmeter.log`
- Allure: ambiente, executor, categorias de falha, breakdown por sampler

## CI/CD

- **GitHub Actions**: `.github/workflows/ci.yml` — JMeter com checksum SHA-512; `mvn package` do `jtl-allure` e conversão JTL→Allure (Java) com falha se critério não for atingido; artefatos e Pages em `main`/`master`; upload com `always()` para preservar evidências mesmo com falha.
- **Agendamento**: execução diária às **08:00**, **12:00** e **18:00** (horário de Brasília, `America/Sao_Paulo`), via `schedule` em UTC (`0 11 * * *`, `0 15 * * *`, `0 21 * * *`). Disparos agendados usam o branch padrão do repositório; pequenos atrasos são possíveis em horários de pico do GitHub Actions.
- **Jenkins**: `Jenkinsfile` — Docker, `catchError` nos estágios de teste, Allure só se existir JTL.

## Rastreabilidade

Commits e PRs no GitHub; execuções em **Actions** e artefatos do workflow. Ver [AUDIT.md](AUDIT.md).

Opcional após clone: `git config core.hooksPath .githooks` (ver `.githooks/README`).

## Decisões de projeto

Detalhes em [DECISIONS.md](DECISIONS.md) (ferramenta, fluxo E2E, CSV, perfis, relatórios, Docker, endurecimento).

## Problemas frequentes

- **Dashboard não gera**: pasta `-e -o` deve estar vazia ou ser nova.
- **RPS baixo**: CPU/RAM do gerador; reduzir threads ou aumentar ramp-up.
- **Timeouts**: `connect_timeout_ms` / `response_timeout_ms` nos `.jmx`; rede até `www.blazedemo.com`.

## Baseline registrado

Não atende `p90 < 2s` na rodada documentada nos JTL versionados em `results/`.

**Fonte dos números:** `results/load/load.jtl` e `results/peak/peak.jtl` (agregação igual ao módulo `jtl-allure`: p90 sobre `elapsed` de todas as amostras; throughput = amostras / duração da janela do teste).

**Load (`load_test.jmx`):** throughput `366.6 RPS`, p90 `5078 ms`, falhas `2002 / 220121`, latência média (`elapsed`) `1723.68 ms`.

**Peak (`peak_test.jmx`):** throughput `378.68 RPS`, p90 `7533 ms`, falhas `2114 / 90921`, latência média (`elapsed`) `2499.33 ms`.

Após gerar novos `.jtl`, atualize esta secção com: `java -cp jtl-allure/target/jtl-allure-1.0.0.jar com.blazedemo.perf.PrintBaselineApp` (ou acrescentar `--json`).

## Próximos passos

Ajustar threads, ramp-up, pacing e timeouts em iterações; ambiente de execução fixo; p90 por transação para achar gargalo.
