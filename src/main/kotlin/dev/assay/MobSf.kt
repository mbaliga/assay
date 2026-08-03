package dev.assay

import java.nio.file.Path

object MobSfNormalizer {
    private val supportedSections = listOf(
        "manifest_analysis",
        "code_analysis",
        "binary_analysis",
        "certificate_analysis",
        "network_security",
        "file_analysis",
    )
    private val findingSeverities = setOf("critical", "high", "warning", "medium", "low", "info")

    fun decode(raw: String, sourceRoot: Path): List<Finding> {
        val root = Json.parse(raw).requireObject()
        val present = supportedSections.filter { root.value(it) != null }
        require(present.isNotEmpty()) { "MobSF report has no supported analysis sections" }
        val findings = mutableListOf<Finding>()
        present.forEach { section ->
            collect(
                value = root.required(section),
                section = section,
                keyPath = listOf(section),
                sourceRoot = sourceRoot,
                sink = findings,
            )
        }
        return Sarif.merge(findings)
    }

    private fun collect(
        value: JsonValue,
        section: String,
        keyPath: List<String>,
        sourceRoot: Path,
        sink: MutableList<Finding>,
    ) {
        when (value) {
            is JsonValue.Obj -> {
                decodeFinding(value, section, keyPath, sourceRoot)?.let { sink += it }
                value.values.toSortedMap().forEach { (key, child) ->
                    collect(child, section, keyPath + key, sourceRoot, sink)
                }
            }
            is JsonValue.Arr -> value.values.forEachIndexed { index, child ->
                collect(child, section, keyPath + index.toString(), sourceRoot, sink)
            }
            else -> Unit
        }
    }

    private fun decodeFinding(
        value: JsonValue.Obj,
        section: String,
        keyPath: List<String>,
        sourceRoot: Path,
    ): Finding? {
        val severityLabel = listOf("severity", "level", "status")
            .firstNotNullOfOrNull { value.stringOrNull(it) }
            ?.lowercase()
            ?: return null
        if (severityLabel !in findingSeverities) return null
        val message = listOf("description", "message", "title", "name")
            .firstNotNullOfOrNull { value.stringOrNull(it) }
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val ruleId = listOf("rule", "rule_id", "id", "test")
            .firstNotNullOfOrNull { value.stringOrNull(it) }
            ?.takeIf { it.isNotBlank() }
            ?: keyPath.lastOrNull { it.toIntOrNull() == null }
            ?: "mobsf-finding"
        val file = listOf("file", "path", "filename")
            .firstNotNullOfOrNull { value.stringOrNull(it) }
            ?.let { normalizePath(it, section, sourceRoot) }
            ?: firstFile(value, section, sourceRoot)
            ?: "mobsf/$section.json"
        val line = listOf("line", "line_number", "start_line")
            .firstNotNullOfOrNull { value.doubleOrNull(it) }
            ?.toInt()
            ?.coerceAtLeast(1)
            ?: 1
        val securitySeverity = when (severityLabel) {
            "critical" -> 9.5
            "high" -> 8.0
            "warning", "medium" -> 5.0
            "low" -> 2.0
            else -> 0.0
        }
        val combined = (ruleId + " " + message).lowercase()
        val properties = linkedMapOf(
            "mobsf.section" to section,
            "mobsf.severity" to severityLabel,
        )
        if ("exported" in combined && "permission" in combined &&
            ("no permission" in combined || "without permission" in combined || "not protected" in combined)
        ) {
            properties["assay.ruleClass"] = "exported-component-no-permission"
        }
        return Finding(
            scanner = Scanner.MOBSF,
            ruleId = ruleId,
            message = Redaction.sanitize(message),
            location = Location(file, line),
            context = Redaction.sanitize(message),
            level = when (severityLabel) {
                "critical", "high" -> SarifLevel.ERROR
                "warning", "medium" -> SarifLevel.WARNING
                else -> SarifLevel.NOTE
            },
            securitySeverity = securitySeverity,
            properties = properties,
        )
    }

    private fun firstFile(value: JsonValue.Obj, section: String, sourceRoot: Path): String? {
        return when (val files = value.value("files")) {
            is JsonValue.Arr -> files.values.firstNotNullOfOrNull { it.stringOrNull() }
            is JsonValue.Obj -> files.values.keys.firstOrNull()
            else -> null
        }?.let { normalizePath(it, section, sourceRoot) }
    }

    private fun normalizePath(raw: String, section: String, sourceRoot: Path): String {
        val candidate = runCatching { Path.of(raw) }.getOrNull() ?: return "mobsf/$section.json"
        return when {
            candidate.isAbsolute -> {
                val root = sourceRoot.toAbsolutePath().normalize()
                val absolute = candidate.toAbsolutePath().normalize()
                if (absolute.startsWith(root)) {
                    Fingerprints.normalizePath(root.relativize(absolute).toString())
                } else {
                    "mobsf/${candidate.fileName ?: Path.of("$section.json")}".replace('\\', '/')
                }
            }
            else -> runCatching { Fingerprints.normalizePath(candidate.normalize().toString()) }
                .getOrElse { "mobsf/$section.json" }
        }
    }
}
