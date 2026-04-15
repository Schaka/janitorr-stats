package com.github.schaka.janitorrstats.api

import com.github.schaka.janitorrstats.persistence.entity.MediaType
import com.github.schaka.janitorrstats.persistence.entity.PlayEvent
import com.github.schaka.janitorrstats.persistence.repository.PlayEventRepository
import io.quarkus.panache.common.Page
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.Response

/**
 * REST resource for querying play history by external media IDs.
 */
@Path("/history")
@Produces("application/json")
class HistoryResource(
    private val playEventRepository: PlayEventRepository
) {

    /**
     * Returns a page of play history for a movie identified by IMDB or TMDB ID.
     * At least one ID parameter is required.
     */
    @GET
    @Path("/movies")
    fun getMovieHistory(
        @QueryParam("imdbId") imdbId: String?,
        @QueryParam("tmdbId") tmdbId: String?,
        @QueryParam("page") @DefaultValue("0") page: Int,
        @QueryParam("size") @DefaultValue("100") pageSize: Int
    ): Response {
        if (imdbId == null && tmdbId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to "At least one of imdbId or tmdbId is required"))
                .build()
        }

        val panachePage = Page.of(page, pageSize)
        val (events, total) = playEventRepository.findByExternalIds(
            mediaType = MediaType.MOVIE,
            imdbId = imdbId,
            tmdbId = tmdbId,
            tvdbId = null,
            seasonNumber = null,
            episodeNumber = null,
            page = panachePage
        )

        return Response.ok(PagedResponse.of(events.map { it.toResponse() }, page, pageSize, total)).build()
    }

    /**
     * Returns a page of play history for a TV series, optionally filtered by season and episode.
     * At least one series ID parameter is required.
     */
    @GET
    @Path("/shows")
    fun getShowHistory(
        @QueryParam("imdbId") imdbId: String?,
        @QueryParam("tmdbId") tmdbId: String?,
        @QueryParam("tvdbId") tvdbId: String?,
        @QueryParam("season") season: Int?,
        @QueryParam("episode") episode: Int?,
        @QueryParam("page") @DefaultValue("0") page: Int,
        @QueryParam("size") @DefaultValue("100") pageSize: Int
    ): Response {
        if (imdbId == null && tmdbId == null && tvdbId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to "At least one of imdbId, tmdbId, or tvdbId is required"))
                .build()
        }

        val panachePage = Page.of(page, pageSize)
        val (events, total) = playEventRepository.findByExternalIds(
            mediaType = MediaType.SERIES,
            imdbId = imdbId,
            tmdbId = tmdbId,
            tvdbId = tvdbId,
            seasonNumber = season,
            episodeNumber = episode,
            page = panachePage
        )

        return Response.ok(PagedResponse.of(events.map { it.toResponse() }, page, pageSize, total)).build()
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
