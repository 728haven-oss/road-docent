package kr.roaddocent.shots

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import java.io.File

/**
 * Finds text that is cut off (visual overflow / ellipsis) or short labels that wrapped onto 2+ lines
 * (e.g. "이순신 장군" breaking into two lines). Appends findings to a report next to the screenshots.
 */
object TextAudit {
    data class Finding(val screen: String, val kind: String, val text: String, val lines: Int, val widthPx: Int)

    fun audit(rule: ComposeContentTestRule, screen: String, report: File): List<Finding> {
        val out = mutableListOf<Finding>()
        fun walk(n: SemanticsNode) {
            val texts = n.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
            val action = n.config.getOrNull(SemanticsActions.GetTextLayoutResult)
            if (texts != null && action != null) {
                val results = mutableListOf<TextLayoutResult>()
                action.action?.invoke(results)
                results.firstOrNull()?.let { r ->
                    val t = texts.replace("\u2060", "").trim()
                    val raw = r.layoutInput.text.text
                    val ellipsized = (0 until r.lineCount).any { r.isLineEllipsized(it) }
                    // a break between two letters of the same word, e.g. "세종대|왕"
                    val midWord = (0 until r.lineCount - 1).any { i ->
                        val e = r.getLineEnd(i)
                        e in 1 until raw.length && raw[e - 1].isLetterOrDigit() && raw[e].isLetterOrDigit()
                    }
                    val shown = n.boundsInRoot
                    val rootW = rule.onRoot().fetchSemanticsNode().size.width
                    val onScreen = shown.height >= n.size.height - 2f && n.size.height > 0
                    val cutByParent = onScreen && (n.size.width > shown.width + 2f || shown.right > rootW + 1f)
                    when {
                        cutByParent -> out += Finding(screen, "부모에 가려 잘림", t, r.lineCount, n.size.width)
                        (0 until r.lineCount).any { r.getLineRight(it) > r.size.width + 2f } -> out += Finding(screen, "가로 잘림", t, r.lineCount, r.size.width)
                        ellipsized -> out += Finding(screen, "말줄임(…)", t, r.lineCount, r.size.width)
                        midWord -> out += Finding(screen, "단어 중간 줄바꿈", t, r.lineCount, r.size.width)
                        r.lineCount > 1 && t.length <= 14 && !t.contains("\n") && !t.endsWith(".") -> out += Finding(screen, "짧은 라벨 2줄", t, r.lineCount, r.size.width)
                    }
                }
            }
            n.children.forEach { walk(it) }
        }
        walk(rule.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        report.appendText(out.joinToString("") { "${it.screen}\t${it.kind}\t${it.lines}줄\t${it.widthPx}px\t${it.text.take(60)}\n" })
        return out
    }
}
