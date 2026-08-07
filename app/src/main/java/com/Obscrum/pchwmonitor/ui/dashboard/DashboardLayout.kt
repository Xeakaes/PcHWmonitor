package com.Obscrum.pchwmonitor.ui.dashboard

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

enum class CardId(val storageId: String) {
    CPU("cpu"), GPU("gpu"), IGPU("igpu"), FPS("fps"),
    RAM("ram"), DISK("disk"), NET("net"), FAN("fan");

    companion object {
        fun fromStorage(s: String): CardId? = entries.firstOrNull { it.storageId == s }
    }
}

enum class CardPriority { PINNED, NORMAL }

fun LayoutEntry.priority(): CardPriority = if (pinned) CardPriority.PINNED else CardPriority.NORMAL

data class LayoutEntry(
    val card: CardId,
    val visible: Boolean = true,
    val pinned: Boolean = false,
    val wide: Boolean = false,
) {
    internal fun toDto() = LayoutEntryDto(card.storageId, visible, pinned, wide)

    companion object {
        internal fun fromDto(d: LayoutEntryDto): LayoutEntry? =
            CardId.fromStorage(d.card)?.let { LayoutEntry(it, d.visible, d.pinned, d.wide) }
    }
}

data class DashboardLayout(val entries: List<LayoutEntry> = emptyList()) {
    fun visibleEntries() = entries.filter { it.visible }

    fun toJson(): String = Json.encodeToString(ListSerializer(LayoutEntryDto.serializer()), entries.map { it.toDto() })

    fun fromJson(s: String): DashboardLayout = runCatching {
        val list = Json.decodeFromString(ListSerializer(LayoutEntryDto.serializer()), s)
        DashboardLayout(list.mapNotNull(LayoutEntry::fromDto))
    }.getOrDefault(default())

    companion object {
        private val Json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        fun default() = DashboardLayout(
            listOf(
                LayoutEntry(CardId.CPU, pinned = true),
                LayoutEntry(CardId.GPU, pinned = true),
                LayoutEntry(CardId.IGPU),
                LayoutEntry(CardId.FPS, pinned = true),
                LayoutEntry(CardId.RAM, pinned = true),
                LayoutEntry(CardId.DISK),
                LayoutEntry(CardId.NET),
                LayoutEntry(CardId.FAN),
            ),
        )
    }
}

@Serializable
internal data class LayoutEntryDto(val card: String, val visible: Boolean, val pinned: Boolean, val wide: Boolean = false)
