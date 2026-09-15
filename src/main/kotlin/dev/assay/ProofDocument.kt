package dev.assay

object ProofDocument {
    private const val SCHEMA_VERSION = "1.1.0"

    fun decode(text: String): ProofEvidence {
        val root = Json.parse(text).requireObject()
        exact(
            root,
            setOf(
                "schemaVersion", "provingId", "findingFingerprint", "sourceCommit", "beforeTreeRef",
                "afterTreeRef", "patchDigest", "testDigest", "scannerConfigDigest", "toolchainDigest",
                "testName", "beforeAttempts", "afterAttempts", "scannerReplay", "gate",
            ),
            "$",
        )
        require(root.string("schemaVersion") == SCHEMA_VERSION) { "unsupported proof schema version" }
        val replay = root.required("scannerReplay").requireObject("$.scannerReplay")
        exact(replay, setOf("beforeDigest", "afterDigest", "foundBefore", "clearedAfter"), "$.scannerReplay")
        val evidence = ProofEvidence(
            provingId = root.string("provingId"),
            findingFingerprint = root.string("findingFingerprint"),
            sourceCommit = root.string("sourceCommit"),
            beforeTreeRef = root.string("beforeTreeRef"),
            afterTreeRef = root.string("afterTreeRef"),
            patchDigest = root.string("patchDigest"),
            testDigest = root.string("testDigest"),
            scannerConfigDigest = root.string("scannerConfigDigest"),
            toolchainDigest = root.string("toolchainDigest"),
            testName = root.string("testName"),
            beforeAttempts = attempts(root.required("beforeAttempts"), "$.beforeAttempts"),
            afterAttempts = attempts(root.required("afterAttempts"), "$.afterAttempts"),
            scannerReplayBeforeDigest = replay.string("beforeDigest", "$.scannerReplay"),
            scannerReplayAfterDigest = replay.string("afterDigest", "$.scannerReplay"),
            replayFoundBefore = replay.required("foundBefore", "$.scannerReplay").booleanOrNull()
                ?: error("expected boolean at $.scannerReplay.foundBefore"),
            replayClearedAfter = replay.required("clearedAfter", "$.scannerReplay").booleanOrNull()
                ?: error("expected boolean at $.scannerReplay.clearedAfter"),
        )
        val expectedGate = if (evidence.passed) "passed" else "failed"
        require(root.string("gate") == expectedGate) { "proof gate field contradicts evidence" }
        return evidence
    }

    private fun attempts(value: JsonValue, path: String): List<TestAttemptEvidence> =
        value.requireArray(path).values.mapIndexed { index, item ->
            val itemPath = "$path[$index]"
            val attempt = item.requireObject(itemPath)
            exact(attempt, setOf("exitCode", "resultDigest", "namedTestRan", "assertionFailure"), itemPath)
            TestAttemptEvidence(
                exitCode = integer(attempt.required("exitCode", itemPath), "$itemPath.exitCode"),
                resultDigest = attempt.string("resultDigest", itemPath),
                namedTestRan = attempt.required("namedTestRan", itemPath).booleanOrNull()
                    ?: error("expected boolean at $itemPath.namedTestRan"),
                assertionFailure = attempt.required("assertionFailure", itemPath).booleanOrNull()
                    ?: error("expected boolean at $itemPath.assertionFailure"),
            )
        }

    private fun integer(value: JsonValue, path: String): Int {
        val number = (value as? JsonValue.Num)?.value ?: error("expected integer at $path")
        require(number.isFinite() && number % 1.0 == 0.0 && number in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) {
            "invalid integer at $path"
        }
        return number.toInt()
    }

    private fun exact(value: JsonValue.Obj, expected: Set<String>, path: String) {
        require(value.values.keys == expected) {
            "unexpected keys at $path: expected ${expected.sorted()}, got ${value.values.keys.sorted()}"
        }
    }
}
