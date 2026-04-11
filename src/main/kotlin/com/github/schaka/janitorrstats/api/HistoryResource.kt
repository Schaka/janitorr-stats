package com.github.schaka.janitorrstats.api

import com.github.schaka.janitorrstats.persistence.entity.PlayEvent
import com.github.schaka.janitorrstats.persistence.repository.PlayEventRepository
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

/**
 * REST resource for querying play history by external media IDs.
 */
@Path("/history")
@Produces(MediaType.APPLICATION_JSON)
class HistoryResource(
    private val playEventRepository: PlayEventRepository
) {

    /**
     * Returns play history for a movie identified by IMDB or TMDB ID.
     * At least one ID parameter is required.
     */
    @GET
    @Path("/movies")
    fun getMovieHistory(
        @QueryParam("imdbId") imdbId: String?,
        @QueryParam("tmdbId") tmdbId: String?
    ): Response {
        if (imdbId == null && tmdbId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to "At least one of imdbId or tmdbId is required"))
                .build()
        }

        val events = playEventRepository.findByExternalIds(
            imdbId = imdbId,
            tmdbId = tmdbId,
            tvdbId = null,
            seasonNumber = null,
            episodeNumber = null
        )

        return Response.ok(events.map { it.toResponse() }).build()
    }

    /**
     * Returns play history for a TV series, optionally filtered by season and episode.
     * At least one series ID parameter is required.
     */
    @GET
    @Path("/shows")
    fun getShowHistory(
        @QueryParam("imdbId") imdbId: String?,
        @QueryParam("tmdbId") tmdbId: String?,
        @QueryParam("tvdbId") tvdbId: String?,
        @QueryParam("season") season: Int?,
        @QueryParam("episode") episode: Int?
    ): Response {
        if (imdbId == null && tmdbId == null && tvdbId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to "At least one of imdbId, tmdbId, or tvdbId is required"))
                .build()
        }

        val events = playEventRepository.findByExternalIds(
            imdbId = imdbId,
            tmdbId = tmdbId,
            tvdbId = tvdbId,
            seasonNumber = season,
            episodeNumber = episode
        )

        return Response.ok(events.map { it.toResponse() }).build()
    }

    private fun PlayEvent.toResponse() = PlayEventResponse(
        userId = user.id,
        username = user.username,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        playedAt = playedAt,
        percentComplete = percentComplete,
        completed = completed,
        durationMs = durationMs,
        positionMs = positionMs
    )
}
