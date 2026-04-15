package com.github.schaka.janitorrstats.polling

import com.github.schaka.janitorrstats.mediaserver.MediaServerSession
import com.github.schaka.janitorrstats.mediaserver.ResolvedMediaItem
import com.github.schaka.janitorrstats.persistence.entity.MediaItem
import com.github.schaka.janitorrstats.persistence.entity.MediaType
import com.github.schaka.janitorrstats.persistence.entity.PlayEvent
import com.github.schaka.janitorrstats.persistence.entity.Season
import com.github.schaka.janitorrstats.persistence.entity.User
import com.github.schaka.janitorrstats.persistence.repository.MediaItemRepository
import com.github.schaka.janitorrstats.persistence.repository.PlayEventRepository
import com.github.schaka.janitorrstats.persistence.repository.SeasonRepository
import com.github.schaka.janitorrstats.persistence.repository.UserRepository
import io.quarkus.logging.Log
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.Instant

/**
 * Handles all database writes for a single resolved play session.
 * Each call to [persistSession] runs in its own transaction so that a failure for one session
 * does not roll back work already done for other sessions in the same poll cycle.
 */
@ApplicationScoped
class SessionPersistenceService(
    private val mediaItemRepository: MediaItemRepository,
    private val seasonRepository: SeasonRepository,
    private val userRepository: UserRepository,
    private val playEventRepository: PlayEventRepository
) {

    companion object {
        private const val TICKS_PER_MS = 10_000L
        private const val COMPLETION_THRESHOLD = 90
    }

    @Transactional
    fun persistSession(
        session: MediaServerSession,
        resolvedItem: ResolvedMediaItem,
        resolvedEpisode: ResolvedMediaItem?,
        resolvedSeason: ResolvedMediaItem?
    ) {
        val isEpisode = session.itemType == "Episode"

        val user = upsertUser(session.userId, session.username)
        val mediaItem = upsertMediaItem(resolvedItem, isEpisode)

        if (isEpisode && session.seasonNumber != null) {
            upsertSeason(mediaItem, session.seasonNumber, resolvedSeason, resolvedEpisode?.jellyfinSeasonId)
        }

        val itemRuntimeTicks = if (isEpisode) resolvedEpisode?.runTimeTicks else resolvedItem.runTimeTicks
        val durationMs = (session.runtimeTicks ?: itemRuntimeTicks)?.let { it / TICKS_PER_MS } ?: 0L
        val positionMs = session.positionTicks?.let { it / TICKS_PER_MS } ?: 0L
        val percentComplete = if (durationMs > 0) ((positionMs * 100) / durationMs).toInt().coerceIn(0, 100) else 0

        recordPlayEvent(
            user = user,
            mediaItem = mediaItem,
            seasonNumber = if (isEpisode) session.seasonNumber else null,
            episodeNumber = if (isEpisode) session.episodeNumber else null,
            durationMs = durationMs,
            positionMs = positionMs,
            percentComplete = percentComplete
        )
    }

    private fun upsertUser(jellyfinUserId: String, username: String): User {
        val existing = userRepository.findByJellyfinUserId(jellyfinUserId)
        if (existing != null) {
            if (existing.username != username) {
                existing.username = username
            }
            return existing
        }

        val user = User().apply {
            this.jellyfinUserId = jellyfinUserId
            this.username = username
        }
        userRepository.persist(user)
        return user
    }

    private fun upsertMediaItem(resolved: ResolvedMediaItem, isEpisode: Boolean): MediaItem {
        val existing = mediaItemRepository.findByExternalIds(resolved.imdbId, resolved.tmdbId, resolved.tvdbId)
        if (existing != null) {
            existing.jellyfinItemId = resolved.itemId
            if (resolved.title.isNotBlank()) existing.title = resolved.title
            if (resolved.year != null) existing.year = resolved.year
            return existing
        }

        val mediaItem = MediaItem().apply {
            this.mediaType = if (isEpisode) MediaType.SERIES else MediaType.MOVIE
            this.title = resolved.title
            this.year = resolved.year
            this.imdbId = resolved.imdbId
            this.tmdbId = resolved.tmdbId
            this.tvdbId = resolved.tvdbId
            this.jellyfinItemId = resolved.itemId
        }
        mediaItemRepository.persist(mediaItem)
        return mediaItem
    }

    private fun upsertSeason(
        mediaItem: MediaItem,
        seasonNumber: Int,
        resolvedSeason: ResolvedMediaItem?,
        jellyfinSeasonId: String?
    ) {
        val existing = seasonRepository.findByMediaItemAndNumber(mediaItem.id, seasonNumber)
        if (existing != null) {
            if (existing.jellyfinSeasonId == null && jellyfinSeasonId != null) existing.jellyfinSeasonId = jellyfinSeasonId
            if (existing.tmdbSeasonId == null && resolvedSeason?.tmdbId != null) existing.tmdbSeasonId = resolvedSeason.tmdbId
            if (existing.tvdbSeasonId == null && resolvedSeason?.tvdbId != null) existing.tvdbSeasonId = resolvedSeason.tvdbId
            return
        }

        val season = Season().apply {
            this.mediaItem = mediaItem
            this.seasonNumber = seasonNumber
            this.jellyfinSeasonId = jellyfinSeasonId
            this.tmdbSeasonId = resolvedSeason?.tmdbId
            this.tvdbSeasonId = resolvedSeason?.tvdbId
        }
        seasonRepository.persist(season)
    }

    private fun recordPlayEvent(
        user: User,
        mediaItem: MediaItem,
        seasonNumber: Int?,
        episodeNumber: Int?,
        durationMs: Long,
        positionMs: Long,
        percentComplete: Int
    ) {
        val existing = playEventRepository.findLatestForSession(user.id, mediaItem.id, seasonNumber, episodeNumber)

        if (existing != null) {
            if (positionMs > existing.positionMs) {
                existing.positionMs = positionMs
                existing.percentComplete = percentComplete
                existing.completed = percentComplete >= COMPLETION_THRESHOLD
                existing.playedAt = Instant.now()
                Log.debug("Updated play event for ${user.username} - ${mediaItem.title} (${percentComplete}%)")
            }
            return
        }

        val event = PlayEvent().apply {
            this.user = user
            this.mediaItem = mediaItem
            this.seasonNumber = seasonNumber
            this.episodeNumber = episodeNumber
            this.playedAt = Instant.now()
            this.durationMs = durationMs
            this.positionMs = positionMs
            this.percentComplete = percentComplete
            this.completed = percentComplete >= COMPLETION_THRESHOLD
        }
        playEventRepository.persist(event)

        val episodeInfo = if (seasonNumber != null) " S${seasonNumber}E${episodeNumber}" else ""
        Log.info("Recorded play event for ${user.username} - ${mediaItem.title}${episodeInfo} (${percentComplete}%)")
    }
}
