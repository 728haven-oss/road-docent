package kr.roaddocent

import kr.roaddocent.ui.theme.keepAll
import org.junit.Assert.assertEquals
import org.junit.Test

class KeepAllTest {
    @Test fun gluesWordsButKeepsSpaces() {
        val s = keepAll("세종대왕 이순신 장군")
        assertEquals("세종대왕 이순신 장군", s.replace("⁠", ""))
        assertEquals(listOf("세종대왕", "이순신", "장군"), s.split(' ').map { it.replace("⁠", "") })
        s.split(' ').forEach { w -> assertEquals(w.replace("⁠", "").length * 2 - 1, w.length) } // joiner between every letter
        assertEquals("Road Docent", keepAll("Road Docent"))
    }
}
