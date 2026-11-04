# Assay output — `.assay/`

This tree is real scan output, readable and actionable with zero Fonebrew installed
(docs/ratified/ASSAY_REPO_CONTRACT_V1.md, INT-002). `assay-index.v1.json` is this
branch's current run manifest; `runs/20261104T023917Z-17e51fd/` holds that run's SARIF findings and
proving tests; `tests/proving/` holds the generated proving-test sources referenced
from `runs/20261104T023917Z-17e51fd/proving-tests.v1.json`.

Fonebrew Core only reads this tree after a user explicitly selects this repo+branch
and taps "Check branch" (INT-020) — nothing here is polled or auto-imported.
