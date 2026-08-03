package dev.assay

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

object DeterministicSecretScanner {
    private val candidate = Regex("(?i)(api[_-]?key|token|secret|password)\\s*[:=]\\s*['\"]?([A-Za-z0-9_\\-/.+=]{8,})")

    fun scan(root: Path): List<Finding> {
        val findings = mutableListOf<Finding>()
        Files.walk(root).use { stream ->
            stream.filter { it.isRegularFile() }
                .filter { !it.toString().contains("/.git/") && !it.fileName.toString().endsWith(".sarif") }
                .sorted()
                .forEach { file ->
                    Files.readAllLines(file).forEachIndexed { index, line ->
                        if (candidate.containsMatchIn(line)) {
                            val relative = root.relativize(file).toString().replace('\\', '/')
                            findings += Finding(
                                scanner = Scanner.GITLEAKS,
                                ruleId = "generic-hardcoded-secret",
                                message = "Potential hardcoded credential",
                                location = Location(relative, index + 1),
                                context = Redaction.sanitize(line),
                            )
                        }
                    }
                }
        }
        return findings
    }
}
