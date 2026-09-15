package dev.assay

object Redaction {
    private val assignment = Regex(
        "(?i)(api[_-]?key|access[_-]?token|auth[_-]?token|token|client[_-]?secret|secret|password|passwd)" +
            "\\s*[:=]\\s*['\"]?([A-Za-z0-9_\\-/.+=]{8,})",
    )
    private val standalone = listOf(
        Regex("AKIA[0-9A-Z]{16}"),
        Regex("ASIA[0-9A-Z]{16}"),
        Regex("gh[pousr]_[A-Za-z0-9]{20,}"),
        Regex("glpat-[A-Za-z0-9_-]{20,}"),
        Regex("sk-(?:proj-)?[A-Za-z0-9_-]{20,}"),
        Regex("xox[baprs]-[A-Za-z0-9-]{10,}"),
        Regex("ASSAY_TEST_SECRET_[A-Z0-9]{20}"),
        Regex("-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----[\\s\\S]*?-----END (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    )

    fun sanitize(text: String): String {
        var result = assignment.replace(text) { match ->
            val label = match.groups[1]?.value ?: "credential"
            "$label=[REDACTED]"
        }
        standalone.forEach { pattern -> result = pattern.replace(result, "[REDACTED]") }
        return result
    }

    fun containsSecret(text: String): Boolean =
        assignment.containsMatchIn(text) || standalone.any { it.containsMatchIn(text) }
}
