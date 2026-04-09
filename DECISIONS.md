# Decisoes tecnicas

Este arquivo registra escolhas do projeto e o motivo de cada uma.

## 1) Ferramenta de carga

- Escolha: **JMeter** (`.jmx`)
- Motivo: requisito do desafio e facilidade de execução headless em CI.

## 2) Fluxo funcional

- Escolha: cobrir fluxo completo de compra (`/`, `/reserve.php`, `/purchase.php`, `/confirmation.php`)
- Motivo: evitar falso positivo de performance em endpoint isolado sem validar negócio.

## 3) Parametrizacao de dados

- Escolha: `CSV Data Set Config` com `scripts/passengers.csv`
- Motivo: reduzir repetição de payload e simular usuários distintos.

## 4) Perfil de carga

- Load test: `15000/min` (~250 RPS no agregado do grupo), `400` threads, ramp-up `120s`, duração `300s`
- Peak test: `21000/min` (~350 RPS), `500` threads, ramp-up `45s`, duração `180s`
- Motivo: alinhar ao critério do desafio (**≥250 RPS** e pico acima disso). O site público pode não cumprir p90 &lt; 2s ou RPS medido em todas as execuções; o gate no `jtl-allure` documenta pass/fail. `ACCEPTANCE_MAX_ERROR_PCT` no CI (1%) limita falhas intermitentes sem mascarar taxas altas de erro.

## 5) Relatorios

- Escolha: Dashboard HTML do JMeter + Allure
- Motivo: dashboard para leitura operacional e Allure para trilha de histórico.

## 6) Execucao local sem dependencias

- Escolha: `docker-compose.yml` com serviços dedicados (`perf-load`, `perf-peak`, `perf-all`)
- Motivo: rodar sem instalar Java/JMeter na máquina host (imagem inclui JMeter, Allure CLI e o JAR `jtl-allure`).

## 7) Hardening aplicado

- Validação SHA-512 no download do JMeter
- Allure CLI instalado com `--ignore-scripts`
- Container rodando com usuário não-root
- Jenkins com estágios pesados limitados a `main/master`
- Motivo: reduzir risco de supply chain e execução não confiável.

## 8) Estado atual do critério

- Gate do `jtl-allure` (CI/Docker): RPS mínimo **250**, p90 &lt; **2000** ms, `ACCEPTANCE_MAX_ERROR_PCT` **1.0** no workflow para erros esporádicos do host.
- O cumprimento real do SLO depende da capacidade do BlazeDemo no momento do teste; relatórios e artefatos permitem auditar cada rodada.
