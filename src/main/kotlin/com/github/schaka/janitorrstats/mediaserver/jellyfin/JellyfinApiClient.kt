package com.github.schaka.janitorrstats.mediaserver.jellyfin

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.QueryParam
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient

/**
 * Typed REST client for the Jellyfin API.
 * Authentication is handled via [JellyfinAuthFilter] which is registered as a CDI provider.
 */
@RegisterRestClient(configKey = "jellyfin-api")
@RegisterProvider(JellyfinAuthFilter::class)
@Path("/")
interface JellyfinApiClient {

    /**
     * Retrieves active and recently ended sessions.
     */
    @GET
    @Path("/Sessions")
    fun getSessions(): List<JellyfinSession>

    /**
     * Retrieves a single item by its Jellyfin internal ID, including provider IDs.
     * [userId] is required by Jellyfin 10.9+ to authorize and scope the item lookup.
     */
    @GET
    @Path("/Items/{itemId}")
    fun getItem(
        @PathParam("itemId") itemId: String,
        @QueryParam("fields") fields: String,
        @QueryParam("userId") userId: String
    ): JellyfinItem

    /**
     * Retrieves all items under a parent (e.g. all episodes of a series), including provider IDs.
     */
    @GET
    @Path("/Items")
    fun getItems(
        @QueryParam("parentId") parentId: String,
        @QueryParam("fields") fields: String,
        @QueryParam("Recursive") recursive: Boolean,
        @QueryParam("limit") limit: Int
    ): JellyfinItemPage

    /**
     * Retrieves seasons of a show.
     */
    @GET
    @Path("/Shows/{seriesId}/Seasons")
    fun getSeasons(
        @PathParam("seriesId") seriesId: String,
        @QueryParam("fields") fields: String
    ): JellyfinItemPage

    /**
     * Retrieves the list of users.
     */
    @GET
    @Path("/Users")
    fun getUsers(): List<JellyfinUser>
}
