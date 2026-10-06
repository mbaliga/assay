#!/bin/sh
set -eu
umask 077

ASSAY_BIN=${ASSAY_BIN:-/opt/assay/bin/assay}
ASSAY_WORK_ROOT=${ASSAY_WORK_ROOT:-/var/lib/assay/work}
ASSAY_TOOL_LOCK=${ASSAY_TOOL_LOCK:-/opt/assay/tool-lock.json}
ASSAY_GITLEAKS=${ASSAY_GITLEAKS:-/opt/assay/tools/gitleaks}
ASSAY_SEMGREP=${ASSAY_SEMGREP:-/opt/assay/tools/semgrep}
ASSAY_OSV=${ASSAY_OSV:-/opt/assay/tools/osv-scanner}
ASSAY_GIT=${ASSAY_GIT:-/usr/bin/git}
ASSAY_CONTAINER_RUNTIME=${ASSAY_CONTAINER_RUNTIME:-/usr/bin/podman}
ASSAY_PREFLIGHT_REPORT=${ASSAY_PREFLIGHT_REPORT:-/var/lib/assay/status/runner-preflight.json}

exec "$ASSAY_BIN" runner-preflight \
  --work-root "$ASSAY_WORK_ROOT" \
  --tool-lock "$ASSAY_TOOL_LOCK" \
  --gitleaks "$ASSAY_GITLEAKS" \
  --semgrep "$ASSAY_SEMGREP" \
  --osv "$ASSAY_OSV" \
  --git "$ASSAY_GIT" \
  --container-runtime "$ASSAY_CONTAINER_RUNTIME" \
  --output "$ASSAY_PREFLIGHT_REPORT"
