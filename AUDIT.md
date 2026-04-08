# Rastreabilidade (BlazeDemo performance)

Notas para rever **commits**, **PRs** e **execuções de CI** neste repositório.

## Git (clone local)

- Últimos commits: `git log --oneline -20`
- Detalhe de um commit: `git show <SHA>`
- Quem alterou uma linha: `git blame -w <ficheiro>`

## GitHub

- **Actions:** histórico do workflow em `.github/workflows/ci.yml` (push, PR, manual e agendamento).
- **Pull requests:** diff agregado, comentários e checks.
- **Artefatos:** nome típico `blazedemo-performance-reports`; retenção definida no workflow.

Em contas pessoais não existe audit log organizacional; em organizações com esse recurso, ver *Organization settings → Audit log*.
