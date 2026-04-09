# Rastreabilidade (BlazeDemo performance)

Comandos e atalhos para rastrear **commits**, **PRs** e **execuções de CI**.

## Git (clone local)

- Últimos commits: `git log --oneline -20`
- Detalhe de um commit: `git show <SHA>`
- Quem alterou uma linha: `git blame -w <ficheiro>`

## GitHub

- **Actions:** histórico do workflow em `.github/workflows/ci.yml` (push, PR, manual e agendamento).
- **Pull requests:** diff agregado, comentários e checks.
- **Artefatos:** nome típico `blazedemo-performance-reports`; retenção definida no workflow.

Contas pessoais: sem audit log de organização. Organizações: *Organization settings → Audit log*.
