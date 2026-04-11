package com.github.schaka.janitorrstats.persistence.repository

import com.github.schaka.janitorrstats.persistence.entity.PlayEvent
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/**
 * Repository for [PlayEvent] persistence operations.
 */
@ApplicationScoped
class PlayEventRepository : PanacheRepositoryBase<PlayEvent, UUID> {

    /**
     * Finds play events for a media item matching the given external IDs.
     * All non-null ID parameters are ANDed. Results are ordered by playedAt descending.
     * The user association is eagerly fetched to avoid N+1 queries during response mapping.
     */
    fun findByExternalIds(
        imdbId: String?,
        tmdbId: String?,
        tvdbId: String?,
        seasonNumber: Int?,
        episodeNumber: Int?
    ): List<PlayEvent> {
        val conditions = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()

        if (imdbId != null) {
            conditions.add("pe.mediaItem.imdbId = :imdbId")
            params["imdbId"] = imdbId
        }
        if (tmdbId != null) {
            conditions.add("pe.mediaItem.tmdbId = :tmdbId")
            params["tmdbId"] = tmdbId
        }
        if (tvdbId != null) {
            conditions.add("pe.mediaItem.tvdbId = :tvdbId")
            params["tvdbId"] = tvdbId
        }
        if (seasonNumber != null) {
            conditions.add("pe.seasonNumber = :seasonNumber")
            params["seasonNumber"] = seasonNumber
        }
        if (episodeNumber != null) {
            conditions.add("pe.episodeNumber = :episodeNumber")
            params["episodeNumber"] = episodeNumber
        }

        if (conditions.isEmpty()) return emptyList()

        return find(
            "SELECT pe FROM PlayEvent pe JOIN FETCH pe.user WHERE ${conditions.joinToString(" AND ")} ORDER BY pe.playedAt DESC",
            params
        ).list()
    }

    /**
     * Returns the most recent play event for a given user, media item, and optional episode coordinates.
     * Used to detect and update an in-progress session rather than creating duplicate events.
     */
    fun findLatestForSession(userId: UUID, mediaItemId: UUID, seasonNumber: Int?, episodeNumber: Int?): PlayEvent? {
        val conditions = mutableListOf(
            "pe.user.id = :userId",
            "pe.mediaItem.id = :mediaItemId"
        )
        val params = mutableMapOf<String, Any>(
            "userId" to userId,
            "mediaItemId" to mediaItemId
        )

        if (seasonNumber != null) {
            conditions.add("pe.seasonNumber = :seasonNumber")
            params["seasonNumber"] = seasonNumber
        } else {
            conditions.add("pe.seasonNumber IS NULL")
        }

        if (episodeNumber != null) {
            conditions.add("pe.episodeNumber = :episodeNumber")
            params["episodeNumber"] = episodeNumber
        } else {
            conditions.add("pe.episodeNumber IS NULL")
        }

        return find(
            "SELECT pe FROM PlayEvent pe JOIN FETCH pe.user WHERE ${conditions.joinToString(" AND ")} ORDER BY pe.playedAt DESC",
            params
        ).firstResult()
    }
}
