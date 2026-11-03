# Status ledger

Ladder: `code-complete` (compiles, tests green) < `verified` (ran end-to-end on CI with evidence) < `ready-for-owner-review` (verified + only a device/owner step remains).

| Date | WP | Ladder | Evidence |
|---|---|---|---|
| 2026-11-03 | WP0 One trunk | in progress | see below |

## Superseded branches (safe to delete, owner)
`apk-testbuild`, `agent/*`, `claude/github-storage-cleanup-*`, `claude/repo-status-overview-2c0rgm` (only `58ede9d` cherry-picked).

## OWNER-ACTION-NEEDED
- 2.1 GitHub PAT, 2.4 cloud model key, 2.5 release keystore: not supplied; console runs offline/debug-signed until provided.
- 2.3 Licence: kept Apache-2.0; `claude/asystemofcells-redesign-s6yv4c` (PolyForm) left unmerged.
- Branch note: work lives on `ccr-4d4cf4bc-rvt28h` (session-mandated) in place of `trunk`.
