package com.github.schaka.janitorrstats.setup

import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Drives the Jellyfin HTTP API to complete the startup wizard, create an admin user,
 * create an API key, add media libraries, and trigger an initial scan.
 */
class JellyfinSetup(private val baseUrl: String) {

    companion object {
        private val log = LoggerFactory.getLogger(JellyfinSetup::class.java)
        private const val ADMIN_USER = "admin"
        private const val ADMIN_PASS = "adminadmin"
        private const val AUTH_PARAMS =
            """Client="Janitorr-Stats-LocalDev", Device="LocalDev", DeviceId="00000000-0000-0000-0000-000000000001", Version="1.0.0""""
    }

    private val http = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .build()
    private var userToken = ""

    /**
     * Runs the full Jellyfin setup sequence and returns the API key to use for
     * janitorr-stats configuration.
     */
    fun setup(): String {
        if (wizardNeeded()) {
            log.info("Completing Jellyfin startup wizard...")
            completeWizard()
        } else {
            log.info("Jellyfin wizard already complete, skipping setup")
        }

        log.info("Authenticating as admin...")
        authenticateWithRetry()

        log.info("Creating API key...")
        val apiKey = createApiKey()

        log.info("Adding media libraries...")
        addLibraries(apiKey)

        log.info("Triggering library scan...")
        triggerScan(apiKey)

        return apiKey
    }

    /**
     * Polls /Startup/Configuration until Jellyfin's wizard API is ready (not 503).
     * Returns true if the wizard still needs to be run, false if already complete.
     */
    private fun wizardNeeded(): Boolean {
        repeat(30) { attempt ->
            val (status, body) = get("/Startup/Configuration")
            when {
                status in 200..299 -> {
                    log.info("Jellyfin wizard API ready after {} attempt(s)", attempt + 1)
                    return true
                }
                status == 503 -> {
                    log.info("Jellyfin not ready yet (503), waiting... ({}/30)", attempt + 1)
                    Thread.sleep(2_000)
                }
                else -> {
                    log.info("Jellyfin wizard already complete (status {})", status)
                    return false
                }
            }
        }
        error("Jellyfin wizard API did not become ready after 30 attempts")
    }

    private fun completeWizard() {
        postAndCheck("/Startup/Configuration", """{"UICulture":"en-US","MetadataCountryCode":"US","PreferredMetadataLanguage":"en"}""")
        // GET /Startup/User must be called first — it initialises the first user entry in Jellyfin's
        // database. Without it, the subsequent POST finds an empty user collection and throws a 500.
        val (getUserStatus, _) = get("/Startup/User")
        log.info("GET /Startup/User → {}", getUserStatus)
        postAndCheck("/Startup/User", """{"Name":"$ADMIN_USER","Password":"$ADMIN_PASS"}""")
        postAndCheck("/Startup/RemoteAccess", """{"EnableRemoteAccess":true,"EnableAutomaticPortMapping":false}""")
        postAndCheck("/Startup/Complete", "")
        // Jellyfin briefly reinitialises after the wizard is complete.
        Thread.sleep(3_000)
    }

    private fun postAndCheck(path: String, body: String) {
        val (status, responseBody) = post(path, body)
        if (status !in 200..299) {
            log.warn("POST {} returned {} — body: {}", path, status, responseBody)
        } else {
            log.info("POST {} → {}", path, status)
        }
    }

    private fun authenticateWithRetry() {
        var lastStatus = -1
        var lastBody = ""
        repeat(10) { attempt ->
            try {
                val (status, body) = post(
                    "/Users/AuthenticateByName",
                    """{"Username":"$ADMIN_USER","Pw":"$ADMIN_PASS"}"""
                )
                lastStatus = status
                lastBody = body
                userToken = parseJsonString(body, "AccessToken")
                if (userToken.isNotBlank()) {
                    log.info("Authenticated successfully")
                    return
                }
                log.warn("Auth attempt {}/10 — status: {}, body: {}", attempt + 1, status, body)
            } catch (e: Exception) {
                log.warn("Auth attempt {}/10 failed with exception: {}", attempt + 1, e.message)
            }
            Thread.sleep(2_000)
        }
        error("Failed to authenticate with Jellyfin after 10 attempts — last status: $lastStatus, body: $lastBody")
    }

    private fun createApiKey(): String {
        // POST returns 204 No Content; the key is then retrievable via GET.
        post("/Auth/Keys?app=janitorr-stats-local", "", token = userToken)
        val (_, body) = get("/Auth/Keys", token = userToken)
        val key = parseJsonString(body, "AccessToken")
        check(key.isNotBlank()) { "Could not parse API key from Jellyfin response: $body" }
        return key
    }

    private fun addLibraries(apiKey: String) {
        post(
            "/Library/VirtualFolders?name=Movies&collectionType=movies&refreshLibrary=false",
            """{"Paths":["/media/Movies"]}""",
            token = apiKey
        )
        post(
            "/Library/VirtualFolders?name=TV+Shows&collectionType=tvshows&refreshLibrary=false",
            """{"Paths":["/media/TV Shows"]}""",
            token = apiKey
        )
    }

    private fun triggerScan(apiKey: String) {
        post("/Library/Refresh", "", token = apiKey)
    }

    private fun post(
        path: String,
        body: String,
        token: String? = null,
    ): Pair<Int, String> {
        val builder = HttpRequest.newBuilder(URI.create("$baseUrl$path"))
            .header("Content-Type", "application/json")
        val authHeader = if (token != null) {
            """MediaBrowser Token="$token", $AUTH_PARAMS"""
        } else {
            "MediaBrowser , $AUTH_PARAMS"
        }
        builder.header("Authorization", authHeader)
        builder.POST(
            if (body.isEmpty()) HttpRequest.BodyPublishers.ofString("{}")
            else HttpRequest.BodyPublishers.ofString(body)
        )
        val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        return response.statusCode() to (response.body() ?: "")
    }

    private fun get(path: String, token: String? = null): Pair<Int, String> {
        val builder = HttpRequest.newBuilder(URI.create("$baseUrl$path")).GET()
        val authHeader = if (token != null) {
            """MediaBrowser Token="$token", $AUTH_PARAMS"""
        } else {
            "MediaBrowser , $AUTH_PARAMS"
        }
        builder.header("Authorization", authHeader)
        val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        return response.statusCode() to (response.body() ?: "")
    }

    /**
     * Naive JSON string field extractor — avoids pulling in a JSON library for this simple case.
     * Finds the first occurrence of `"key":"<value>"` and returns the value.
     */
    private fun parseJsonString(json: String, key: String): String {
        val marker = """"$key":""""
        val start = json.indexOf(marker)
        if (start == -1) return ""
        val valueStart = start + marker.length
        val end = json.indexOf('"', valueStart)
        return if (end == -1) "" else json.substring(valueStart, end)
    }
}
