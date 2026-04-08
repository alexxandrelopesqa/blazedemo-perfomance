# Auditoria de commits e pull requests (GitHub)

Guia prático do que usar para rever **histórico Git**, **PRs** e **eventos** no GitHub. Útil para conformidade, revisão de código ou investigação (ex.: autoria, rewrites, integridade).

## 1) Commits — auditoria local (`git`)

Requer clone do repositório.

| Objetivo | Comando / ação |
|----------|----------------|
| Lista linear com datas e autores | `git log --oneline --decorate -20` |
| Mensagem completa + autor/committer | `git log -1 --format=fuller` ou `git show --no-patch --format=fuller <SHA>` |
| Ver ficheiros tocados num commit | `git show --stat <SHA>` |
| Diff completo | `git show <SHA>` |
| Procurar texto nas mensagens | `git log --all --grep='texto' -i` |
| Quem alterou uma linha (ficheiro) | `git blame -w <ficheiro>` |
| Assinaturas GPG (se existirem) | `git log --show-signature -5` |
| Detectar reescrita de histórico | Comparar SHAs antigos (notas, issues) com `git log main`; `git reflog` só no clone local |

**Limitações:** após `git push --force`, os SHAs antigos deixam de existir no remoto; quem só clona depois não vê o histórico anterior. Issues/PRs podem ainda referenciar SHAs “mortos”.

---

## 2) Pull requests — interface GitHub (repositório público/privado)

No PR (`https://github.com/<org>/<repo>/pull/<n>`):

| Área | O que verificar |
|------|-----------------|
| **Commits** | Lista de SHAs incluídos; clicar no SHA abre o commit no `main`/branch base |
| **Files changed** | Diff agregado; comentários em linha |
| **Checks** | Workflows (ex.: Actions), estado required checks se branch protection existir |
| **Conversation** | Aprovações, comentários de review, histórico de pushes |
| **Merge** | Tipo (merge commit, squash, rebase) — altera como os commits aparecem no `main` |

**Compare entre branches:**  
`https://github.com/<org>/<repo>/compare/<base>...<head>` — útil para ver commits e diff antes de abrir PR.

---

## 3) GitHub — API REST (automação ou integrações)

Autenticação: Personal Access Token (classic ou fine-grained) ou GitHub App.

Exemplos (substituir `OWNER`, `REPO`):

- Lista de commits de um branch:  
  `GET /repos/{OWNER}/{REPO}/commits?sha=main`
- Detalhe de um commit:  
  `GET /repos/{OWNER}/{REPO}/commits/{ref}`
- Lista de PRs:  
  `GET /repos/{OWNER}/{REPO}/pulls`
- Detalhe de um PR (inclui `merge_commit_sha` após merge):  
  `GET /repos/{OWNER}/{REPO}/pulls/{number}`

Documentação: [REST API - Repositories](https://docs.github.com/en/rest/repos/repos), [Commits](https://docs.github.com/en/rest/commits/commits), [Pulls](https://docs.github.com/en/rest/pulls/pulls).

---

## 4) Organizações — Audit log (GitHub)

- **Disponível** para organizações em planos que incluem **Audit log** (equipa/empresa).
- Localização (UI): **Organization settings → Audit log** (eventos de membros, repos, políticas, etc.).
- **Não substitui** o histórico Git: regista **ações na plataforma** (quem fez push, alterou branch protection, etc.), não o conteúdo linha a linha dos commits.

Para repositório individual (sem org), não há audit log organizacional; usam-se PRs, Actions e o histórico Git público.

---

## 5) O que este repositório expõe

- **CI:** `.github/workflows/ci.yml` — cada push/PR a `main`/`master` gera execução; histórico em **Actions** do repositório.
- **Branch protection:** se não estiver ativada em **Settings → Branches**, merges podem não exigir review nem checks verdes.

Recomendação para auditoria contínua: ativar **branch protection** em `main` (reviews, status checks obrigatórios) se a política do projeto exigir.

---

## 6) Checklist rápido de auditoria de um PR

1. Abrir o PR → separador **Commits** (quantidade e mensagens).
2. **Files changed** → alterações alinhadas ao objetivo do PR.
3. **Checks** → workflows concluídos; falhas documentadas ou justificadas.
4. Opcional: `git fetch` + `git log origin/main` local para cruzar SHAs após merge.
5. Se houver suspeita de histórico reescrito: procurar referências antigas em issues/comentários e comparar com `git log`.

---

## 7) Privacidade e retenção

- Repositório **público**: commits e PRs são visíveis a todos.
- **Artefactos** e **logs** de Actions têm retenção limitada (ex.: dias configuráveis por workflow); não são arquivo legal permanente por si só.

Para este projeto, relatórios de performance ficam também em **Artifacts** e opcionalmente **GitHub Pages** conforme o workflow.
