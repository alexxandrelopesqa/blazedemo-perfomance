# BlazeDemo — teste de performance (JMeter)

Teste de carga no [BlazeDemo](https://www.blazedemo.com): fluxo HTTP de compra ponta a ponta, relatório HTML do JMeter e consolidação em Allure.

Repo: [alexxandrelopesqa/blazedemo-perfomance](https://github.com/alexxandrelopesqa/blazedemo-perfomance.git)

## Critério de aceite (desafio)

- **250 requisições por segundo** (média de amostras no JTL no intervalo do teste; o `ConstantThroughputTimer` visa **15000 amostras/min** no grupo de threads, equivalente a ~250 req/s agregadas).
- **p90 do tempo de resposta &lt; 2 s** (`ACCEPTANCE_P90_MS=2000` no `jtl-allure`).

**Gate do `jtl-allure`:** RPS ≥ `250` e p90 &lt; `2000` ms em cada cenário; falhas `success=false` acima de `ACCEPTANCE_MAX_ERROR_PCT` reprovam (no CI **1%** para absorver instabilidade do host público). Variáveis: `ACCEPTANCE_RPS`, `ACCEPTANCE_P90_MS`, `ACCEPTANCE_MAX_ERROR_PCT`, `STRICT_ACCEPTANCE=0` (só gera relatório sem falhar o processo). Código de saída **3** se o gate falhar com aceite estrito.

Checagem no dashboard JMeter (`Aggregate Report`):

- Throughput coerente com o timer (~250 req/s no load, ~350 req/s no pico)
- `90% Line` &lt; `2000` ms quando o SLO for atingido
- `Error %` próximo de zero

## Fluxo exercitado

1. `GET /`
2. `POST /reserve.php`
3. escolha de voo (`flight`, `price`, `airline` extraídos da resposta)
4. `POST /purchase.php`
5. `POST /confirmation.php`

Validações: HTTP 200 em cada passo; corpo final com `Thank you for your purchase today!`.

## Cenários JMeter

| Arquivo | Objetivo | Throughput alvo (timer) | Threads | Ramp-up | Duração |
|---------|----------|---------------------------|---------|---------|---------|
| `scripts/load_test.jmx` | carga estável | `15000/min` (~250 RPS) | 400 | 120s | 300s |
| `scripts/peak_test.jmx` | pico | `21000/min` (~350 RPS) | 500 | 45s | 180s |

O teste de **pico** mantém vazão **acima** do mínimo de 250 RPS exigido pelo desafio.

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
ACCEPTANCE_P90_MS=2000 ACCEPTANCE_RPS=250 ACCEPTANCE_MAX_ERROR_PCT=1.0 java -jar jtl-allure/target/jtl-allure-1.0.0.jar results/load/load.jtl allure-results "Load 250 RPS"
ACCEPTANCE_P90_MS=2000 ACCEPTANCE_RPS=250 ACCEPTANCE_MAX_ERROR_PCT=1.0 java -jar jtl-allure/target/jtl-allure-1.0.0.jar results/peak/peak.jtl allure-results "Peak 350 RPS"
allure generate allure-results --clean -o results/allure-report
```

Resumos JSON (nome derivado do rótulo do teste no comando):

- `allure-results/load_250_rps-summary.json`
- `allure-results/peak_350_rps-summary.json`

## Evidências

- dashboards JMeter acima
- `results/*/jmeter.log`
- Allure: ambiente, executor, categorias de falha, detalhe por sampler

## CI/CD

- **GitHub Actions**: `.github/workflows/ci.yml` — JMeter com checksum SHA-512; `mvn package` do `jtl-allure`; conversão JTL→Allure com gate **250 RPS / p90 2 s**; os dois JARs correm mesmo se um falhar; **Gerar relatório Allure** com `if: always()` para publicar relatório mesmo quando o gate reprova; artefatos com `always()`; Pages em `main`/`master`.
- **Agendamento**: **08:00**, **12:00** e **18:00** America/São Paulo (crons UTC `0 11 * * *`, `0 15 * * *`, `0 21 * * *`).
- **Jenkins**: `Jenkinsfile` — Docker, `catchError` nos testes, mesmos gates que o Docker.

## Rastreabilidade

Commits e PRs no GitHub; execuções em **Actions** e artefatos do workflow. Ver [AUDIT.md](AUDIT.md).

Opcional após clone: `git config core.hooksPath .githooks` (ver `.githooks/README`).

## Decisões de projeto

Detalhes em [DECISIONS.md](DECISIONS.md).

## Problemas frequentes

- **Dashboard não gera**: pasta `-e -o` deve estar vazia ou ser nova.
- **RPS abaixo do gate**: o host público pode não sustentar 250+ RPS; ver `jmeter.log`, erros HTTP e ajustar threads (com cuidado) ou aceitar falha do gate documentada.
- **Timeouts**: `connect_timeout_ms` / `response_timeout_ms` nos `.jmx`; rede até `www.blazedemo.com`.

## Relatório de execução (baseline)

Os números variam a cada execução. Para extrair o mesmo agregado do `jtl-allure` a partir de JTL gerados localmente ou baixados dos artefatos do CI:

```bash
java -cp jtl-allure/target/jtl-allure-1.0.0.jar com.blazedemo.perf.PrintBaselineApp
```

Use `--json` se precisar só de saída máquina-legível. Compare **RPS**, **p90** e **error %** com o critério acima; o host compartilhado pode falhar o SLO em horários de maior carga.

## Próximos passos

Afinar threads e ramp-up por ambiente; opcionalmente medir p90 por transação para isolar gargalos.
