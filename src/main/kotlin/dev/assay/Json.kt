package dev.assay

sealed interface JsonValue {
    data class Obj(val values: LinkedHashMap<String, JsonValue> = linkedMapOf()) : JsonValue
    data class Arr(val values: MutableList<JsonValue> = mutableListOf()) : JsonValue
    data class Str(val value: String) : JsonValue
    data class Num(val value: Double) : JsonValue
    data class Bool(val value: Boolean) : JsonValue
    data object Null : JsonValue
}

object Json {
    fun parse(text: String): JsonValue = Parser(text).parse()

    fun stringify(value: JsonValue, pretty: Boolean = false): String = buildString {
        Writer(this, pretty).write(value, 0)
    }

    fun obj(vararg pairs: Pair<String, JsonValue>): JsonValue.Obj = JsonValue.Obj(linkedMapOf(*pairs))
    fun arr(values: Iterable<JsonValue>): JsonValue.Arr = JsonValue.Arr(values.toMutableList())
    fun str(value: String): JsonValue.Str = JsonValue.Str(value)
    fun num(value: Number): JsonValue.Num = JsonValue.Num(value.toDouble())
    fun bool(value: Boolean): JsonValue.Bool = JsonValue.Bool(value)
    val nullValue: JsonValue = JsonValue.Null

    private class Parser(private val text: String) {
        private var index = 0

        fun parse(): JsonValue {
            skipWhitespace()
            val value = readValue()
            skipWhitespace()
            require(index == text.length) { "unexpected trailing JSON at offset $index" }
            return value
        }

        private fun readValue(): JsonValue {
            require(index < text.length) { "unexpected end of JSON" }
            return when (text[index]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> JsonValue.Str(readString())
                't' -> { expect("true"); JsonValue.Bool(true) }
                'f' -> { expect("false"); JsonValue.Bool(false) }
                'n' -> { expect("null"); JsonValue.Null }
                '-', in '0'..'9' -> JsonValue.Num(readNumber())
                else -> error("invalid JSON token '${text[index]}' at offset $index")
            }
        }

        private fun readObject(): JsonValue.Obj {
            index++
            skipWhitespace()
            val values = linkedMapOf<String, JsonValue>()
            if (peek('}')) { index++; return JsonValue.Obj(values) }
            while (true) {
                skipWhitespace()
                require(peek('"')) { "object key must be a string at offset $index" }
                val key = readString()
                skipWhitespace()
                require(peek(':')) { "expected ':' after object key at offset $index" }
                index++
                skipWhitespace()
                require(key !in values) { "duplicate JSON key '$key'" }
                values[key] = readValue()
                skipWhitespace()
                when {
                    peek(',') -> { index++; skipWhitespace() }
                    peek('}') -> { index++; return JsonValue.Obj(values) }
                    else -> error("expected ',' or '}' at offset $index")
                }
            }
        }

        private fun readArray(): JsonValue.Arr {
            index++
            skipWhitespace()
            val values = mutableListOf<JsonValue>()
            if (peek(']')) { index++; return JsonValue.Arr(values) }
            while (true) {
                values += readValue()
                skipWhitespace()
                when {
                    peek(',') -> { index++; skipWhitespace() }
                    peek(']') -> { index++; return JsonValue.Arr(values) }
                    else -> error("expected ',' or ']' at offset $index")
                }
            }
        }

        private fun readString(): String {
            require(peek('"'))
            index++
            val out = StringBuilder()
            while (index < text.length) {
                val ch = text[index++]
                when (ch) {
                    '"' -> return out.toString()
                    '\\' -> {
                        require(index < text.length) { "unterminated escape" }
                        when (val escaped = text[index++]) {
                            '"', '\\', '/' -> out.append(escaped)
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000c')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                require(index + 4 <= text.length) { "incomplete unicode escape" }
                                val code = text.substring(index, index + 4).toIntOrNull(16)
                                    ?: error("invalid unicode escape")
                                out.append(code.toChar())
                                index += 4
                            }
                            else -> error("invalid escape \\$escaped")
                        }
                    }
                    else -> {
                        require(ch.code >= 0x20) { "control character in JSON string" }
                        out.append(ch)
                    }
                }
            }
            error("unterminated JSON string")
        }

        private fun readNumber(): Double {
            val start = index
            if (peek('-')) index++
            if (peek('0')) {
                index++
            } else {
                require(index < text.length && text[index].isDigit()) { "invalid number" }
                while (index < text.length && text[index].isDigit()) index++
            }
            if (peek('.')) {
                index++
                require(index < text.length && text[index].isDigit()) { "invalid fraction" }
                while (index < text.length && text[index].isDigit()) index++
            }
            if (peek('e') || peek('E')) {
                index++
                if (peek('+') || peek('-')) index++
                require(index < text.length && text[index].isDigit()) { "invalid exponent" }
                while (index < text.length && text[index].isDigit()) index++
            }
            return text.substring(start, index).toDouble()
        }

        private fun expect(token: String) {
            require(text.regionMatches(index, token, 0, token.length)) { "expected '$token' at offset $index" }
            index += token.length
        }

        private fun skipWhitespace() {
            while (index < text.length && text[index] in charArrayOf(' ', '\n', '\r', '\t')) index++
        }

        private fun peek(ch: Char): Boolean = index < text.length && text[index] == ch
    }

    private class Writer(private val out: StringBuilder, private val pretty: Boolean) {
        fun write(value: JsonValue, depth: Int) {
            when (value) {
                is JsonValue.Obj -> writeObject(value, depth)
                is JsonValue.Arr -> writeArray(value, depth)
                is JsonValue.Str -> writeString(value.value)
                is JsonValue.Num -> {
                    require(value.value.isFinite()) { "JSON numbers must be finite" }
                    val asLong = value.value.toLong()
                    out.append(if (asLong.toDouble() == value.value) asLong else value.value)
                }
                is JsonValue.Bool -> out.append(value.value)
                JsonValue.Null -> out.append("null")
            }
        }

        private fun writeObject(value: JsonValue.Obj, depth: Int) {
            out.append('{')
            val entries = value.values.entries.sortedBy { it.key }
            entries.forEachIndexed { i, entry ->
                if (i > 0) out.append(',')
                newline(depth + 1)
                writeString(entry.key)
                out.append(if (pretty) ": " else ":")
                write(entry.value, depth + 1)
            }
            if (entries.isNotEmpty()) newline(depth)
            out.append('}')
        }

        private fun writeArray(value: JsonValue.Arr, depth: Int) {
            out.append('[')
            value.values.forEachIndexed { i, item ->
                if (i > 0) out.append(',')
                newline(depth + 1)
                write(item, depth + 1)
            }
            if (value.values.isNotEmpty()) newline(depth)
            out.append(']')
        }

        private fun writeString(value: String) {
            out.append('"')
            value.forEach { ch ->
                when (ch) {
                    '"' -> out.append("\\\"")
                    '\\' -> out.append("\\\\")
                    '\b' -> out.append("\\b")
                    '\u000c' -> out.append("\\f")
                    '\n' -> out.append("\\n")
                    '\r' -> out.append("\\r")
                    '\t' -> out.append("\\t")
                    else -> if (ch.code < 0x20) out.append("\\u%04x".format(ch.code)) else out.append(ch)
                }
            }
            out.append('"')
        }

        private fun newline(depth: Int) {
            if (pretty) {
                out.append('\n')
                repeat(depth) { out.append("  ") }
            }
        }
    }
}

fun JsonValue.requireObject(path: String = "$"): JsonValue.Obj = this as? JsonValue.Obj
    ?: error("expected object at $path")
fun JsonValue.requireArray(path: String = "$"): JsonValue.Arr = this as? JsonValue.Arr
    ?: error("expected array at $path")
fun JsonValue.requireString(path: String = "$"): String = (this as? JsonValue.Str)?.value
    ?: error("expected string at $path")
fun JsonValue.stringOrNull(): String? = when (this) {
    is JsonValue.Str -> value
    is JsonValue.Num -> value.toString()
    is JsonValue.Bool -> value.toString()
    else -> null
}
fun JsonValue.booleanOrNull(): Boolean? = when (this) {
    is JsonValue.Bool -> value
    is JsonValue.Str -> value.toBooleanStrictOrNull()
    else -> null
}
fun JsonValue.doubleOrNull(): Double? = when (this) {
    is JsonValue.Num -> value
    is JsonValue.Str -> value.toDoubleOrNull()
    else -> null
}
fun JsonValue.Obj.value(name: String): JsonValue? = values[name]
fun JsonValue.Obj.required(name: String, path: String = "$"): JsonValue = values[name]
    ?: error("missing '$name' at $path")
fun JsonValue.Obj.string(name: String, path: String = "$"): String = required(name, path).requireString("$path.$name")
fun JsonValue.Obj.stringOrNull(name: String): String? = values[name]?.stringOrNull()
fun JsonValue.Obj.boolOrNull(name: String): Boolean? = values[name]?.booleanOrNull()
fun JsonValue.Obj.doubleOrNull(name: String): Double? = values[name]?.doubleOrNull()
fun JsonValue.Obj.arrayOrNull(name: String): JsonValue.Arr? = values[name] as? JsonValue.Arr
fun JsonValue.Obj.objectOrNull(name: String): JsonValue.Obj? = values[name] as? JsonValue.Obj
