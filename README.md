# BlazeDemo — teste de performance (JMeter)

Teste de carga no [BlazeDemo](https://www.blazedemo.com): fluxo HTTP de compra ponta a ponta, relatório HTML do JMeter e consolidação em Allure.

Repo: [alexxandrelopesqa/blazedemo-perfomance](https://github.com/alexxandrelopesqa/blazedemo-perfomance.git)

## Critério de aceite

- `250 RPS` sustentados
- `p90 < 2s`

Checagem rápida no dashboard de carga (`Aggregate Report`):

- Throughput ≥ `250 req/s`
- `90% Line` < `2000 ms`
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
| `scripts/load_test.jmx` | carga estável | `15000/min` (~250 RPS) | 350 | 120s | 600s |
| `scripts/peak_test.jmx` | pico | `21000/min` (~350 RPS) | 500 | 30s | 240s |

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
ACCEPTANCE_RPS=250 java -jar jtl-allure/target/jtl-allure-1.0.0.jar results/load/load.jtl allure-results "Load 250 RPS"
ACCEPTANCE_RPS=350 java -jar jtl-allure/target/jtl-allure-1.0.0.jar results/peak/peak.jtl allure-results "Peak 350 RPS"
allure generate allure-results --clean -o results/allure-report
```

O módulo `jtl-allure` aplica o mesmo critério do README (RPS mínimo + p90) por cenário: **250 RPS** no load e **350 RPS** no peak (`ACCEPTANCE_P90_MS` padrão `2000`). Se houver falhas funcionais no JTL ou SLO não atendido, o processo termina com **código 3** e o job de CI falha. Para só gerar relatório local sem falhar o comando, use `STRICT_ACCEPTANCE=0`.

Resumos JSON gerados (nomes derivados do parâmetro do teste):

- `allure-results/load_250_rps-summary.json`
- `allure-results/peak_350_rps-summary.json`

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
