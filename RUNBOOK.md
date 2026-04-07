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

Checar:

- `Throughput >= 250 req/s`
- `90% Line < 2000 ms`
- `Error %` próximo de `0`

Se qualquer item falhar, considerar baseline reprovado no critério.

## 3) Comandos de apoio (CLI)

```bash
python scripts/jtl_to_allure.py results/load/load.jtl allure-results "Concluir compra carga sustentada 250 RPS"
python scripts/jtl_to_allure.py results/peak/peak.jtl allure-results "Concluir compra pico abrupto 350 RPS"
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

## 5) Coleta para anexar em avaliação técnica

Salvar/compartilhar:

- `results/load/dashboard/*`
- `results/peak/dashboard/*`
- `allure-results/*-summary.json`
- trecho de `results/load/jmeter.log` e `results/peak/jmeter.log` com erros principais
