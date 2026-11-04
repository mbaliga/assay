# Status ledger

Ladder: `code-complete` (compiles, tests green) < `verified` (ran end-to-end on CI with evidence) < `ready-for-owner-review` (verified + only a device/owner step remains).

| Date | WP | Ladder | Evidence |
|---|---|---|---|
| 2026-11-03 | WP0 One trunk | code-complete | `gradle check --no-daemon` exit 0 (all acceptance harnesses); console `assembleDebug lintDebug` exit 0 on AGP 8.9.1/Kotlin 2.1.20/minSdk 31 |
| 2026-11-04 | WP1 `assay audit` | verified | `gradle check --no-daemon` exit 0 incl. `auditAcceptanceTest` (8 assertions); `live-cli-contracts` green on 3fc6b6b: https://github.com/mbaliga/assay/actions/runs/41757377387 (real gitleaks/semgrep/osv via `assay audit`, `.assay/` uploaded as `assay-audit-output`) |
| 2026-11-04 | WP2 hosted runner | verified | `sample-e2e` green on 17e51fd: https://github.com/mbaliga/assay/actions/runs/41761612706 (reusable audit on fixtures/sample-repo with gitleaks+semgrep+osv+mobsf; pushed `assay/sample-output`; `verify` asserted COMPLETE, real assayCommit, 70 findings from Gitleaks/MobSF/OSV-Scanner/Semgrep, exact Semgrep rule id, internal bus `latestComplete` == run id). Needed three CI fixes: Docker `--mount` syntax, MobSF read-only/JADX provisioning, delete_scan retry |
| 2026-11-04 | WP3 console control plane, part 1 | code-complete | CI green on 238d777 (console `build`: lint, unit tests, screenshot verify: https://github.com/mbaliga/assay/actions/runs/41767356565). `/code-review` found 10 issues; 8 fixed (JSON-shape crash, workflow-proposal dead end, double-tap and state races, token in saved state and Keystore failures, success shown without a matching index, unpinned workflow ref, error text, branch names). Not fixed: the exported-component/signer noise in the pre-existing `LocalApkAudit.kt` (kept verbatim by spec); per-workflow rather than per-branch run listing (documented). After fixes: 39 unit tests, 20 screenshots, gate exit 0 locally and CI green on 6270718 (console `build`: https://github.com/mbaliga/assay/actions/runs/41768847013). NEEDS-DEVICE-VALIDATION: install on RedMagic, PAT entry and Keystore round-trip, one real dispatch |
| 2026-11-03 | WP4-WP7 | NOT STARTED | deferred |

## Superseded branches (safe to delete, owner)
`apk-testbuild`, `agent/*`, `claude/github-storage-cleanup-*`, `claude/repo-status-overview-2c0rgm` (only `58ede9d` cherry-picked).

## OWNER-ACTION-NEEDED
- 2.1 GitHub PAT, 2.4 cloud model key, 2.5 release keystore: not supplied; console runs offline/debug-signed until provided.
- 2.3 Licence: kept Apache-2.0; `claude/asystemofcells-redesign-s6yv4c` (PolyForm) left unmerged.
- Branch note: work lives on `ccr-4d4cf4bc-rvt28h` (session-mandated) in place of `trunk`.
