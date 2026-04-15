package com.github.schaka.janitorrstats.api

import com.github.schaka.janitorrstats.persistence.entity.MediaItem
import com.github.schaka.janitorrstats.persistence.entity.MediaType
import com.github.schaka.janitorrstats.persistence.entity.PlayEvent
import com.github.schaka.janitorrstats.persistence.entity.User
import com.github.schaka.janitorrstats.persistence.repository.MediaItemRepository
import com.github.schaka.janitorrstats.persistence.repository.PlayEventRepository
import com.github.schaka.janitorrstats.persistence.repository.UserRepository
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Exercises [HistoryResource] against a real Quarkus stack: REST serialisation, Hibernate
 * queries, pagination, and the combined filtering logic in [PlayEventRepository].
 *
 * Seed data is committed via [QuarkusTransaction] so the REST endpoint (which runs on a
 * separate connection) can see it, then wiped in [cleanup] after each test.
 */
@QuarkusTest
class HistoryResourceTest {

    @Inject lateinit var mediaItemRepository: MediaItemRepository
    @Inject lateinit var userRepository: UserRepository
    @Inject lateinit var playEventRepository: PlayEventRepository

    @AfterEach
    fun cleanup() {
        QuarkusTransaction.requiringNew().run {
            playEventRepository.deleteAll()
            mediaItemRepository.deleteAll()
            userRepository.deleteAll()
        }
    }

    private fun seed(block: SeedContext.() -> Unit) {
        QuarkusTransaction.requiringNew().run {
            SeedContext().block()
        }
    }

    private inner class SeedContext {
        fun user(jellyfinId: String, name: String): User {
            val u = User().apply { jellyfinUserId = jellyfinId; username = name }
            userRepository.persist(u)
            return u
        }

        fun movie(imdb: String?, tmdb: String?, title: String): MediaItem {
            val m = MediaItem().apply {
                mediaType = MediaType.MOVIE; this.title = title; year = 2001
                imdbId = imdb; tmdbId = tmdb
            }
            mediaItemRepository.persist(m)
            return m
        }

        fun series(tvdb: String, title: String): MediaItem {
            val m = MediaItem().apply {
                mediaType = MediaType.SERIES; this.title = title; year = 2008; tvdbId = tvdb
            }
            mediaItemRepository.persist(m)
            return m
        }

        fun event(
            user: User, item: MediaItem,
            season: Int? = null, episode: Int? = null,
            percent: Int = 50
        ) {
            val e = PlayEvent().apply {
                this.user = user
                this.mediaItem = item
                this.seasonNumber = season
                this.episodeNumber = episode
                this.playedAt = Instant.now()
                this.durationMs = 1_000_000
                this.positionMs = (percent * 10_000).toLong()
                this.percentComplete = percent
                this.completed = percent >= 90
            }
            playEventRepository.persist(e)
        }
    }

    @Test
    fun `returns 400 when no movie IDs are supplied`() {
        given().`when`().get("/history/movies")
            .then().statusCode(400)
    }

    @Test
    fun `returns 400 when no show IDs are supplied`() {
        given().`when`().get("/history/shows")
            .then().statusCode(400)
    }

    @Test
    fun `returns movie events matching imdbId only`() {
        seed {
            val alice = user("u-alice", "alice")
            val target = movie(imdb = "tt0000001", tmdb = "100", title = "Target")
            val other = movie(imdb = "tt0000002", tmdb = "200", title = "Other")
            event(alice, target, percent = 42)
            event(alice, other, percent = 99)
        }

        given().queryParam("imdbId", "tt0000001")
            .`when`().get("/history/movies")
            .then()
            .statusCode(200)
            .body("content", hasSize<Any>(1))
            .body("totalItems", equalTo(1))
            .body("content[0].username", equalTo("alice"))
            .body("content[0].percentComplete", equalTo(42))
            .body("content[0].completed", equalTo(false))
    }

    @Test
    fun `ANDs multiple IDs so a mismatched pair returns nothing`() {
        seed {
            val alice = user("u-alice", "alice")
            val target = movie(imdb = "tt0000001", tmdb = "100", title = "Target")
            event(alice, target)
        }

        given().queryParam("imdbId", "tt0000001")
            .queryParam("tmdbId", "999")
            .`when`().get("/history/movies")
            .then()
            .statusCode(200)
            .body("content", hasSize<Any>(0))
            .body("totalItems", equalTo(0))
    }

    @Test
    fun `does not return movie rows on the shows endpoint`() {
        seed {
            val alice = user("u-alice", "alice")
            val m = movie(imdb = "tt0000001", tmdb = "100", title = "A Movie")
            event(alice, m)
        }

        given().queryParam("tvdbId", "whatever")
            .`when`().get("/history/shows")
            .then()
            .statusCode(200)
            .body("content", hasSize<Any>(0))
    }

    @Test
    fun `filters show events by season and episode`() {
        seed {
            val bob = user("u-bob", "bob")
            val s = series(tvdb = "81189", title = "Breaking Bad")
            event(bob, s, season = 1, episode = 1, percent = 30)
            event(bob, s, season = 1, episode = 2, percent = 80)
            event(bob, s, season = 2, episode = 1, percent = 95)
        }

        given().queryParam("tvdbId", "81189")
            .`when`().get("/history/shows")
            .then().statusCode(200).body("totalItems", equalTo(3))

        given().queryParam("tvdbId", "81189").queryParam("season", 1)
            .`when`().get("/history/shows")
            .then().statusCode(200).body("totalItems", equalTo(2))

        given().queryParam("tvdbId", "81189").queryParam("season", 1).queryParam("episode", 2)
            .`when`().get("/history/shows")
            .then().statusCode(200)
            .body("totalItems", equalTo(1))
            .body("content[0].episodeNumber", equalTo(2))
            .body("content[0].percentComplete", equalTo(80))
    }
}
