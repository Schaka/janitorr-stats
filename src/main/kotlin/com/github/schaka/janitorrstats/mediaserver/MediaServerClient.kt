package com.github.schaka.janitorrstats.mediaserver

/**
 * Abstraction over a media server's API for retrieving play sessions and resolving item metadata.
 * Implementations exist for Jellyfin; Emby can be added by implementing this interface.
 */
interface MediaServerClient {

    /**
     * Retrieves all currently active or recently ended play sessions.
     */
    fun getActiveSessions(): List<MediaServerSession>

    /**
     * Resolves a media server item by its internal ID, returning external IDs and metadata.
     * [userId] is forwarded to the media server for authorization (required by Jellyfin 10.9+).
     * Returns null if the item cannot be found or has no external IDs.
     */
    fun getItemById(itemId: String, userId: String): ResolvedMediaItem?

    /**
     * Resolves a series by its internal ID, returning series-level external IDs.
     * [userId] is forwarded to the media server for authorization (required by Jellyfin 10.9+).
     * Used when a session references an episode — the series must be resolved separately.
     */
    fun getSeriesById(seriesId: String, userId: String): ResolvedMediaItem?
}

/**
 * Represents an active or recently ended play session from the media server.
 */
data class MediaServerSession(
    val userId: String,
    val username: String,
    val itemId: String,
    val itemName: String,
    val itemType: String,
    val seriesId: String?,
    val seriesName: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val runtimeTicks: Long?,
    val positionTicks: Long?,
    val isPaused: Boolean
)

/**
 * A media item resolved to its external IDs and basic metadata.
 */
data class ResolvedMediaItem(
    val itemId: String,
    val title: String,
    val year: Int?,
    val type: String,
    val imdbId: String?,
    val tmdbId: String?,
    val tvdbId: String?,
    val seriesId: String?,
    val jellyfinSeasonId: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?
)
