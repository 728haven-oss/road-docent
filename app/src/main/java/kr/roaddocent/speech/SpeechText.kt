package kr.roaddocent.speech

import kr.roaddocent.core.Lang

/**
 * Makes written guide text read well aloud. The JSON stays readable on screen ("광화문 → 흥례문", "2시간 3,000원대"),
 * while the TTS gets spoken forms ("광화문, 그다음 흥례문", "2시간 3,000원대" with the brackets turned into pauses).
 */
object SpeechText {
    private val emoji = Regex("[\\p{So}\\p{Cn}\\uFE0F\\u200D]+")
    private val units = listOf(
        Regex("(\\d)\\s?km/h") to "$1킬로미터", Regex("(\\d)\\s?km\\b") to "$1킬로미터", Regex("(\\d)\\s?m\\b") to "$1미터",
        Regex("(\\d)\\s?cm\\b") to "$1센티미터", Regex("(\\d)\\s?mm\\b") to "$1밀리미터", Regex("(\\d)\\s?kg\\b") to "$1킬로그램",
        Regex("(\\d)\\s?psi\\b") to "$1피에스아이", Regex("(\\d)\\s?℃") to "$1도", Regex("(\\d)\\s?㎡") to "$1제곱미터", Regex("(\\d)\\s?%") to "$1퍼센트")
    private val unitsEn = listOf(Regex("(\\d)\\s?km/h") to "$1 km per hour", Regex("(\\d)\\s?km\\b") to "$1 kilometres", Regex("(\\d)\\s?m\\b") to "$1 metres", Regex("(\\d)\\s?℃") to "$1 degrees")

    fun normalize(text: String, lang: Lang): String {
        var t = text.replace(emoji, " ")
        t = t.replace("…", "...")
        if (lang == Lang.KO) {
            units.forEach { (r, rep) -> t = t.replace(r, rep) }
            t = t.replace(Regex("\\s*→\\s*"), ", 그다음 ")
            t = t.replace(Regex("\\s*·\\s*"), ", ")
            t = t.replace(Regex("(\\d)\\s*~\\s*(\\d)"), "$1에서 $2")      // 10~17시 → 10에서 17시
            t = t.replace(Regex("\\s*~\\s*"), " 에서 ")
            t = t.replace(Regex("\\s*/\\s*"), ", ")                        // m4a/mp3, 수·토 등
            t = t.replace(Regex("\\s*\\(([^)]*)\\)"), ", $1,")            // (2시간 3,000원대) → , 2시간 3,000원대,
            t = t.replace(Regex("[‘’“”\"「」『』]"), "")                     // quote marks: some engines pause or read them
            t = t.replace(Regex("\\s*—\\s*|\\s+-\\s+"), ", ")
            t = t.replace("KTX", "케이티엑스").replace("K-POP", "케이팝").replace("K-pop", "케이팝").replace("K-뷰티", "케이뷰티").replace("RM", "알엠")
            t = t.replace("EBS", "이비에스").replace("ADAS", "에이다스").replace("TPMS", "티피엠에스").replace("EV", "이브이").replace("12V", "12볼트")
        } else {
            t = t.replace(Regex("\\s*→\\s*"), ", then ")
            t = t.replace(Regex("\\s*·\\s*"), ", ")
            unitsEn.forEach { (r, rep) -> t = t.replace(r, rep) }
            t = t.replace(Regex("(\\d)\\s*[~–]\\s*(\\d)"), "$1 to $2")
            t = t.replace(Regex("\\s*\\(([^)]*)\\)"), ", $1,")
            t = t.replace("₩", "")
        }
        t = t.replace(Regex(",\\s*,"), ",").replace(Regex("\\s{2,}"), " ").replace(Regex("\\s+([,.!?])"), "$1").trim()
        return t
    }

    /** Sentence split that keeps numbers like "3.5" and "2,600" together. */
    fun sentences(text: String): List<String> =
        text.split(Regex("(?<=[.!?。])\\s+(?=\\S)")).map { it.trim() }.filter { it.isNotBlank() }
}
