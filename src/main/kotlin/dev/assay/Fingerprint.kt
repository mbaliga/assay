package dev.assay

import java.security.MessageDigest
import java.text.Normalizer

object Fingerprints {
    private const val VERSION = "assay-fingerprint-v1"

    fun finding(scanner: Scanner, ruleId: String, location: Location, context: String): String {
        val canonical = listOf(
            VERSION,
            scanner.wireName,
            normalize(ruleId),
            normalizePath(location.file),
            location.startLine.toString(),
            location.endLine.toString(),
            normalize(context),
        ).joinToString("\u001f")
        return sha256(canonical.toByteArray(Charsets.UTF_8))
    }

    fun provingId(finding: Finding): String {
        val slug = finding.ruleId.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).ifBlank { "rule" }
        return "pt1-${finding.scanner.wireName}-$slug-${finding.fingerprint.take(16)}"
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun normalize(value: String): String = Normalizer.normalize(value.trim().replace("\r\n", "\n"), Normalizer.Form.NFC)
    private fun normalizePath(value: String): String = normalize(value).replace('\\', '/').removePrefix("./")
}
