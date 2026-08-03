package dev.assay

object Sarif {
    fun encode(findings: List<Finding>): String {
        val results = findings.joinToString(",\n") { finding ->
            """{
              "ruleId":"${json(finding.ruleId)}",
              "level":"error",
              "message":{"text":"${json(finding.message)}"},
              "locations":[{"physicalLocation":{"artifactLocation":{"uri":"${json(finding.location.file)}"},"region":{"startLine":${finding.location.startLine},"endLine":${finding.location.endLine}}}}],
              "partialFingerprints":{"assayFingerprint/v1":"${finding.fingerprint}"},
              "properties":{"originatingScanner":"${finding.scanner.wireName}"}
            }"""
        }
        return """{
          "version":"2.1.0",
          "${'$'}schema":"https://json.schemastore.org/sarif-2.1.0.json",
          "runs":[{"tool":{"driver":{"name":"Assay deterministic normalizer","version":"1.0.0"}},"results":[${results}]}]
        }""".trimIndent()
    }

    private fun json(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
