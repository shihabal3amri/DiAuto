package com.andrerinas.openheadunit.utils

internal object DiagnosticReportRedactor {
    private val secret = Regex("(?i)\\b(password|passphrase|psk|token|private.?key|payload|hex)\\b")
    private val mac = Regex("(?i)(?:[0-9a-f]{2}:){5}[0-9a-f]{2}")
    private val ip = Regex("(?<![0-9])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?![0-9])")
    private val ipv6 = Regex("(?i)(?:[0-9a-f]{1,4}:)*[0-9a-f]{0,4}::[0-9a-f:]*(?:%[a-z0-9_.-]+)?|(?:[0-9a-f]{1,4}:){7}[0-9a-f]{1,4}")

    private val networkNameKey = Regex("(?i)\\bssid\\s*[=:]\\s*")
    private val nextField = Regex(",\\s+[A-Za-z][A-Za-z ]{0,11}[=:]")

    /** What a log line prints where a network name would be when there is none. */
    private val noName = setOf("", "null", "<null>", "<none>", "unchanged", "[saved value]")

    fun redact(line: String, ssid: String, password: String): String? {
        if (secret.containsMatchIn(line)) return null
        var value = line
        for (saved in listOf(ssid, password).filter { it.isNotEmpty() }) value = value.replace(saved, "[saved value]")
        value = maskNetworkNames(value)
        return value.replace(mac, "[address]").replace(ip, "[ip]").replace(ipv6, "[ip]").take(1000)
    }

    /**
     * Hides the name after "SSID=" or "SSID:" and keeps the line.
     *
     * These lines used to be dropped whole. They are the ones that say whether WiFi details ever
     * existed and whether a phone was given them ("Credentials updated", "Pre-poke credential wait
     * completed", "SUCCESS - Providing credentials"), so three reports of a phone that never
     * connected (#50, #59, #69) could not say which side had stopped. Only the name is private;
     * whether there was one is the evidence, so "null" and its relatives stay as written.
     *
     * A name runs to the next ", Key=" field when there is one; else, when the line put it inside
     * brackets, to the bracket that closes them; else to the end of the line. A name can itself hold
     * a comma or a bracket, and reading too far only hides more.
     */
    private fun maskNetworkNames(line: String): String {
        var key = networkNameKey.find(line) ?: return line
        val out = StringBuilder()
        var from = 0
        while (true) {
            val start = key.range.last + 1
            val end = nextField.find(line, start)?.range?.first
                ?: bracketClosingAround(line, key.range.first, start)
                ?: line.length
            val name = line.substring(start, end)
            out.append(line, from, start).append(if (name.trim() in noName) name else "[name]")
            from = end
            key = networkNameKey.find(line, from) ?: break
        }
        return out.append(line, from, line.length).toString()
    }

    /** Where the bracket that was open at [keyStart] closes, counting brackets inside the name. */
    private fun bracketClosingAround(line: String, keyStart: Int, valueStart: Int): Int? {
        var open = 0
        for (i in 0 until keyStart) when (line[i]) {
            '(' -> open++
            ')' -> if (open > 0) open--
        }
        if (open == 0) return null
        var depth = 0
        for (i in valueStart until line.length) when (line[i]) {
            '(' -> depth++
            ')' -> if (depth == 0) return i else depth--
        }
        return null
    }
}
