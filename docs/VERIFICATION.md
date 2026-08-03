# Verification matrix

| Capability | Current evidence | Status |
|---|---|---|
| Pure-JVM core | local acceptance suite and GitHub `gradle check` | verified |
| Canonical SARIF, severity mapping, bus validation, proof model | deterministic fixtures | verified |
| Gitleaks/Semgrep/OSV command construction | unit acceptance tests | verified |
| Scanner version and executable-digest enforcement | fake-runner acceptance tests | verified |
| Gitleaks/Semgrep/OSV real CLI contract | dedicated GitHub Actions workflow | pending until the workflow is green on the current head |
| MobSF JSON adapter | representative deterministic fixtures | verified for fixtures only |
| MobSF container/API execution | actual pinned container required | unverified |
| Local atomic bus directory | acceptance tests | verified |
| Remote `assay/audit` orphan-branch publication | Git host and push-with-lease integration required | unverified |
| Dell homelab runner | Dell environment required | unverified |
| Android console | not implemented in this slice | not implemented |
| Fonebrew/Orrery readers | downstream repository changes required | not implemented |

No row may be promoted by prose. Promotion requires the named mechanical evidence.
