package com.github.schaka.janitorrstats.polling

import com.github.schaka.janitorrstats.mediaserver.MediaServerSession
import com.github.schaka.janitorrstats.mediaserver.ResolvedMediaItem
import com.github.schaka.janitorrstats.persistence.entity.MediaType
import com.github.schaka.janitorrstats.persistence.repository.MediaItemRepository
import com.github.schaka.janitorrstats.persistence.repository.PlayEventRepository
import com.github.schaka.janitorrstats.persistence.repository.SeasonRepository
import com.github.schaka.janitorrstats.persistence.repository.UserRepository
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Verifies the upsert-and-record behaviour of [SessionPersistenceService] against a real
 * database provided by Quarkus dev services. Media items are keyed on external IDs (never Jellyfin internal IDs),
 * episode sessions create a corresponding season row, and ticks are converted to milliseconds.
 */
@QuarkusTest
class SessionPersistenceServiceTest {

    @Inject lateinit var service: SessionPersistenceService
    @Inject lateinit var mediaItemRepository: MediaItemRepository
    @Inject lateinit var seasonRepository: SeasonRepository
    @Inject lateinit var userRepository: UserRepository
    @Inject lateinit var playEventRepository: PlayEventRepository

    private fun wipe() {
        QuarkusTransaction.requiringNew().run {
            playEventRepository.deleteAll()
            seasonRepository.deleteAll()
            mediaItemRepository.deleteAll()
            userRepository.deleteAll()
        }
    }

    @BeforeEach fun before() = wipe()
    @AfterEach fun after() = wipe()

    private fun movieSession(userId: String = "juser-1", itemId: String = "jf-1", positionTicks: Long = 3_000_000_000L) =
        MediaServerSession(
            userId = userId, username = "alice", itemId = itemId, itemName = "A Movie", itemType = "Movie",
            seriesId = null, seriesName = null, seasonNumber = null, episodeNumber = null,
            runtimeTicks = 10_000_000_000L, positionTicks = positionTicks, isPaused = false
        )

    private fun movieResolved(itemId: String = "jf-1", imdb: String = "tt0000001", year: Int = 2001) =
        ResolvedMediaItem(
            itemId = itemId, title = "A Movie", year = year, type = "Movie",
            imdbId = imdb, tmdbId = null, tvdbId = null,
            seriesId = null, jellyfinSeasonId = null, seasonNumber = null, episodeNumber = null
        )

    private inline fun inTx(crossinline body: () -> Unit) =
        QuarkusTransaction.requiringNew().run { body() }

    @Test
    fun `persists a movie session end-to-end`() = inTx {
        service.persistSession(movieSession(), movieResolved(), null, null)

        val user = userRepository.findByJellyfinUserId("juser-1")
        assertNotNull(user)
        assertEquals("alice", user!!.username)

        val media = mediaItemRepository.findByExternalIds("tt0000001", null, null)
        assertNotNull(media)
        assertEquals(MediaType.MOVIE, media!!.mediaType)
        assertEquals("A Movie", media.title)
        assertEquals("jf-1", media.jellyfinItemId)

        assertEquals(1, playEventRepository.count())
        val event = playEventRepository.listAll().single()
        // Ticks → ms: 3_000_000_000 / 10_000 = 300_000 ms; runtime 10_000_000_000 / 10_000 = 1_000_000 ms.
        assertEquals(300_000L, event.positionMs)
        assertEquals(1_000_000L, event.durationMs)
        assertEquals(30, event.percentComplete)
        assertFalse(event.completed)
    }

    @Test
    fun `reuses existing media item when external IDs match and a new Jellyfin item ID is seen`() = inTx {
        service.persistSession(movieSession(itemId = "jf-old"), movieResolved(itemId = "jf-old"), null, null)
        service.persistSession(movieSession(itemId = "jf-new"), movieResolved(itemId = "jf-new"), null, null)

        // Only one MediaItem — matched by external ID regardless of the fresh Jellyfin internal ID.
        assertEquals(1, mediaItemRepository.count())
        val media = mediaItemRepository.findByExternalIds("tt0000001", null, null)!!
        assertEquals("jf-new", media.jellyfinItemId)
    }

    @Test
    fun `marks an event as completed when progress crosses 90 percent`() = inTx {
        // positionTicks = 9_500_000_000 → 950_000 ms of 1_000_000 ms runtime → 95%.
        service.persistSession(
            movieSession(positionTicks = 9_500_000_000L),
            movieResolved(),
            null,
            null
        )
        val event = playEventRepository.listAll().single()
        assertEquals(95, event.percentComplete)
        assertTrue(event.completed)
    }

    @Test
    fun `updates an in-progress event rather than creating a duplicate for the same session`() = inTx {
        service.persistSession(movieSession(positionTicks = 1_000_000_000L), movieResolved(), null, null)
        service.persistSession(movieSession(positionTicks = 5_000_000_000L), movieResolved(), null, null)

        assertEquals(1, playEventRepository.count())
        val event = playEventRepository.listAll().single()
        assertEquals(500_000L, event.positionMs)
        assertEquals(50, event.percentComplete)
    }

    @Test
    fun `skips recording when positionMs is exactly zero`() = inTx {
        service.persistSession(movieSession(positionTicks = 0L), movieResolved(), null, null)
        assertEquals(0, playEventRepository.count())
    }

    @Test
    fun `allows updating an event to a lower position when user jumps back`() = inTx {
        service.persistSession(movieSession(positionTicks = 5_000_000_000L), movieResolved(), null, null)
        service.persistSession(movieSession(positionTicks = 1_000_000_000L), movieResolved(), null, null)

        assertEquals(1, playEventRepository.count())
        val event = playEventRepository.listAll().single()
        assertEquals(100_000L, event.positionMs)
        assertEquals(10, event.percentComplete)
    }

    @Test
    fun `persists an episode session with its season row`() = inTx {
        val episodeSession = MediaServerSession(
            userId = "juser-2", username = "bob", itemId = "jf-ep-1", itemName = "Pilot", itemType = "Episode",
            seriesId = "jf-series-1", seriesName = "Breaking Bad",
            seasonNumber = 1, episodeNumber = 1,
            runtimeTicks = 20_000_000_000L, positionTicks = 18_000_000_000L, isPaused = false
        )
        val resolvedSeries = ResolvedMediaItem(
            itemId = "jf-series-1", title = "Breaking Bad", year = 2008, type = "Series",
            imdbId = null, tmdbId = null, tvdbId = "81189",
            seriesId = null, jellyfinSeasonId = null, seasonNumber = null, episodeNumber = null
        )
        val resolvedEpisode = ResolvedMediaItem(
            itemId = "jf-ep-1", title = "Pilot", year = 2008, type = "Episode",
            imdbId = null, tmdbId = null, tvdbId = null,
            seriesId = "jf-series-1", jellyfinSeasonId = "jf-season-1", seasonNumber = 1, episodeNumber = 1
        )
        val resolvedSeason = ResolvedMediaItem(
            itemId = "jf-season-1", title = "Season 1", year = 2008, type = "Season",
            imdbId = null, tmdbId = "3572", tvdbId = null,
            seriesId = "jf-series-1", jellyfinSeasonId = null, seasonNumber = 1, episodeNumber = null
        )

        service.persistSession(episodeSession, resolvedSeries, resolvedEpisode, resolvedSeason)

        val media = mediaItemRepository.findByExternalIds(null, null, "81189")!!
        assertEquals(MediaType.SERIES, media.mediaType)

        val season = seasonRepository.findByMediaItemAndNumber(media.id, 1)
        assertNotNull(season)
        assertEquals("jf-season-1", season!!.jellyfinSeasonId)
        assertEquals("3572", season.tmdbSeasonId)

        val event = playEventRepository.listAll().single()
        assertEquals(1, event.seasonNumber)
        assertEquals(1, event.episodeNumber)
        assertEquals(90, event.percentComplete)
        assertTrue(event.completed)
    }
}
