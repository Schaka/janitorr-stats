package com.github.schaka.janitorrstats.integration

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.schaka.janitorrstats.config.JellyfinConfig
import com.github.schaka.janitorrstats.persistence.repository.MediaItemRepository
import com.github.schaka.janitorrstats.persistence.repository.PlayEventRepository
import com.github.schaka.janitorrstats.persistence.repository.SeasonRepository
import com.github.schaka.janitorrstats.persistence.repository.UserRepository
import com.github.schaka.janitorrstats.polling.SessionPoller
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import jakarta.ws.rs.core.UriBuilder
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * End-to-end test driving a real Jellyfin container (started by the Jellyfin dev service),
 * a real Postgres container (started by the Quarkus datasource dev service) and the live
 * REST API. The test:
 *
 *   1. Waits for Jellyfin to finish indexing the seeded media library.
 *   2. Authenticates as the dev-service admin user to obtain a device session.
 *   3. Reports a playing item and progress via `/Sessions/Playing(/Progress)`.
 *   4. Triggers [SessionPoller.poll] directly (the scheduled cadence is 24h in tests).
 *   5. Queries `GET /history/movies` and asserts the event is returned, tied to the correct
 *      external ID and carrying the reported position.
 */
@QuarkusTest
class JellyfinPlaybackIntegrationTest {

    companion object {
        private const val ADMIN_USER = "admin"
        private const val ADMIN_PASS = "adminadmin"
        private const val DEVICE_ID = "janitorr-stats-itest-001"
        private const val AUTH_PARAMS =
            """Client="JanitorrStatsITest", Device="ITest", DeviceId="$DEVICE_ID", Version="1.0""""
        private const val STAR_WARS_IMDB = "tt0076759"
        private val mapper = ObjectMapper()
        private val http: HttpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build()

        private data class AuthRequest(
            @JsonProperty("Username") val username: String,
            @JsonProperty("Pw") val pw: String
        )

        private data class PlaybackStartRequest(
            @JsonProperty("ItemId") val itemId: String,
            @JsonProperty("PlaySessionId") val playSessionId: String,
            @JsonProperty("CanSeek") val canSeek: Boolean = true,
            @JsonProperty("PositionTicks") val positionTicks: Long = 0
        )

        private data class PlaybackProgressRequest(
            @JsonProperty("ItemId") val itemId: String,
            @JsonProperty("PlaySessionId") val playSessionId: String,
            @JsonProperty("PositionTicks") val positionTicks: Long,
            @JsonProperty("IsPaused") val isPaused: Boolean = false,
            @JsonProperty("CanSeek") val canSeek: Boolean = true
        )

        private data class PlaybackStopRequest(
            @JsonProperty("ItemId") val itemId: String,
            @JsonProperty("PlaySessionId") val playSessionId: String,
            @JsonProperty("PositionTicks") val positionTicks: Long
        )
    }

    @Inject lateinit var config: JellyfinConfig
    @Inject lateinit var sessionPoller: SessionPoller
    @Inject lateinit var playEventRepository: PlayEventRepository
    @Inject lateinit var seasonRepository: SeasonRepository
    @Inject lateinit var mediaItemRepository: MediaItemRepository
    @Inject lateinit var userRepository: UserRepository

    @AfterEach
    fun cleanup() {
        QuarkusTransaction.requiringNew().run {
            playEventRepository.deleteAll()
            seasonRepository.deleteAll()
            mediaItemRepository.deleteAll()
            userRepository.deleteAll()
        }
    }

    @Test
    fun `playback reported to Jellyfin is polled and exposed via history API`() {
        val baseUrl = config.baseUrl()
        val apiKey = config.apiKey()

        val (userId, userToken) = authenticate(baseUrl)
        val itemId = waitForMovie(baseUrl, apiKey, userId)

        val playSessionId = "itest-play-session-1"
        reportPlayback(baseUrl, userToken, itemId, playSessionId)
        try {
            // Position: 2 minutes into a ~9 minute clip — 1_200_000_000 ticks.
            reportProgress(baseUrl, userToken, itemId, playSessionId, positionTicks = 1_200_000_000L)

            waitForSessionToAppear(baseUrl, apiKey, itemId)

            sessionPoller.poll()

            given().queryParam("imdbId", STAR_WARS_IMDB)
                .`when`().get("/history/movies")
                .then()
                .statusCode(200)
                .body("totalItems", greaterThanOrEqualTo(1))
                .body("content[0].username", equalTo(ADMIN_USER))
                .body("content[0].positionMs", greaterThan(0))
                .body("content[0].durationMs", greaterThan(0))
        } finally {
            runCatching { reportStopped(baseUrl, userToken, itemId, playSessionId) }
        }
    }

    private fun authenticate(baseUrl: String): Pair<String, String> {
        val body = mapper.writeValueAsString(AuthRequest(ADMIN_USER, ADMIN_PASS))
        val (status, responseBody) = post(uri(baseUrl, "/Users/AuthenticateByName"), body, authHeader())
        check(status in 200..299) { "Admin authentication failed: HTTP $status — $responseBody" }

        val json = mapper.readTree(responseBody)
        val userId = json.path("User").path("Id").asText()
        val token = json.path("AccessToken").asText()
        check(userId.isNotBlank() && token.isNotBlank()) { "Unexpected auth response: $responseBody" }
        return userId to token
    }

    private fun waitForMovie(baseUrl: String, apiKey: String, userId: String): String {
        val itemsUri = UriBuilder.fromUri(baseUrl)
            .path("/Items")
            .queryParam("userId", userId)
            .queryParam("IncludeItemTypes", "Movie")
            .queryParam("Recursive", "true")
            .queryParam("SearchTerm", "Star Wars")
            .queryParam("Fields", "ProviderIds")
            .build()

        repeat(60) { attempt ->
            val (status, body) = get(itemsUri, authHeader(apiKey))
            if (status in 200..299) {
                val items = mapper.readTree(body).path("Items")
                val match = items.firstOrNull { it.matchesStarWars1977() }
                if (match != null) return match.path("Id").asText()
            }
            Thread.sleep(2_000)
            if (attempt > 0 && attempt % 15 == 0) {
                // Give Jellyfin another nudge in case the initial refresh finished before media was linked.
                post(uri(baseUrl, "/Library/Refresh"), "", authHeader(apiKey))
            }
        }
        error("Star Wars (1977) never appeared in Jellyfin library after 2 minutes")
    }

    private fun JsonNode.matchesStarWars1977(): Boolean {
        val imdb = path("ProviderIds").path("Imdb").asText("")
        if (imdb == STAR_WARS_IMDB) return true
        val name = path("Name").asText("")
        val year = path("ProductionYear").asInt(-1)
        return name.contains("Star Wars", ignoreCase = true) && year == 1977
    }

    private fun reportPlayback(baseUrl: String, userToken: String, itemId: String, playSessionId: String) {
        val body = mapper.writeValueAsString(PlaybackStartRequest(itemId, playSessionId))
        val (status, respBody) = post(uri(baseUrl, "/Sessions/Playing"), body, authHeader(userToken))
        check(status in 200..299) { "Failed to report playback start: HTTP $status — $respBody" }
    }

    private fun reportProgress(
        baseUrl: String, userToken: String, itemId: String,
        playSessionId: String, positionTicks: Long
    ) {
        val body = mapper.writeValueAsString(PlaybackProgressRequest(itemId, playSessionId, positionTicks))
        val (status, respBody) = post(uri(baseUrl, "/Sessions/Playing/Progress"), body, authHeader(userToken))
        check(status in 200..299) { "Failed to report playback progress: HTTP $status — $respBody" }
    }

    private fun reportStopped(baseUrl: String, userToken: String, itemId: String, playSessionId: String) {
        val body = mapper.writeValueAsString(PlaybackStopRequest(itemId, playSessionId, positionTicks = 1_200_000_000L))
        post(uri(baseUrl, "/Sessions/Playing/Stopped"), body, authHeader(userToken))
    }

    /**
     * The /Sessions endpoint is how [com.github.schaka.janitorrstats.mediaserver.jellyfin.JellyfinMediaServerClient]
     * discovers playback. Wait until the reported playback shows up there before polling — the
     * server writes are not strictly synchronous with the POSTs above.
     */
    private fun waitForSessionToAppear(baseUrl: String, apiKey: String, itemId: String) {
        repeat(20) {
            val (status, body) = get(uri(baseUrl, "/Sessions"), authHeader(apiKey))
            if (status in 200..299) {
                val sessions = mapper.readTree(body)
                val anyMatch = sessions.any { s ->
                    s.path("NowPlayingItem").path("Id").asText("") == itemId
                }
                if (anyMatch) return
            }
            Thread.sleep(500)
        }
        error("Reported playback never surfaced on /Sessions within 10 seconds")
    }

    private fun authHeader(token: String? = null): String =
        if (token == null) "MediaBrowser , $AUTH_PARAMS"
        else """MediaBrowser Token="$token", $AUTH_PARAMS"""

    private fun uri(baseUrl: String, path: String): URI = URI.create("$baseUrl$path")

    private fun get(uri: URI, authorization: String): Pair<Int, String> {
        val req = HttpRequest.newBuilder(uri)
            .header("Authorization", authorization)
            .GET()
            .build()
        val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
        return resp.statusCode() to (resp.body() ?: "")
    }

    private fun post(uri: URI, body: String, authorization: String): Pair<Int, String> {
        val req = HttpRequest.newBuilder(uri)
            .header("Authorization", authorization)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.ifBlank { "{}" }))
            .build()
        val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
        return resp.statusCode() to (resp.body() ?: "")
    }
}
