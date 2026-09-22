# Fonebrew execution boundary

Assay may ask Fonebrew to execute a workload, but execution is not proof.

`FonebrewExecution.kt` defines:
- the command/environment capability request Assay can hand to Fonebrew;
- the digest-bearing execution result Fonebrew can return;
- a preliminary gate that rejects mismatched requests and missing expected artifacts.

Assay remains the only authority that can:
- validate source identity and revision;
- replay deterministic scanners;
- evaluate proving tests;
- publish Assay evidence;
- change candidate proof state.

An exit code of zero from Fonebrew is never sufficient on its own.

This boundary allows local JVM/Gradle, browser/Selenium, remote runner and future isolated-runtime execution to be supplied by Fonebrew without moving Assay's trust boundary.