package com.github.schaka.janitorrstats.api

import io.quarkus.test.junit.QuarkusIntegrationTest
import io.restassured.RestAssured.given
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test

/**
 * Integration tests for [HistoryResource] that run against the packaged artifact (JVM jar or
 * native binary depending on the build profile). These tests cannot inject CDI beans, so they
 * use only HTTP assertions.
 *
 * The shape-verification tests deliberately query for IDs that will not exist in the empty
 * test database. Even an empty [PagedResponse] must be serialized by Jackson, which is enough
 * to catch native-image reflection registration gaps on the response model classes.
 */
@QuarkusIntegrationTest
class HistoryResourceIT {

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
    fun `movie endpoint serializes PagedResponse correctly for empty results`() {
        given().queryParam("imdbId", "tt0000000")
            .`when`().get("/history/movies")
            .then()
            .statusCode(200)
            .body("content", hasSize<Any>(0))
            .body("totalItems", equalTo(0))
            .body("page", equalTo(0))
            .body("pageSize", equalTo(100))
            .body("totalPages", equalTo(1))
    }

    @Test
    fun `show endpoint serializes PagedResponse correctly for empty results`() {
        given().queryParam("tvdbId", "0")
            .`when`().get("/history/shows")
            .then()
            .statusCode(200)
            .body("content", hasSize<Any>(0))
            .body("totalItems", equalTo(0))
            .body("page", equalTo(0))
            .body("pageSize", equalTo(100))
            .body("totalPages", equalTo(1))
    }
}
