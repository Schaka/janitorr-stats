package com.github.schaka.janitorrstats.polling

import com.github.schaka.janitorrstats.mediaserver.MediaServerClient
import com.github.schaka.janitorrstats.mediaserver.MediaServerSession
import io.quarkus.logging.Log
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped

/**
 * Polls the media server for active play sessions on a configurable interval.
 * Each session is resolved to external IDs and recorded as a play event.
 *
 * HTTP resolution and database persistence are deliberately separated: all network calls happen
 * here (outside any transaction) so that database connections are not held while waiting on
 * Jellyfin responses. [SessionPersistenceService] handles each session in its own transaction,
 * isolating failures so that a single bad session cannot roll back the rest of the poll cycle.
 */
@ApplicationScoped
class SessionPoller(
    private val mediaServerClient: MediaServerClient,
    private val sessionPersistenceService: SessionPersistenceService
) {

    /**
     * Scheduled poll that retrieves active sessions and records play events.
     * The interval is configured via `jellyfin.poll-interval`.
     */
    @Scheduled(every = "\${jellyfin.poll-interval}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    fun poll() {
        Log.debug("Polling for active sessions")

        val sessions = mediaServerClient.getActiveSessions()
        if (sessions.isEmpty()) {
            Log.debug("No active sessions found")
            return
        }

        Log.debug("Found ${sessions.size} active session(s)")

        for (session in sessions) {
            try {
                processSession(session)
            } catch (e: Exception) {
                Log.error("Failed to process session for user ${session.username}, item ${session.itemName}", e)
            }
        }
    }

    private fun processSession(session: MediaServerSession) {
        val isEpisode = session.itemType == "Episode"

        val resolvedSeries = if (isEpisode && session.seriesId != null) {
            mediaServerClient.getSeriesById(session.seriesId, session.userId)
        } else {
            mediaServerClient.getItemById(session.itemId, session.userId)
        }

        if (resolvedSeries == null) {
            Log.debug("Could not resolve item ${session.itemId} (${session.itemName}) to external IDs, skipping")
            return
        }

        val resolvedEpisode = if (isEpisode) mediaServerClient.getItemById(session.itemId, session.userId) else null
        val resolvedSeason = resolvedEpisode?.jellyfinSeasonId?.let { mediaServerClient.getItemById(it, session.userId) }

        sessionPersistenceService.persistSession(session, resolvedSeries, resolvedEpisode, resolvedSeason)
    }
}
