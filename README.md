# BlazeDemo - Engenharia de Performance

Este repositório foi montado para testar, de ponta a ponta, o fluxo de compra de passagem do [BlazeDemo](https://www.blazedemo.com) com foco em uso real de engenharia (execução local, Docker e CI).

Repositório alvo: [alexxandrelopesqa/blazedemo-perfomance](https://github.com/alexxandrelopesqa/blazedemo-perfomance.git)

## Objetivo

- Sustentar **250 requisições por segundo (RPS)** no cenário principal.
- Manter **p90 menor que 2 segundos**.
- Garantir o sucesso funcional da jornada de compra (até a confirmação).

## O que tem aqui

```text
.
├── Jenkinsfile
├── docker-compose.yml
├── .github/
│   └── workflows/
│       └── ci.yml
├── docker/
│   ├── Dockerfile
│   ├── entrypoint.sh
│   ├── run_all.sh
│   └── user.properties
├── results/
│   └── .gitkeep
├── scripts/
│   ├── jtl_to_allure.py
│   ├── load_test.jmx
│   ├── passengers.csv
│   └── peak_test.jmx
├── .gitignore
└── README.md
```

## Cenário coberto nos scripts

Fluxo funcional completo:

1. `GET /` (home)
2. `POST /reserve.php` (origem e destino)
3. Extração dinâmica de `flight`, `price` e `airline`
4. `POST /purchase.php` (seleção do voo)
5. `POST /confirmation.php` (compra confirmada com dados do CSV)

Asserções usadas:

- Status HTTP `200` em todas as etapas.
- Texto final `Thank you for your purchase today!`.
- Log detalhado de falha via Groovy (`JSR223 PostProcessor`) para acelerar diagnóstico.

## Planos de teste

### `scripts/load_test.jmx`
- Alvo de throughput: `15000` amostras/minuto (~250 RPS)
- `350` threads
- Ramp-up de `120s`
- Duração de `600s`

### `scripts/peak_test.jmx`
- Alvo de throughput: `21000` amostras/minuto (~350 RPS)
- `500` threads
- Ramp-up de `30s`
- Duração de `240s`

## Como rodar localmente

Pré-requisitos:

- Java 17+
- JMeter 5.6.3
- Python 3.10+ (para conversão JTL -> Allure)
- Allure CLI (opcional, para relatório local)

### Executar carga

```bash
jmeter -n -t scripts/load_test.jmx -l results/load.jtl -e -o results/dashboard-load
```

### Executar pico

```bash
jmeter -n -t scripts/peak_test.jmx -l results/peak.jtl -e -o results/dashboard-peak
```

### Gerar dados para Allure

```bash
python scripts/jtl_to_allure.py results/load.jtl allure-results "Load Test 250 RPS"
python scripts/jtl_to_allure.py results/peak.jtl allure-results "Peak Test 350 RPS"
```

### Gerar relatório Allure

```bash
allure generate allure-results --clean -o results/allure-report
```

## Como rodar via Docker

Build da imagem:

```bash
docker build -f docker/Dockerfile -t my-jmeter-test .
```

Execução padrão solicitada:

```bash
docker run --rm -v "$(pwd)/results:/workspace/results" my-jmeter-test -n -t scripts/load_test.jmx -l results/res.jtl -e -o results/dashboard
```

Execução de pico:

```bash
docker run --rm -v "$(pwd)/results:/workspace/results" my-jmeter-test -n -t scripts/peak_test.jmx -l results/peak.jtl -e -o results/peak-dashboard
```

## Execucao local sem dependencias externas

Se quiser rodar localmente sem instalar Java, JMeter, Python ou Allure na sua maquina, use apenas Docker/Docker Compose.

Prerequisito unico:

- Docker Desktop (com Compose)

### Subir e rodar so o load test

```bash
docker compose build
docker compose run --rm perf-load
```

### Rodar so o peak test

```bash
docker compose run --rm perf-peak
```

### Rodar fluxo completo (load + peak + Allure)

```bash
docker compose run --rm perf-all
```

Artefatos gerados:

- `results/load/dashboard/index.html`
- `results/peak/dashboard/index.html`
- `results/allure-report/index.html`

Observacao: a execucao ainda depende de internet para acessar `www.blazedemo.com`.

## Pipeline CI (GitHub Actions)

Arquivo: `.github/workflows/ci.yml`

O pipeline faz:

1. Setup de Java, Python e Node.
2. Download do JMeter.
3. Execução headless de `load_test.jmx` e `peak_test.jmx`.
4. Geração dos dashboards HTML do JMeter.
5. Conversão de JTL para resultados Allure.
6. Geração de relatório Allure com reaproveitamento de histórico.
7. Upload de artefatos.
8. Montagem de um site estático com links para os relatórios.
9. Deploy automático no GitHub Pages (push em `main`/`master`).

Hardening aplicado:

- Job de teste com permissão mínima: `contents: read`.
- Job de deploy com permissões específicas: `pages: write` e `id-token: write`.
- Publicação de artefatos com retenção de 14 dias.
- Download do JMeter com validação de checksum SHA-512 no CI.
- Instalação do Allure CLI com `--ignore-scripts` para reduzir risco de supply chain.

## Jenkins

O projeto inclui `Jenkinsfile` declarativo para rodar no Jenkins usando Docker no agente.

Fluxo do pipeline:

1. Build da imagem (`docker/Dockerfile`)
2. Execucao do load test
3. Execucao do peak test
4. Conversao para Allure e geracao do relatorio
5. Archive dos artefatos (`results/**/*` e `allure-results/**/*`)

Observacao de segurança:

- Os stages de execução pesada estão limitados a `main` e `master` para reduzir risco com branches não confiáveis.

## Publicação no GitHub Pages

O workflow já está preparado para publicar automaticamente no Pages.

### Como habilitar no repositório

1. Acesse **Settings** do repositório.
2. Abra **Pages**.
3. Em **Build and deployment**, selecione **Source: GitHub Actions**.
4. Faça push para `main` (ou execute manualmente via `workflow_dispatch`).

Após o primeiro deploy, a URL fica no formato:

- `https://<seu-usuario>.github.io/<nome-do-repo>/`

No index publicado no Pages você terá links para:

- Dashboard JMeter de carga
- Dashboard JMeter de pico
- Relatório Allure

## Como avaliar se a meta foi atingida

No dashboard do JMeter, olhe principalmente o **Aggregate Report** do `load_test`:

- `Throughput` >= **250 req/s**
- `90% Line` < **2000 ms**
- `Error %` = **0**

No Allure, cada execução também é marcada como `failed` caso:

- haja falhas funcionais (`success=false`),
- o RPS fique abaixo de 250,
- ou o p90 fique maior/igual a 2000 ms.

## Troubleshooting prático

### 1) Ajustar heap do JMeter

No Docker:

```bash
docker run --rm -e JMETER_HEAP="-Xms2g -Xmx4g -XX:MaxMetaspaceSize=512m" -v "$(pwd)/results:/workspace/results" my-jmeter-test -n -t scripts/load_test.jmx -l results/res.jtl -e -o results/dashboard
```

No arquivo `docker/user.properties`:

```properties
heap=-Xms2g -Xmx4g -XX:MaxMetaspaceSize=512m
```

### 2) Throughput não chega na meta

- Aumente `num_threads` gradualmente.
- Ajuste o `ramp_time` para reduzir burst de abertura.
- Monitore CPU/RAM do gerador de carga.
- Se necessário, rode em runner maior (mais CPU/memória).

### 3) Assertion falhando

Conferir:

- `results/load/jmeter.log`
- `results/peak/jmeter.log`

Os logs já saem com contexto (sampler, response code, URL e trecho da resposta) para facilitar análise.
