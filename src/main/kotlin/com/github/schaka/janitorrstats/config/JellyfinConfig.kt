package com.github.schaka.janitorrstats.config

import io.smallrye.config.ConfigMapping
import io.smallrye.config.WithDefault
import io.smallrye.config.WithName
import java.time.Duration

/**
 * Configuration properties for the Jellyfin connection.
 */
@ConfigMapping(prefix = "jellyfin")
interface JellyfinConfig {

    @WithName("base-url")
    fun baseUrl(): String

    @WithName("api-key")
    fun apiKey(): String

    @WithName("poll-interval")
    @WithDefault("60s")
    fun pollInterval(): Duration
}
