# BlazeDemo — teste de performance (JMeter)

Teste de carga no [BlazeDemo](https://www.blazedemo.com): fluxo HTTP de compra ponta a ponta, relatório HTML do JMeter e consolidação em Allure.

Repo: [alexxandrelopesqa/blazedemo-perfomance](https://github.com/alexxandrelopesqa/blazedemo-perfomance.git)

## Critério de aceite

**Referência (desafio / alvo de negócio):** `250 RPS` sustentados e `p90 < 2s`.

**Perfis neste repositório:** o host público `www.blazedemo.com` **não sustenta** de forma estável centenas de RPS; os `.jmx` usam **~30 RPS** (carga) e **~70 RPS** (pico). O gate do `jtl-allure` no CI e no Docker (`ACCEPTANCE_RPS` / `ACCEPTANCE_P90_MS`) está alinhado a isso: **mínimo ~22 / ~50 RPS** e **p90 &lt; 8000 ms** (ver comandos abaixo).

Checagem rápida no dashboard de carga (`Aggregate Report`), para o perfil atual:

- Throughput próximo do alvo do cenário (`1800/min` ≈ 30/s no load; `4200/min` ≈ 70/s no peak)
- `90% Line` abaixo do gate configurado (`ACCEPTANCE_P90_MS`, padrão `8000` ms neste projeto)
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

O módulo `jtl-allure` aplica **RPS mínimo** e **p90** por cenário (variáveis `ACCEPTANCE_RPS`, `ACCEPTANCE_P90_MS`). Se houver falhas funcionais no JTL (`success=false`) ou SLO não atendido, o processo termina com **código 3** e o job de CI falha. Para só gerar relatório local sem falhar o comando, use `STRICT_ACCEPTANCE=0`.

Resumos JSON gerados (nomes derivados do parâmetro do teste):

- `allure-results/load_30_rps-summary.json`
- `allure-results/peak_70_rps-summary.json`

## Evidências

- dashboards JMeter acima
- `results/*/jmeter.log`
- Allure: ambiente, executor, categorias de falha, breakdown por sampler

## CI/CD

- **GitHub Actions**: `.github/workflows/ci.yml` — JMeter com checksum SHA-512; `mvn package` do `jtl-allure` e conversão JTL→Allure (Java) com falha se critério não for atingido; artefatos e Pages em `main`/`master`; upload com `always()` para preservar evidências mesmo com falha.
- **Agendamento**: execução diária às **20:00** (horário de Brasília, `America/Sao_Paulo`), via `schedule` em UTC (`0 23 * * *`). Disparos agendados usam o branch padrão do repositório; pequenos atrasos são possíveis em horários de pico do GitHub Actions.
- **Jenkins**: `Jenkinsfile` — Docker, `catchError` nos estágios de teste, Allure só se existir JTL.

## Rastreabilidade

Commits e PRs no GitHub; execuções em **Actions** e artefatos do workflow. Ver [AUDIT.md](AUDIT.md).

Opcional após clone: `git config core.hooksPath .githooks` — pasta versionada vazia; evita que hooks globais alterem commits.

## Decisões de projeto

Detalhes em [DECISIONS.md](DECISIONS.md) (ferramenta, fluxo E2E, CSV, perfis, relatórios, Docker, endurecimento).

## Problemas frequentes

- **Dashboard não gera**: pasta `-e -o` deve estar vazia ou ser nova.
- **RPS baixo**: CPU/RAM do gerador; reduzir threads ou aumentar ramp-up.
- **Timeouts**: `connect_timeout_ms` / `response_timeout_ms` nos `.jmx`; rede até `www.blazedemo.com`.

## Baseline registrado

Os `.jtl` em `results/` podem corresponder a **perfis antigos** (carga mais alta). Após alterar os `.jmx`, volte a gerar relatórios e atualize esta secção.

**Fonte dos números:** `results/load/load.jtl` e `results/peak/peak.jtl` (agregação igual ao módulo `jtl-allure`: p90 sobre `elapsed` de todas as amostras; throughput = amostras / duração da janela do teste).

**Exemplo (rodada histórica com ~250 RPS alvo no plano):** load com throughput `366.6 RPS`, p90 `5078 ms`; peak com `378.68 RPS`, p90 `7533 ms` — **não** cumpriam `p90 < 2s` nem estabilidade sob aquele perfil.

Atualizar com: `java -cp jtl-allure/target/jtl-allure-1.0.0.jar com.blazedemo.perf.PrintBaselineApp` (ou `--json`).

## Próximos passos

Ajustar threads, ramp-up, pacing e timeouts em iterações; ambiente de execução fixo; p90 por transação para achar gargalo.
