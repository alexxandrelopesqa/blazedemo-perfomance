# BlazeDemo - Confiabilidade da Jornada de Compra

Repositório técnico para avaliar a jornada completa de compra de passagem no [BlazeDemo](https://www.blazedemo.com) com JMeter, dashboards HTML e consolidação no Allure.

Repositório: [alexxandrelopesqa/blazedemo-perfomance](https://github.com/alexxandrelopesqa/blazedemo-perfomance.git)

## Objetivo e critério de aceite

Critério solicitado:

- Sustentar `250 RPS`
- Manter `p90 < 2s`

Regra de aprovação rápida:

- `Throughput >= 250 req/s`
- `90% Line < 2000 ms`
- `Error %` próximo de `0`

Se qualquer item falhar, considerar baseline reprovado no critério.

## Jornada de negócio coberta

Fluxo ponta a ponta implementado:

1. `GET /`
2. `POST /reserve.php` (origem/destino)
3. seleção de voo com extração dinâmica de `flight`, `price`, `airline`
4. `POST /purchase.php`
5. `POST /confirmation.php`

Asserções funcionais:

- HTTP 200 em todas as etapas
- resposta final contendo `Thank you for your purchase today!`

## Arquitetura e perfil de carga

### Cenario 1 - compra sob carga sustentada (`scripts/load_test.jmx`)

- throughput alvo: `15000/min` (~250 RPS)
- `350` threads
- ramp-up `120s`
- duração `600s`
- objetivo: validar estabilidade no patamar alvo

### Cenario 2 - compra sob pico abrupto (`scripts/peak_test.jmx`)

- throughput alvo: `21000/min` (~350 RPS)
- `500` threads
- ramp-up `30s`
- duração `240s`
- objetivo: observar degradação e recuperação sob aumento brusco

## Como executar

### Opcao recomendada (Docker Compose)

Pré-requisito: Docker Desktop + Docker Compose.

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

### Opcao local com CLI (sem Docker)

Pré-requisitos:

- Java 17+
- JMeter 5.6.3
- Python 3.10+
- Allure CLI (opcional)

```bash
jmeter -n -t scripts/load_test.jmx -l results/load/load.jtl -e -o results/load/dashboard
jmeter -n -t scripts/peak_test.jmx -l results/peak/peak.jtl -e -o results/peak/dashboard
python scripts/jtl_to_allure.py results/load/load.jtl allure-results "Concluir compra carga sustentada 250 RPS"
python scripts/jtl_to_allure.py results/peak/peak.jtl allure-results "Concluir compra pico abrupto 350 RPS"
allure generate allure-results --clean -o results/allure-report
```

## Validacao e evidencias

Abrir:

- `results/load/dashboard/index.html` (Aggregate Report)
- `results/peak/dashboard/index.html`
- `results/allure-report/index.html`

Arquivos importantes para avaliação:

- `allure-results/concluir_compra_carga_sustentada_250_rps-summary.json`
- `allure-results/concluir_compra_pico_abrupto_350_rps-summary.json`
- trechos de `results/load/jmeter.log` e `results/peak/jmeter.log` com erros principais

No Allure, além do overview, o projeto publica:

- `Environment` (target, thresholds, runner, versões)
- `Executor` (contexto local/CI)
- `Categories` com classificação de falha (SLO, funcional, timeout/rede)
- casos detalhados por sampler/transação com anexos JSON de métricas e erros

## CI/CD e publicação

### GitHub Actions

Workflow: `.github/workflows/ci.yml`

Pipeline:

1. prepara Java/Python/Node
2. baixa JMeter com validação SHA-512
3. executa cenários em modo headless
4. gera dashboards JMeter e Allure
5. publica artifacts
6. publica página no GitHub Pages (`main/master`)

Robustez:

- etapas de publicação executam com `always()`
- deploy do Pages preserva a trilha de execução mesmo com degradação

### Jenkins

Pipeline declarativa em `Jenkinsfile` com execução via Docker.

Robustez:

- estágios de execução com `catchError` para manter evidências
- geração de Allure condicional à existência de JTL

## Decisoes tecnicas consolidadas

- Ferramenta de carga: JMeter (`.jmx`) por requisito do desafio e execução headless em CI
- Fluxo funcional: jornada completa (`/`, `/reserve.php`, `/purchase.php`, `/confirmation.php`) para evitar falso positivo em endpoint isolado
- Parametrização: `CSV Data Set Config` com `scripts/passengers.csv` para simular usuários distintos
- Relatórios: dashboard HTML do JMeter + Allure para visão operacional e histórico
- Execução local sem dependências: `docker-compose.yml` com `perf-load`, `perf-peak`, `perf-all`
- Hardening aplicado:
  - validação SHA-512 no download do JMeter
  - Allure CLI com `--ignore-scripts`
  - container com usuário não-root
  - Jenkins com estágios pesados limitados a `main/master`

## Troubleshooting rapido

### Dashboard nao gera

- Verificar se o diretório de saída já existe com conteúdo antigo
- JMeter exige pasta nova ou vazia para `-e -o`

### RPS baixo na maquina local

- Máquina geradora de carga pode estar saturada (CPU/RAM)
- Ajustar ramp-up e quantidade de threads antes de subir throughput

### Muitos timeouts

- Revisar `connect_timeout_ms` e `response_timeout_ms` no `.jmx`
- Validar estabilidade da rede para `www.blazedemo.com`

## Resultado atual (baseline)

Status: **não atende** ao critério nesta rodada.

### Cenario de carga sustentada

- throughput: `292.73 RPS` (acima de 250)
- p90: `6890 ms` (acima de 2s)
- falhas: `6299 / 175650`
- latency média: `2165.61 ms`

### Cenario de pico abrupto

- throughput: `231.17 RPS`
- p90: `10376 ms`
- falhas: `8247 / 55511`
- latency média: `4100.34 ms`

Conclusão: apesar de throughput razoável no load, a latência de cauda e a taxa de erro permanecem altas, reprovando `p90 < 2s`.

## Próximos passos

- Rodar tuning iterativo (threads, ramp-up, pacing e timeouts) em 3 a 5 rodadas
- Fixar ambiente de execução dedicado para reduzir ruído
- Comparar p90 por transação (não só total) para localizar gargalos mais rápido
