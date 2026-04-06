# BlazeDemo - Teste de Performance (JMeter)

Repositório técnico para avaliar o fluxo completo de compra de passagem no [BlazeDemo](https://www.blazedemo.com) usando apenas JMeter.

Repositório: [alexxandrelopesqa/blazedemo-perfomance](https://github.com/alexxandrelopesqa/blazedemo-perfomance.git)

## Visão geral do desafio

Critério de aceitação pedido:

- Sustentar `250 RPS`
- Manter `p90 < 2s`

Cenário funcional obrigatório (implementado):

1. `GET /`
2. `POST /reserve.php` (origem/destino)
3. seleção de voo com extração dinâmica de `flight`, `price`, `airline`
4. `POST /purchase.php`
5. `POST /confirmation.php`

Asserções funcionais:

- HTTP 200 em todas as etapas
- resposta final contendo `Thank you for your purchase today!`

## Estrutura do projeto

```text
.
├── Jenkinsfile
├── docker-compose.yml
├── .github/workflows/ci.yml
├── docker/
│   ├── Dockerfile
│   ├── entrypoint.sh
│   ├── run_all.sh
│   └── user.properties
├── scripts/
│   ├── load_test.jmx
│   ├── peak_test.jmx
│   ├── passengers.csv
│   └── jtl_to_allure.py
├── results/
│   └── .gitkeep
├── DECISIONS.md
├── RUNBOOK.md
└── README.md
```

## Arquitetura do teste e perfil de carga

### 1) Load test (`scripts/load_test.jmx`)

Objetivo: validar estabilidade no patamar alvo.

- Throughput alvo: `15000/min` (~250 RPS)
- `350` threads
- ramp-up `120s`
- duração `600s`

### 2) Spike test (`scripts/peak_test.jmx`)

Objetivo: observar degradação e recuperação sob aumento brusco.

- Throughput alvo: `21000/min` (~350 RPS)
- `500` threads
- ramp-up `30s`
- duração `240s`

## Pré-requisitos

Você pode executar de duas formas:

### Opção A - Local clássico

- Java 17+
- JMeter 5.6.3
- Python 3.10+ (para consolidar resumo Allure)
- Allure CLI (opcional)

### Opção B - Sem instalar Java/JMeter/Python/Allure

- Docker Desktop + Docker Compose

## Execução local passo a passo

### Execução com Docker Compose (recomendado)

```bash
docker compose build
docker compose run --rm perf-load
docker compose run --rm perf-peak
docker compose run --rm perf-all
```

### Execução local com JMeter CLI

```bash
jmeter -n -t scripts/load_test.jmx -l results/load/load.jtl -e -o results/load/dashboard
jmeter -n -t scripts/peak_test.jmx -l results/peak/peak.jtl -e -o results/peak/dashboard
python scripts/jtl_to_allure.py results/load/load.jtl allure-results "Load Test 250 RPS"
python scripts/jtl_to_allure.py results/peak/peak.jtl allure-results "Peak Test 350 RPS"
allure generate allure-results --clean -o results/allure-report
```

## CI/CD e publicação

### GitHub Actions

Workflow: `.github/workflows/ci.yml`

O pipeline:

1. prepara Java/Python/Node
2. baixa JMeter (com validação SHA-512)
3. roda load e spike em modo headless
4. gera dashboards JMeter e Allure
5. publica artifacts
6. publica página no GitHub Pages (main/master)

### Jenkins

Pipeline declarativa em `Jenkinsfile` com execução via Docker.

## Onde encontrar artefatos e evidências

- `results/load/dashboard/index.html`
- `results/peak/dashboard/index.html`
- `results/allure-report/index.html`
- `allure-results/load_test_250_rps-summary.json`
- `allure-results/peak_test_350_rps-summary.json`

## Conclusão objetiva (baseline atual)

### Resultado final

**Não atende** ao critério de aceitação nesta rodada.

### O que foi medido

#### Load test (principal)

- throughput: `292.73 RPS` (**acima de 250**)
- p90: `6890 ms` (**acima de 2s**)
- falhas: `6299 / 175650`
- latency média: `2165.61 ms`

#### Spike test

- throughput: `231.17 RPS`
- p90: `10376 ms`
- falhas: `8247 / 55511`
- latency média: `4100.34 ms`

### Decisão técnica

Mesmo com throughput razoável no load, a cauda de latência e a taxa de erro estão altas.  
Com base em métricas objetivas, este baseline reprova no critério `p90 < 2s`.

## Rodadas de tuning (registro rápido)

| Rodada | Mudança | RPS | p90 (ms) | Erro | Decisão |
|---|---|---:|---:|---:|---|
| baseline | perfil inicial load/spike | 292.73 (load) | 6890 (load) | 6299/175650 | reprova p90 e erro |
| spike baseline | pico com ramp-up curto | 231.17 (spike) | 10376 (spike) | 8247/55511 | degradação forte sob pico |

## Limitações e considerações importantes

- O alvo testado é um site público (`www.blazedemo.com`), então há variabilidade externa de rede/infra.
- Resultado local depende da máquina geradora de carga.
- Mesmo rodando em Docker, ainda existe dependência de internet para atingir o sistema alvo.

## Segurança e governança

- `.gitignore` preparado para não subir resultados temporários
- sem segredos/token no repositório
- CI com permissões mínimas por job
- validação de checksum no download do JMeter
- container de execução como usuário não-root

## Próximos passos sugeridos

- Rodar tuning iterativo (threads, ramp-up, pacing e timeouts) em 3 a 5 rodadas.
- Fixar ambiente de execução dedicado para reduzir ruído.
- Comparar p90 por transação (não só total) para identificar gargalos mais rápido.
