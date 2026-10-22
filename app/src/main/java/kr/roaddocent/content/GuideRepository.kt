package kr.roaddocent.content

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kr.roaddocent.core.Guide
import kr.roaddocent.core.Trigger

/**
 * Loads guides. Today: bundled JSON per destination (pre-generated, human-reviewed, with sources).
 * Production: a server builds the same JSON per destination from TourAPI, KMA weather, encyclopedias,
 * creator courses and dwell-time data, and the car downloads it once at route start (offline-safe).
 */
class GuideRepository(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
        serializersModule = SerializersModule {
            polymorphic(Trigger::class) {
                subclass(Trigger.AtStart::class)
                subclass(Trigger.NearPoint::class)
                subclass(Trigger.RemainUnderMin::class)
                subclass(Trigger.Congestion::class)
                subclass(Trigger.RestAreaAhead::class)
                subclass(Trigger.Reboard::class)
            }
        }
    }

    private val cache = mutableMapOf<String, Guide>()

    fun load(id: String): Guide = cache.getOrPut(id) {
        context.assets.open("guides/$id.json").bufferedReader().use { json.decodeFromString(Guide.serializer(), it.readText()) }
    }

    /** Pick the guide whose destination name matches the navigation destination (any language). */
    fun forDestination(name: String): Guide {
        val all = available().map { load(it) }
        val n = name.lowercase()
        return all.firstOrNull { g -> g.destination.name.values.any { v -> n.contains(v.lowercase()) || v.lowercase().contains(n) } }
            ?: all.firstOrNull { g -> g.id.split("_").any { k -> k.length > 3 && n.contains(k) } }
            ?: load("seoul_gangneung")
    }
    fun all(): List<Guide> = available().sorted().map { load(it) }

    fun available(): List<String> = context.assets.list("guides")?.map { it.removeSuffix(".json") } ?: emptyList()
}
