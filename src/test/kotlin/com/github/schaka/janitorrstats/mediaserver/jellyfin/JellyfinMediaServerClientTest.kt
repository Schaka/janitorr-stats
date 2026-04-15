package com.github.schaka.janitorrstats.mediaserver.jellyfin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure unit tests for [JellyfinMediaServerClient] — exercises the mapping from Jellyfin DTOs
 * into the internal [com.github.schaka.janitorrstats.mediaserver.MediaServerSession] /
 * [com.github.schaka.janitorrstats.mediaserver.ResolvedMediaItem] shapes without bootstrapping
 * Quarkus or calling a live Jellyfin instance.
 */
class JellyfinMediaServerClientTest {

    private class FakeJellyfinApiClient(
        private val stubbedSessions: List<JellyfinSession> = emptyList(),
        private val stubbedItems: Map<String, JellyfinItem> = emptyMap()
    ) : JellyfinApiClient {
        override fun getSessions(): List<JellyfinSession> = stubbedSessions
        override fun getItem(itemId: String, fields: String, userId: String): JellyfinItem =
            stubbedItems[itemId] ?: error("no item $itemId")
        override fun getItems(parentId: String, fields: String, recursive: Boolean, limit: Int) = JellyfinItemPage()
        override fun getSeasons(seriesId: String, fields: String) = JellyfinItemPage()
        override fun getUsers() = emptyList<JellyfinUser>()
    }

    @Test
    fun `getActiveSessions filters trailers and sessions missing user or item`() {
        val fake = FakeJellyfinApiClient(
            stubbedSessions = listOf(
                JellyfinSession(userId = "u1", userName = "alice",
                    nowPlayingItem = JellyfinNowPlayingItem(id = "movie-1", name = "A Movie", type = "Movie")),
                JellyfinSession(userId = "u2", userName = "bob",
                    nowPlayingItem = JellyfinNowPlayingItem(id = "trl-1", name = "Trailer", type = "Trailer")),
                JellyfinSession(userId = null, userName = "ghost",
                    nowPlayingItem = JellyfinNowPlayingItem(id = "movie-2", name = "Ghost", type = "Movie")),
                JellyfinSession(userId = "u3", userName = "idle", nowPlayingItem = null)
            )
        )
        val client = JellyfinMediaServerClient(fake)

        val result = client.getActiveSessions()

        assertEquals(1, result.size)
        assertEquals("u1", result[0].userId)
        assertEquals("movie-1", result[0].itemId)
    }

    @Test
    fun `getActiveSessions carries play state fields through to model`() {
        val fake = FakeJellyfinApiClient(
            stubbedSessions = listOf(
                JellyfinSession(
                    userId = "u1", userName = "alice",
                    nowPlayingItem = JellyfinNowPlayingItem(
                        id = "ep-1", name = "Pilot", type = "Episode",
                        seriesId = "series-1", seriesName = "Breaking Bad",
                        parentIndexNumber = 1, indexNumber = 1,
                        runTimeTicks = 10_000_000_000L
                    ),
                    playState = JellyfinPlayState(positionTicks = 3_000_000_000L, isPaused = true)
                )
            )
        )

        val session = JellyfinMediaServerClient(fake).getActiveSessions().single()

        assertEquals("series-1", session.seriesId)
        assertEquals(1, session.seasonNumber)
        assertEquals(1, session.episodeNumber)
        assertEquals(10_000_000_000L, session.runtimeTicks)
        assertEquals(3_000_000_000L, session.positionTicks)
        assertTrue(session.isPaused)
    }

    @Test
    fun `getItemById returns null when provider IDs are absent`() {
        val fake = FakeJellyfinApiClient(
            stubbedItems = mapOf(
                "no-ids" to JellyfinItem(id = "no-ids", name = "Orphan", type = "Movie", providerIds = null),
                "empty-ids" to JellyfinItem(id = "empty-ids", name = "Empty", type = "Movie",
                    providerIds = JellyfinProviderIds())
            )
        )
        val client = JellyfinMediaServerClient(fake)

        assertNull(client.getItemById("no-ids", "u1"))
        assertNull(client.getItemById("empty-ids", "u1"))
    }

    @Test
    fun `getItemById maps external IDs and metadata`() {
        val fake = FakeJellyfinApiClient(
            stubbedItems = mapOf(
                "movie-1" to JellyfinItem(
                    id = "movie-1", name = "Star Wars", type = "Movie", productionYear = 1977,
                    providerIds = JellyfinProviderIds(imdb = "tt0076759", tmdb = "11")
                )
            )
        )

        val resolved = JellyfinMediaServerClient(fake).getItemById("movie-1", "u1")!!

        assertEquals("Star Wars", resolved.title)
        assertEquals(1977, resolved.year)
        assertEquals("tt0076759", resolved.imdbId)
        assertEquals("11", resolved.tmdbId)
        assertNull(resolved.tvdbId)
    }

    @Test
    fun `getItemById on an episode preserves series and season references`() {
        val fake = FakeJellyfinApiClient(
            stubbedItems = mapOf(
                "ep-1" to JellyfinItem(
                    id = "ep-1", name = "Pilot", type = "Episode",
                    seriesId = "series-1", seasonId = "season-1",
                    parentIndexNumber = 1, indexNumber = 1,
                    providerIds = JellyfinProviderIds(tvdb = "349232")
                )
            )
        )

        val resolved = JellyfinMediaServerClient(fake).getItemById("ep-1", "u1")!!

        assertEquals("series-1", resolved.seriesId)
        assertEquals("season-1", resolved.jellyfinSeasonId)
        assertEquals(1, resolved.seasonNumber)
        assertEquals(1, resolved.episodeNumber)
        assertEquals("349232", resolved.tvdbId)
    }

    @Test
    fun `getActiveSessions returns empty when API throws`() {
        val boomClient = object : JellyfinApiClient {
            override fun getSessions(): List<JellyfinSession> = throw RuntimeException("jellyfin down")
            override fun getItem(itemId: String, fields: String, userId: String) = JellyfinItem()
            override fun getItems(parentId: String, fields: String, recursive: Boolean, limit: Int) = JellyfinItemPage()
            override fun getSeasons(seriesId: String, fields: String) = JellyfinItemPage()
            override fun getUsers() = emptyList<JellyfinUser>()
        }
        assertTrue(JellyfinMediaServerClient(boomClient).getActiveSessions().isEmpty())
    }
}
