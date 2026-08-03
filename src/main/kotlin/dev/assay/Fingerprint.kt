package dev.assay

import java.security.MessageDigest
import java.text.Normalizer

object Fingerprints {
    private const val VERSION = "assay-fingerprint-v1"

    fun finding(scanner: Scanner, ruleId: String, location: Location, context: String): String {
        val canonical = listOf(
            VERSION,
            scanner.wireName,
            normalize(ruleId).lowercase(),
            normalizePath(location.file),
            location.startLine.toString(),
            location.endLine.toString(),
            normalize(Redaction.sanitize(context)),
        ).joinToString("\u001f")
        return sha256(canonical.toByteArray(Charsets.UTF_8))
    }

    fun provingId(finding: Finding): String {
        val slug = finding.ruleId.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(40)
            .ifBlank { "rule" }
        return "pt1-${finding.scanner.wireName}-$slug-${finding.fingerprint.take(16)}"
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    fun normalizePath(value: String): String {
        val normalized = normalize(value).replace('\\', '/').removePrefix("./")
        require(!normalized.startsWith('/') && !Regex("^[A-Za-z]:/").containsMatchIn(normalized)) { "absolute path rejected" }
        require(normalized.split('/').none { it == ".." || it.isBlank() }) { "unsafe path" }
        return normalized
    }

    private fun normalize(value: String): String = Normalizer.normalize(
        value.trim().replace("\r\n", "\n").replace('\r', '\n'),
        Normalizer.Form.NFC,
    )
}
