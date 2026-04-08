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

Java 17+, JMeter 5.6.3, Python 3.10+; Allure CLI opcional.

```bash
jmeter -n -t scripts/load_test.jmx -l results/load/load.jtl -e -o results/load/dashboard
jmeter -n -t scripts/peak_test.jmx -l results/peak/peak.jtl -e -o results/peak/dashboard
ACCEPTANCE_RPS=250 python scripts/jtl_to_allure.py results/load/load.jtl allure-results "Load 250 RPS"
ACCEPTANCE_RPS=350 python scripts/jtl_to_allure.py results/peak/peak.jtl allure-results "Peak 350 RPS"
allure generate allure-results --clean -o results/allure-report
```

O script `jtl_to_allure.py` aplica o mesmo critério do README (RPS mínimo + p90) por cenário: **250 RPS** no load e **350 RPS** no peak (`ACCEPTANCE_P90_MS` padrão `2000`). Se houver falhas funcionais no JTL ou SLO não atendido, o processo termina com **código 3** e o job de CI falha. Para só gerar relatório local sem falhar o comando, use `STRICT_ACCEPTANCE=0`.

Resumos JSON gerados pelo script (nomes derivados do parâmetro do teste):

- `allure-results/load_250_rps-summary.json`
- `allure-results/peak_350_rps-summary.json`

## Evidências

- dashboards JMeter acima
- `results/*/jmeter.log`
- Allure: ambiente, executor, categorias de falha, breakdown por sampler

## CI/CD

- **GitHub Actions**: `.github/workflows/ci.yml` — JMeter com checksum SHA-512; conversão JTL→Allure com falha se critério não for atingido; artefatos e Pages em `main`/`master`; upload com `always()` para preservar evidências mesmo com falha.
- **Jenkins**: `Jenkinsfile` — Docker, `catchError` nos estágios de teste, Allure só se existir JTL.

## Decisões de projeto

Detalhes em [DECISIONS.md](DECISIONS.md) (ferramenta, fluxo E2E, CSV, perfis, relatórios, Docker, endurecimento).

## Problemas frequentes

- **Dashboard não gera**: pasta `-e -o` deve estar vazia ou ser nova.
- **RPS baixo**: CPU/RAM do gerador; reduzir threads ou aumentar ramp-up.
- **Timeouts**: `connect_timeout_ms` / `response_timeout_ms` nos `.jmx`; rede até `www.blazedemo.com`.

## Baseline registrado

Não atende `p90 < 2s` na rodada documentada.

**Load:** throughput `292.73 RPS`, p90 `6890 ms`, falhas `6299 / 175650`, latência média `2165.61 ms`.

**Peak:** throughput `231.17 RPS`, p90 `10376 ms`, falhas `8247 / 55511`, latência média `4100.34 ms`.

## Próximos passos

Ajustar threads, ramp-up, pacing e timeouts em iterações; ambiente de execução fixo; p90 por transação para achar gargalo.
