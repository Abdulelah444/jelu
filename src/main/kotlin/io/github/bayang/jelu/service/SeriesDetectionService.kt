package io.github.bayang.jelu.service

import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

class SeriesDetectionService {
    companion object {
        private val ENGLISH_SERIES_PATTERNS =
            listOf(
                Regex("""(?i)\bbook\s+(\d+|one|two|three|four|five|six|seven|eight|nine|ten)\b"""),
                Regex("""(?i)\bvol(?:ume)?\.?\s*(\d+)\b"""),
                Regex("""#\s*(\d+)"""),
                Regex("""(?i)\bpart\s+(\d+|one|two|three|four|five|six|seven|eight|nine|ten)\b"""),
                Regex("""\(\s*(\d+)\s*\)"""),
                Regex("""-\s*(\d+)\s*-"""),
                Regex("""\b(\d+)\s*$"""),
            )

        private val ARABIC_SERIES_PATTERNS =
            listOf(
                Regex(
                    "\u0627\u0644\u062C\u0632\u0621\\s+(\u0627\u0644\u0623\u0648\u0644|\u0627\u0644\u062B\u0627\u0646\u064A|\u0627\u0644\u062B\u0627\u0644\u062B|\u0627\u0644\u0631\u0627\u0628\u0639|\u0627\u0644\u062E\u0627\u0645\u0633|\u0627\u0644\u0633\u0627\u062F\u0633|\u0627\u0644\u0633\u0627\u0628\u0639|\u0627\u0644\u062B\u0627\u0645\u0646|\u0627\u0644\u062A\u0627\u0633\u0639|\u0627\u0644\u0639\u0627\u0634\u0631|\\d+)",
                ),
                Regex(
                    "\u0627\u0644\u0643\u062A\u0627\u0628\\s+(\u0627\u0644\u0623\u0648\u0644|\u0627\u0644\u062B\u0627\u0646\u064A|\u0627\u0644\u062B\u0627\u0644\u062B|\u0627\u0644\u0631\u0627\u0628\u0639|\u0627\u0644\u062E\u0627\u0645\u0633|\u0627\u0644\u0633\u0627\u062F\u0633|\u0627\u0644\u0633\u0627\u0628\u0639|\u0627\u0644\u062B\u0627\u0645\u0646|\u0627\u0644\u062A\u0627\u0633\u0639|\u0627\u0644\u0639\u0627\u0634\u0631|\\d+)",
                ),
                Regex(
                    "\u0627\u0644\u0645\u062C\u0644\u062F\\s+(\u0627\u0644\u0623\u0648\u0644|\u0627\u0644\u062B\u0627\u0646\u064A|\u0627\u0644\u062B\u0627\u0644\u062B|\u0627\u0644\u0631\u0627\u0628\u0639|\u0627\u0644\u062E\u0627\u0645\u0633|\u0627\u0644\u0633\u0627\u062F\u0633|\u0627\u0644\u0633\u0627\u0628\u0639|\u0627\u0644\u062B\u0627\u0645\u0646|\u0627\u0644\u062A\u0627\u0633\u0639|\u0627\u0644\u0639\u0627\u0634\u0631|\\d+)",
                ),
                Regex("\u062C\\s*(\\d+)"),
            )

        private val ENGLISH_WORD_TO_NUMBER =
            mapOf(
                "one" to 1,
                "two" to 2,
                "three" to 3,
                "four" to 4,
                "five" to 5,
                "six" to 6,
                "seven" to 7,
                "eight" to 8,
                "nine" to 9,
                "ten" to 10,
            )

        private val ARABIC_ORDINAL_TO_NUMBER =
            mapOf(
                "\u0627\u0644\u0623\u0648\u0644" to 1,
                "\u0627\u0644\u062B\u0627\u0646\u064A" to 2,
                "\u0627\u0644\u062B\u0627\u0644\u062B" to 3,
                "\u0627\u0644\u0631\u0627\u0628\u0639" to 4,
                "\u0627\u0644\u062E\u0627\u0645\u0633" to 5,
                "\u0627\u0644\u0633\u0627\u062F\u0633" to 6,
                "\u0627\u0644\u0633\u0627\u0628\u0639" to 7,
                "\u0627\u0644\u062B\u0627\u0645\u0646" to 8,
                "\u0627\u0644\u062A\u0627\u0633\u0639" to 9,
                "\u0627\u0644\u0639\u0627\u0634\u0631" to 10,
            )

        fun detectFromTitle(title: String): SeriesHint? {
            for (pattern in ENGLISH_SERIES_PATTERNS) {
                val match = pattern.find(title)
                if (match != null) {
                    val rawNumber = match.groupValues[1].lowercase()
                    val position =
                        rawNumber.toDoubleOrNull()
                            ?: ENGLISH_WORD_TO_NUMBER[rawNumber]?.toDouble()
                    return SeriesHint(
                        reason = HintReason.TITLE_PATTERN,
                        suggestedSeriesName = null,
                        suggestedPosition = position,
                        detail = "Title contains \"${match.value}\" which suggests this is part of a series",
                    )
                }
            }
            for (pattern in ARABIC_SERIES_PATTERNS) {
                val match = pattern.find(title)
                if (match != null) {
                    val rawNumber = match.groupValues[1]
                    val position =
                        rawNumber.toDoubleOrNull()
                            ?: ARABIC_ORDINAL_TO_NUMBER[rawNumber]?.toDouble()
                    return SeriesHint(
                        reason = HintReason.TITLE_PATTERN,
                        suggestedSeriesName = null,
                        suggestedPosition = position,
                        detail = "Title contains \"${match.value}\" which suggests this is part of a series",
                    )
                }
            }
            return null
        }
    }

    data class SeriesHint(
        val reason: HintReason,
        val suggestedSeriesName: String?,
        val suggestedPosition: Double?,
        val detail: String,
    )

    enum class HintReason {
        TITLE_PATTERN,
        SAME_AUTHOR_HAS_SERIES,
    }
}
