package com.github.schaka.janitorrstats.mediaserver.jellyfin

import com.github.schaka.janitorrstats.config.JellyfinConfig
import io.quarkus.arc.Unremovable
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.client.ClientRequestContext
import jakarta.ws.rs.client.ClientRequestFilter
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.WebApplicationException

/**
 * Attaches the Jellyfin MediaBrowser authorization header to every outgoing request.
 */
@ApplicationScoped
@Unremovable
class JellyfinAuthFilter(
    private val config: JellyfinConfig
) : ClientRequestFilter {

    override fun filter(requestContext: ClientRequestContext) {
        requestContext.headers.add(
            "Authorization",
            """MediaBrowser Token="${config.apiKey()}", Client="JanitorrStats", Device="Quarkus", DeviceId="janitorr-stats", Version="1.0""""
        )
    }
}

/**
 * Maps non-2xx responses from the Jellyfin API to exceptions with useful messages.
 */
@ApplicationScoped
class JellyfinExceptionMapper : ResponseExceptionMapper<WebApplicationException> {

    override fun toThrowable(response: Response): WebApplicationException {
        val body = response.readEntity(String::class.java)
        return WebApplicationException(
            "Jellyfin API error ${response.status}: $body",
            response.status
        )
    }
}
