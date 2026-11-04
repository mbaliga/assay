# Assay hosted-runner workflows

GitHub only lets other repositories call a reusable workflow that lives under
`.github/workflows/`, so the real files are there:

| Workflow | Purpose |
|---|---|
| `.github/workflows/assay-audit.yml` | Reusable (`workflow_call`) full audit: scanners -> `.assay/` tree -> push to your output branch. |
| `.github/workflows/sample-e2e.yml` | Runs the audit against `fixtures/sample-repo/` and asserts the result. |
| `prove.yml`, `approve.yml` | Arrive with WP4 (proving-test execution, human approve/reject/apply). |

Add a caller to a repository with `assay init-actions --repo .` and read
[docs/RUNNER-ACTIONS.md](../docs/RUNNER-ACTIONS.md) for the isolation argument and its limits.
