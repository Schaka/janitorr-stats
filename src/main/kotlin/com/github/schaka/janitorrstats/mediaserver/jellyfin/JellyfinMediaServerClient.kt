package com.github.schaka.janitorrstats.mediaserver.jellyfin

import com.github.schaka.janitorrstats.mediaserver.MediaServerClient
import com.github.schaka.janitorrstats.mediaserver.MediaServerSession
import com.github.schaka.janitorrstats.mediaserver.ResolvedMediaItem
import io.quarkus.logging.Log
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.rest.client.inject.RestClient

/**
 * Jellyfin implementation of [MediaServerClient].
 * Translates Jellyfin API responses into the generic media server model.
 */
@ApplicationScoped
class JellyfinMediaServerClient(
    @RestClient private val api: JellyfinApiClient
) : MediaServerClient {

    override fun getActiveSessions(): List<MediaServerSession> {
        return try {
            api.getSessions()
                .filter { it.nowPlayingItem != null && it.userId != null }
                .filter { it.nowPlayingItem!!.type != "Trailer" }
                .map { session ->
                    val item = session.nowPlayingItem!!
                    MediaServerSession(
                        userId = session.userId!!,
                        username = session.userName ?: "unknown",
                        itemId = item.id,
                        itemName = item.name,
                        itemType = item.type,
                        seriesId = item.seriesId,
                        seriesName = item.seriesName,
                        seasonNumber = item.parentIndexNumber,
                        episodeNumber = item.indexNumber,
                        runtimeTicks = item.runTimeTicks,
                        positionTicks = session.playState?.positionTicks,
                        isPaused = session.playState?.isPaused ?: false
                    )
                }
        } catch (e: Exception) {
            Log.error("Failed to retrieve sessions from Jellyfin", e)
            emptyList()
        }
    }

    override fun getItemById(itemId: String, userId: String): ResolvedMediaItem? {
        return try {
            val item = api.getItem(itemId, "ProviderIds", userId)
            toResolvedMediaItem(item)
        } catch (e: Exception) {
            Log.error("Failed to resolve Jellyfin item $itemId", e)
            null
        }
    }

    override fun getSeriesById(seriesId: String, userId: String): ResolvedMediaItem? {
        return getItemById(seriesId, userId)
    }

    private fun toResolvedMediaItem(item: JellyfinItem): ResolvedMediaItem? {
        val providerIds = item.providerIds
        if (providerIds == null || (providerIds.imdb == null && providerIds.tmdb == null && providerIds.tvdb == null)) {
            Log.warn("Item ${item.id} (${item.name}) has no external provider IDs, skipping")
            return null
        }

        return ResolvedMediaItem(
            itemId = item.id,
            title = item.name,
            year = item.productionYear,
            type = item.type,
            imdbId = providerIds.imdb,
            tmdbId = providerIds.tmdb,
            tvdbId = providerIds.tvdb,
            seriesId = item.seriesId,
            jellyfinSeasonId = item.seasonId,
            seasonNumber = item.parentIndexNumber,
            episodeNumber = item.indexNumber,
            runTimeTicks = item.runTimeTicks
        )
    }
}
