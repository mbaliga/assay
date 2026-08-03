# Assay v1 threat model

Trust boundaries: audited repository, scanner processes, AI adapter, runner host, SSH transport, GitHub credential, and repo bus consumers.

Assay v1 assumes every audited repository is malicious. Scanner and build execution must occur in an ephemeral unprivileged outer sandbox with no host SSH agent, Docker socket, home directory, cloud credentials, or network by default. The deterministic core never executes repository code. A production runner must inject a scoped push credential only after artifact validation.

The bus is untrusted input to every reader. Readers reject traversal, absolute paths, symlinks, unsupported schema versions, missing manifests, digest mismatch, stale source commits, and failed runs. `Unavailable` and `Invalid` are never represented as an empty finding set.

AI is downstream of immutable deterministic findings and cannot construct findings. Proposed patches remain inert until proof passes and a human approves a dedicated fix branch. Nothing auto-merges.

Secret-bearing source lines, deleted patch lines, scanner output, logs, and model prompts are sensitive. Publication requires full redaction followed by a second scan of the staged bus.
