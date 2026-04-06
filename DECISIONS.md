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

- Load test: `15000/min`, `350` threads, ramp-up `120s`, duração `600s`
- Spike test: `21000/min`, `500` threads, ramp-up `30s`, duração `240s`
- Motivo: ter um teste de estabilidade e outro de estresse abrupto.

## 5) Relatorios

- Escolha: Dashboard HTML do JMeter + Allure
- Motivo: dashboard para leitura operacional e Allure para trilha de histórico.

## 6) Execucao local sem dependencias

- Escolha: `docker-compose.yml` com serviços dedicados (`perf-load`, `perf-peak`, `perf-all`)
- Motivo: reduzir fricção para quem vai avaliar o projeto em máquina limpa.

## 7) Hardening aplicado

- Validação SHA-512 no download do JMeter
- Allure CLI instalado com `--ignore-scripts`
- Container rodando com usuário não-root
- Jenkins com estágios pesados limitados a `main/master`
- Motivo: reduzir risco de supply chain e execução não confiável.

## 8) Estado atual do critério

- Resultado baseline: **não atende** `p90 < 2s` no load.
- Motivo técnico: latência de cauda e taxa de erro acima do alvo, apesar de throughput razoável.
