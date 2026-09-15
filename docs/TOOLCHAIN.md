# Deterministic scanner toolchain

Version set reviewed on 3 August 2026:

| Scanner | Pinned adapter version | Evidence format | Network policy |
|---|---:|---|---|
| Gitleaks | 8.30.1 | SARIF 2.1.0 | denied |
| Semgrep Community Edition | 1.164.0 | SARIF 2.1.0 | denied; local rules only |
| OSV-Scanner | 2.3.8 | SARIF 2.1.0 | offline by default |
| MobSF | 4.5.1 | JSON normalized by Assay | isolated container |

These are adapter compatibility pins, not floating dependencies. Deployment must supply `tool-lock.json` with the SHA-256 of the exact executable or immutable container image selected for that runner. `assay verify-tool-lock` requires all four entries. `assay run-scanner` may consume a single-scanner subset but still verifies the selected tool's version and digest before execution.

Semgrep configuration is mandatory and must be a pinned local file. Its digest is recorded in run metadata. OSV online operation requires the explicit `--online` CLI switch and should be limited to controlled contract verification; production scans use a preloaded offline database. CISA KEV is a separate pinned deterministic enrichment and is not claimed to be native OSV output.

MobSF is not invoked as a local CLI by this module. Its report is accepted only through the JSON adapter after the runner has independently attested the server/image version and immutable digest.
