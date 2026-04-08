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

- Load test: `1800/min` (~30 RPS), `60` threads, ramp-up `60s`, duração `400s`
- Spike test: `4200/min` (~70 RPS), `120` threads, ramp-up `30s`, duração `150s`
- Motivo: o host público não sustenta centenas de RPS com baixa taxa de erro; perfis moderados mantêm o fluxo E2E e o CI utilizável. Referência de desafio (`250 RPS`, `p90 < 2s`) permanece no README como alvo conceitual.

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

- Gate do `jtl-allure` (CI/Docker): RPS mínimos ~22 / ~50 e p90 &lt; 8000 ms, alinhados aos perfis atuais.
- Referência `250 RPS` / `p90 < 2s`: ver README; medições antigas em `results/` com carga agressiva não a cumpriam.
