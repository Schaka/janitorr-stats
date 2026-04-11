package com.github.schaka.janitorrstats.mediaserver.jellyfin

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Represents a Jellyfin session from the /Sessions endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class JellyfinSession(
    @JsonProperty("UserId") val userId: String? = null,
    @JsonProperty("UserName") val userName: String? = null,
    @JsonProperty("NowPlayingItem") val nowPlayingItem: JellyfinNowPlayingItem? = null,
    @JsonProperty("PlayState") val playState: JellyfinPlayState? = null
)

/**
 * The item currently being played in a session.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class JellyfinNowPlayingItem(
    @JsonProperty("Id") val id: String = "",
    @JsonProperty("Name") val name: String = "",
    @JsonProperty("Type") val type: String = "",
    @JsonProperty("SeriesId") val seriesId: String? = null,
    @JsonProperty("SeriesName") val seriesName: String? = null,
    @JsonProperty("SeasonId") val seasonId: String? = null,
    @JsonProperty("ParentIndexNumber") val parentIndexNumber: Int? = null,
    @JsonProperty("IndexNumber") val indexNumber: Int? = null,
    @JsonProperty("RunTimeTicks") val runTimeTicks: Long? = null,
    @JsonProperty("ProviderIds") val providerIds: JellyfinProviderIds? = null
)

/**
 * Play state for a session, including position and pause status.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class JellyfinPlayState(
    @JsonProperty("PositionTicks") val positionTicks: Long? = null,
    @JsonProperty("IsPaused") val isPaused: Boolean = false
)

/**
 * External provider IDs attached to a Jellyfin item.
 * Field names from Jellyfin are capitalized (Imdb, Tmdb, Tvdb).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class JellyfinProviderIds(
    @JsonProperty("Imdb") val imdb: String? = null,
    @JsonProperty("Tmdb") val tmdb: String? = null,
    @JsonProperty("Tvdb") val tvdb: String? = null,
    @get:JsonAnyGetter @JsonAnySetter
    val other: MutableMap<String, String> = mutableMapOf()
)

/**
 * Full item representation from the /Items endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class JellyfinItem(
    @JsonProperty("Id") val id: String = "",
    @JsonProperty("Name") val name: String = "",
    @JsonProperty("Type") val type: String = "",
    @JsonProperty("ProductionYear") val productionYear: Int? = null,
    @JsonProperty("ProviderIds") val providerIds: JellyfinProviderIds? = null,
    @JsonProperty("SeriesId") val seriesId: String? = null,
    @JsonProperty("SeriesName") val seriesName: String? = null,
    @JsonProperty("SeasonId") val seasonId: String? = null,
    @JsonProperty("ParentIndexNumber") val parentIndexNumber: Int? = null,
    @JsonProperty("IndexNumber") val indexNumber: Int? = null,
    @JsonProperty("RunTimeTicks") val runTimeTicks: Long? = null
)

/**
 * Paginated list of items from the /Items endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class JellyfinItemPage(
    @JsonProperty("Items") val items: List<JellyfinItem> = emptyList(),
    @JsonProperty("TotalRecordCount") val totalRecordCount: Int = 0
)

/**
 * A Jellyfin user.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class JellyfinUser(
    @JsonProperty("Id") val id: String = "",
    @JsonProperty("Name") val name: String = ""
)
