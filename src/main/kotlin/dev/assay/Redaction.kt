package dev.assay

object Redaction {
    private val patterns = listOf(
        Regex("(?i)(api[_-]?key|token|secret|password)\\s*[:=]\\s*['\"]?([A-Za-z0-9_\\-/.+=]{8,})"),
        Regex("AKIA[0-9A-Z]{16}"),
        Regex("gh[pousr]_[A-Za-z0-9]{20,}"),
    )

    fun sanitize(text: String): String = patterns.fold(text) { acc, pattern ->
        pattern.replace(acc) { match ->
            val label = match.groups[1]?.value ?: "credential"
            "$label=[REDACTED]"
        }
    }

    fun containsSecret(text: String): Boolean = patterns.any { it.containsMatchIn(text) }
}
