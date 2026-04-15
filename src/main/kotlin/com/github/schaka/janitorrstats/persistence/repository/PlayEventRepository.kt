package com.github.schaka.janitorrstats.persistence.repository

import com.github.schaka.janitorrstats.persistence.entity.MediaType
import com.github.schaka.janitorrstats.persistence.entity.PlayEvent
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import io.quarkus.panache.common.Page
import jakarta.enterprise.context.ApplicationScoped
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Repository for [PlayEvent] persistence operations.
 */
@ApplicationScoped
class PlayEventRepository : PanacheRepositoryBase<PlayEvent, UUID> {

    /**
     * Finds a page of play events for a media item matching the given external IDs and media type.
     * All non-null ID parameters are ANDed. Results are ordered by playedAt descending.
     * The user association is eagerly fetched to avoid N+1 queries during response mapping.
     */
    fun findByExternalIds(
        mediaType: MediaType,
        imdbId: String?,
        tmdbId: String?,
        tvdbId: String?,
        seasonNumber: Int?,
        episodeNumber: Int?,
        page: Page
    ): Pair<List<PlayEvent>, Long> {
        val conditions = mutableListOf("pe.mediaItem.mediaType = :mediaType")
        val params = mutableMapOf<String, Any>("mediaType" to mediaType)

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

        val whereClause = conditions.joinToString(" AND ")
        val query = find(
            "SELECT pe FROM PlayEvent pe JOIN FETCH pe.user WHERE $whereClause ORDER BY pe.playedAt DESC",
            params
        )
        val total = find("SELECT pe FROM PlayEvent pe WHERE $whereClause", params).count()
        val items = query.page(page).list()

        return items to total
    }

    /**
     * Returns the most recent play event for a given user, media item, and optional episode coordinates,
     * limited to events recorded within the last 24 hours.
     *
     * The time bound ensures that a re-watch of previously completed content produces a new event
     * rather than updating a stale one, which matters for Janitorr's "recently watched" determination.
     */
    fun findLatestForSession(userId: UUID, mediaItemId: UUID, seasonNumber: Int?, episodeNumber: Int?): PlayEvent? {
        val since = Instant.now().minus(24, ChronoUnit.HOURS)
        val conditions = mutableListOf(
            "pe.user.id = :userId",
            "pe.mediaItem.id = :mediaItemId",
            "pe.playedAt >= :since"
        )
        val params = mutableMapOf<String, Any>(
            "userId" to userId,
            "mediaItemId" to mediaItemId,
            "since" to since
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
