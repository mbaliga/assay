# Assay — working rules

Gates: `LANG=C.UTF-8 gradle check --no-daemon` (JVM engine, all acceptance harnesses);
`gradle -p android-console :app:assembleDebug :app:lintDebug --no-daemon` (console).

1. Only the four scanners (Gitleaks, Semgrep, OSV, MobSF) may create a `Finding`; AI never does.
2. Proof is exit codes only; `ProofGate` is the sole arbiter of `proof_passed`.
3. Approve/reject requires a `human:` actor; the console resolves it from the GitHub login.
4. The Kotlin core never executes scanned-repo build scripts in-process; isolated job only.
5. No telemetry. Network only for pinned tool downloads, user git/GitHub, ASOM or a configured cloud model.
6. Linked libraries are permissive-only (Apache/MIT/BSD/ISC/CC0/Unlicense).
7. Colour never carries state alone: shape + label + luminance (Hyle tokens).
8. Anti-overclaim ladder: code-complete | verified | ready-for-owner-review. Never say "done" or "works on device".
9. Environment honesty: stub loudly with typed errors; never fake success. Device behaviour is NEEDS-DEVICE-VALIDATION.
10. Two contracts: external `.assay/` ratified shape vs internal bus 1.1.0. Never merge them.
11. Don't rename app/package/branches; don't touch LICENSE; don't add the Hyle submodule.

Full spec: BUILD-SPEC.md. Status ledger: docs/STATUS.md.
