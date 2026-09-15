package dev.assay

object SeverityPolicy {
    const val VERSION = "sev-map-v1"

    fun map(finding: Finding): Severity {
        val generic = finding.securitySeverity?.let {
            when {
                it >= 9.0 -> Severity.SEV1
                it >= 7.0 -> Severity.SEV2
                it >= 4.0 -> Severity.SEV3
                it > 0.0 -> Severity.SEV4
                else -> Severity.SEV4
            }
        } ?: when (finding.level) {
            SarifLevel.ERROR -> Severity.SEV2
            SarifLevel.WARNING -> Severity.SEV3
            SarifLevel.NOTE, SarifLevel.NONE -> Severity.SEV4
        }

        val floor = when {
            finding.scanner == Scanner.GITLEAKS &&
                (finding.properties["assay.ruleClass"] == "committed-live-secret" ||
                    !finding.properties["commit"].isNullOrBlank()) -> Severity.SEV1
            finding.scanner == Scanner.OSV &&
                finding.properties["cisaKev"].equals("true", ignoreCase = true) -> Severity.SEV1
            finding.scanner == Scanner.MOBSF &&
                finding.properties["assay.ruleClass"] == "exported-component-no-permission" -> Severity.SEV2
            else -> null
        }
        return if (floor == null) generic else moreSevere(generic, floor)
    }

    private fun moreSevere(a: Severity, b: Severity): Severity = if (a.ordinal <= b.ordinal) a else b
}
