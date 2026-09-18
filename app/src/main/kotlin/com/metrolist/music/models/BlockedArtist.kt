package com.metrolist.music.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BlockedArtist(
    val id: String,
    val name: String,
    val blockExpiryTime: Long // 0 means forever
)

object BlockedArtistManager {
    fun parse(json: String?): List<BlockedArtist> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching { Json.decodeFromString<List<BlockedArtist>>(json) }.getOrDefault(emptyList())
    }
    
    fun encode(list: List<BlockedArtist>): String {
        return Json.encodeToString(list)
    }
    
    fun isBlocked(artistId: String?, json: String?, currentTime: Long = System.currentTimeMillis()): Boolean {
        if (artistId == null) return false
        val list = parse(json)
        val artist = list.find { it.id == artistId } ?: return false
        return artist.blockExpiryTime == 0L || artist.blockExpiryTime > currentTime
    }
}
