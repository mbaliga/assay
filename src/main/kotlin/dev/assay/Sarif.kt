package dev.assay

import java.net.URI
import java.nio.file.Path

object Sarif {
    fun encode(findings: List<Finding>): String {
        val sorted = merge(findings)
        val rules = sorted.groupBy { it.ruleId }.toSortedMap().map { (ruleId, group) ->
            val exemplar = group.minBy { it.severity.ordinal }
            Json.obj(
                "id" to Json.str(ruleId),
                "shortDescription" to Json.obj("text" to Json.str(exemplar.message)),
                "properties" to propertiesJson(
                    exemplar.properties + mapOf(
                        "security-severity" to (exemplar.securitySeverity?.toString() ?: ""),
                        "assay.severityMappingVersion" to SeverityPolicy.VERSION,
                    ).filterValues { it.isNotBlank() },
                ),
            )
        }
        val results = sorted.map { finding ->
            Json.obj(
                "ruleId" to Json.str(finding.ruleId),
                "level" to Json.str(finding.level.wireName),
                "message" to Json.obj("text" to Json.str(Redaction.sanitize(finding.message))),
                "locations" to Json.arr(
                    listOf(
                        Json.obj(
                            "physicalLocation" to Json.obj(
                                "artifactLocation" to Json.obj("uri" to Json.str(finding.location.file)),
                                "region" to Json.obj(
                                    "startLine" to Json.num(finding.location.startLine),
                                    "endLine" to Json.num(finding.location.endLine),
                                    "snippet" to Json.obj("text" to Json.str(Redaction.sanitize(finding.context))),
                                ),
                            ),
                        ),
                    ),
                ),
                "partialFingerprints" to Json.obj("assayFingerprint/v1" to Json.str(finding.fingerprint)),
                "properties" to propertiesJson(
                    finding.properties + mapOf(
                        "originatingScanner" to finding.scanner.wireName,
                        "assaySeverity" to finding.severity.name,
                        "assay.severityMappingVersion" to SeverityPolicy.VERSION,
                    ) + (finding.securitySeverity?.let { mapOf("security-severity" to it.toString()) } ?: emptyMap()),
                ),
            )
        }
        val document = Json.obj(
            "version" to Json.str("2.1.0"),
            "\$schema" to Json.str("https://json.schemastore.org/sarif-2.1.0.json"),
            "runs" to Json.arr(
                listOf(
                    Json.obj(
                        "tool" to Json.obj(
                            "driver" to Json.obj(
                                "name" to Json.str("Assay deterministic normalizer"),
                                "version" to Json.str("1.1.0"),
                                "rules" to Json.arr(rules),
                            ),
                        ),
                        "results" to Json.arr(results),
                    ),
                ),
            ),
        )
        return Json.stringify(document, pretty = true)
    }

    fun decode(scanner: Scanner, raw: String, sourceRoot: Path? = null): List<Finding> =
        decodeInternal(scanner, raw, sourceRoot)

    fun decodeCanonical(raw: String): List<Finding> = decodeInternal(null, raw, null)

    private fun decodeInternal(forcedScanner: Scanner?, raw: String, sourceRoot: Path?): List<Finding> {
        val root = Json.parse(raw).requireObject()
        require(root.string("version") == "2.1.0") { "scanner emitted unsupported SARIF version" }
        val runs = root.required("runs").requireArray("$.runs").values
        val findings = mutableListOf<Finding>()
        runs.forEachIndexed { runIndex, runValue ->
            val run = runValue.requireObject("$.runs[$runIndex]")
            val driver = run.required("tool").requireObject("$.runs[$runIndex].tool")
                .required("driver").requireObject("$.runs[$runIndex].tool.driver")
            val ruleProperties = mutableMapOf<String, Map<String, String>>()
            driver.arrayOrNull("rules")?.values?.forEachIndexed { ruleIndex, value ->
                val rule = value.requireObject("$.runs[$runIndex].tool.driver.rules[$ruleIndex]")
                val id = rule.string("id")
                ruleProperties[id] = flattenProperties(rule.objectOrNull("properties"))
            }
            run.arrayOrNull("results")?.values.orEmpty().forEachIndexed { resultIndex, value ->
                val path = "$.runs[$runIndex].results[$resultIndex]"
                val result = value.requireObject(path)
                val ruleId = result.string("ruleId", path)
                val message = result.required("message", path).requireObject("$path.message")
                    .string("text", "$path.message")
                val level = SarifLevel.parse(result.stringOrNull("level"))
                val location = parseLocation(result, path, sourceRoot)
                val resultProperties = flattenProperties(result.objectOrNull("properties"))
                val properties = (ruleProperties[ruleId].orEmpty() + resultProperties)
                    .filterKeys { it != "originatingScanner" }
                    .toMutableMap()
                val scanner = forcedScanner ?: resultProperties["originatingScanner"]
                    ?.let(Scanner::fromWireName)
                    ?: error("canonical SARIF result is missing originatingScanner")
                properties["originatingScanner"] = scanner.wireName
                if (scanner == Scanner.GITLEAKS && !properties["commit"].isNullOrBlank()) {
                    properties["assay.ruleClass"] = "committed-live-secret"
                }
                val securitySeverity = resultProperties["security-severity"]?.toDoubleOrNull()
                    ?: ruleProperties[ruleId]?.get("security-severity")?.toDoubleOrNull()
                val context = result.arrayOrNull("locations")?.values?.firstOrNull()
                    ?.requireObject()?.objectOrNull("physicalLocation")?.objectOrNull("region")
                    ?.objectOrNull("snippet")?.stringOrNull("text")
                    ?: message
                findings += Finding(
                    scanner = scanner,
                    ruleId = ruleId,
                    message = Redaction.sanitize(message),
                    location = location,
                    context = Redaction.sanitize(context),
                    level = level,
                    securitySeverity = securitySeverity,
                    properties = properties.toSortedMap(),
                )
            }
        }
        return merge(findings)
    }

    fun merge(findings: List<Finding>): List<Finding> = findings
        .groupBy { it.fingerprint }
        .map { (_, group) -> group.minWith(compareBy<Finding> { it.severity.ordinal }.thenBy { it.message }) }
        .sortedWith(
            compareBy<Finding> { it.scanner.wireName }
                .thenBy { it.ruleId }
                .thenBy { it.location.file }
                .thenBy { it.location.startLine }
                .thenBy { it.fingerprint },
        )

    private fun parseLocation(result: JsonValue.Obj, path: String, sourceRoot: Path?): Location {
        val locations = result.required("locations", path).requireArray("$path.locations")
        require(locations.values.isNotEmpty()) { "result has no location at $path" }
        val physical = locations.values.first().requireObject("$path.locations[0]")
            .required("physicalLocation", "$path.locations[0]")
            .requireObject("$path.locations[0].physicalLocation")
        val uri = physical.required("artifactLocation").requireObject().string("uri")
        val file = normalizeArtifactUri(uri, sourceRoot)
        val region = physical.objectOrNull("region")
        val startLine = region?.doubleOrNull("startLine")?.toInt() ?: 1
        val endLine = region?.doubleOrNull("endLine")?.toInt() ?: startLine
        return Location(file, startLine, endLine)
    }

    private fun normalizeArtifactUri(raw: String, sourceRoot: Path?): String {
        val decoded = if (raw.startsWith("file:")) Path.of(URI(raw)) else Path.of(raw)
        val path = when {
            decoded.isAbsolute -> {
                require(sourceRoot != null) { "absolute scanner path cannot be normalized without source root" }
                val root = sourceRoot.toAbsolutePath().normalize()
                val absolute = decoded.toAbsolutePath().normalize()
                require(absolute.startsWith(root)) { "scanner path escapes source root" }
                root.relativize(absolute).toString()
            }
            else -> decoded.normalize().toString()
        }
        return Fingerprints.normalizePath(path)
    }

    private fun flattenProperties(properties: JsonValue.Obj?): Map<String, String> = properties?.values
        ?.mapNotNull { (key, value) -> value.stringOrNull()?.let { key to it } }
        ?.toMap()
        .orEmpty()

    private fun propertiesJson(properties: Map<String, String>): JsonValue.Obj = JsonValue.Obj(
        LinkedHashMap(properties.toSortedMap().mapValues { (_, value) -> Json.str(value) }),
    )
}
