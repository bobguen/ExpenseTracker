package com.expensetracker.voice

object AmountParser {
    private val amountRegex = Regex("""(?i)(?<cur>\$|€|£|usd|eur|inr|jpy)?\s*(?<amt>\d{1,6}(?:,\d{3})*(?:\.\d{1,2})?)\s*(?<cur2>dollars|euros|rupees|bucks)?""")
    private val numberWords = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "hundred" to 100, "thousand" to 1000
    )

    data class Parsed(val amountMinor: Long, val currency: String, val description: String)

    fun parse(raw: String, defaultCurrency: String = "USD"): Parsed? {
        val lower = raw.lowercase()
        // Try numeric
        val match = amountRegex.find(raw)
        var amount: Double? = match?.groups?.get("amt")?.value?.replace(",", "")?.toDoubleOrNull()
        var currency = match?.groups?.get("cur")?.value?.ifEmpty { null } ?: match?.groups?.get("cur2")?.value
        currency = when (currency?.lowercase()?.trim()) {
            "$", "usd", "dollars", "bucks", "dollar" -> "USD"
            "€", "eur", "euros" -> "EUR"
            "£", "gbp" -> "GBP"
            "inr", "rs", "rupees", "₹" -> "INR"
            "jpy", "yen" -> "JPY"
            else -> defaultCurrency
        }
        // Fallback number words like "one hundred"
        if (amount == null) {
            val words = lower.split(" ")
            var total = 0
            var current = 0
            for (w in words) {
                val v = numberWords[w]
                if (v != null) {
                    if (v == 100 || v == 1000) {
                        if (current == 0) current = 1
                        current *= v
                        if (v == 1000) { total += current; current = 0 }
                    } else current += v
                }
            }
            total += current
            if (total > 0) amount = total.toDouble()
        }
        if (amount == null) return null
        val desc = raw.replace(match?.value ?: "", "").trim().ifEmpty { "general" }
        return Parsed((amount * 100).toLong(), currency, desc)
    }
}
