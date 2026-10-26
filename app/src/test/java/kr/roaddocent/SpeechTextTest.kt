package kr.roaddocent

import kr.roaddocent.core.Lang
import kr.roaddocent.speech.SpeechText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SpeechTextTest {
    @Test fun arrowsBracketsAndUnitsReadNaturally() {
        assertEquals("광화문, 그다음 흥례문, 그다음 근정전", SpeechText.normalize("광화문 → 흥례문 → 근정전", Lang.KO))
        assertEquals("주차: 경복궁 주차장, 2시간 3,000원대, 주말 만차", SpeechText.normalize("주차: 경복궁 주차장(2시간 3,000원대) · 주말 만차", Lang.KO))
        assertEquals("북촌 통행 시간 10에서 17시", SpeechText.normalize("북촌 통행 시간 10~17시", Lang.KO))
        assertEquals("시속 100킬로미터, 150미터 안, 35피에스아이", SpeechText.normalize("시속 100km/h · 150m 안 · 35psi", Lang.KO))
        assertEquals("Gwanghwamun, then Heungnyemun, 10 to 17", SpeechText.normalize("Gwanghwamun → Heungnyemun · 10–17", Lang.EN))
    }

    @Test fun emojiStrippedAndSentencesKeepNumbers() {
        assertEquals("퀴즈. 정답은 세 번이에요.", SpeechText.normalize("❓ 퀴즈. 정답은 세 번이에요. 🎉", Lang.KO))
        val s = SpeechText.sentences("인구는 2,600만 명이에요. 면적은 605.2제곱킬로미터! 맞죠?")
        assertEquals(3, s.size); assertTrue(s[0].contains("2,600")); assertTrue(s[1].contains("605.2"))
    }

    @Test fun everyGuideLineNormalizesWithoutLeftoverSymbols() {
        val bad = Regex("[→·~/()]")
        File("src/main/assets/guides").listFiles { f -> f.extension == "json" }!!.forEach { f ->
            val raw = f.readText()
            Regex("\"(ko|en)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(raw).forEach { m ->
                val lang = if (m.groupValues[1] == "ko") Lang.KO else Lang.EN
                val out = SpeechText.normalize(m.groupValues[2].replace("\\\"", "\""), lang)
                assertFalse("${f.name}: '$out'", bad.containsMatchIn(out))
            }
        }
    }
}
