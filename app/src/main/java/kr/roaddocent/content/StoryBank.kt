package kr.roaddocent.content

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One script from the Korea Tourism Organization storytelling DB (public data, no usage restriction). */
@Serializable
data class StoryItem(val id: String, val spot: String, val title: String, val text: String, val sec: Int)

@Serializable
private data class Bank(val source: String = "", val count: Int = 0, val items: List<StoryItem> = emptyList())

/**
 * 1,624 Korean audio-guide scripts keyed by spot name. Used as the "tell me more" layer when an episode has no
 * hand-written deep text, and as extra material for free-ride mode. No coordinates in the source, so matching is by name.
 */
class StoryBank(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val bank: Bank by lazy {
        runCatching { context.assets.open("storybank/kto_storytelling.json").bufferedReader().use { json.decodeFromString(Bank.serializer(), it.readText()) } }.getOrDefault(Bank())
    }
    val size: Int get() = bank.items.size
    val source: String get() = bank.source

    private fun norm(s: String) = s.replace(Regex("[\\s·,()\\[\\]'\"“”]"), "")

    /** Items whose spot name is contained in [query] or vice versa (longest spot names first). */
    fun find(query: String, limit: Int = 3): List<StoryItem> {
        val q = norm(query); if (q.length < 2) return emptyList()
        return bank.items.asSequence()
            .filter { val s = norm(it.spot); s.length >= 2 && (q.contains(s) || s.contains(q)) }
            .sortedByDescending { norm(it.spot).length }
            .take(limit).toList()
    }

    /** Best script for a set of names (place name, episode title words), or null. */
    fun bestFor(vararg names: String): StoryItem? = names.asSequence().flatMap { find(it, 1).asSequence() }.firstOrNull()
}
