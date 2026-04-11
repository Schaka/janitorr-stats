package com.github.schaka.janitorrstats.api

import java.time.Instant

/**
 * Response model for a play event returned by the history API.
 * Contains no Jellyfin internal IDs — only user-facing data.
 */
data class PlayEventResponse(
    val userId: String,
    val username: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val playedAt: Instant,
    val percentComplete: Int,
    val completed: Boolean,
    val durationMs: Long,
    val positionMs: Long
)
