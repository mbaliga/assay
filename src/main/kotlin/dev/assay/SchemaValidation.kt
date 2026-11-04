package dev.assay

import com.networknt.schema.InputFormat
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion

/**
 * Validates published documents against the byte-identical mirror of Core's ratified JSON
 * Schemas (`schemas/integrations/`, bundled on the classpath at build time). Runs at write time
 * so a document that Core would reject is never staged, let alone pushed.
 */
object IntegrationSchemas {
    const val ASSAY_INDEX = "assay-index.v1.schema.json"
    const val PROVING_TESTS = "proving-tests.v1.schema.json"

    private val factory: JsonSchemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
    private val cache = java.util.concurrent.ConcurrentHashMap<String, JsonSchema>()

    fun validate(schemaName: String, document: String) {
        val schema = cache.getOrPut(schemaName) {
            val resource = "/schemas/integrations/$schemaName"
            val text = IntegrationSchemas::class.java.getResourceAsStream(resource)
                ?.use { it.readAllBytes().toString(Charsets.UTF_8) }
                ?: error("schema resource $resource is not bundled")
            factory.getSchema(text)
        }
        val errors = schema.validate(document, InputFormat.JSON)
        require(errors.isEmpty()) {
            "document violates $schemaName: " + errors.joinToString("; ") { it.message }.take(2_000)
        }
    }
}
