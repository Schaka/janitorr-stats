package com.github.schaka.janitorrstats.persistence.repository

import com.github.schaka.janitorrstats.persistence.entity.MediaItem
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/**
 * Repository for [MediaItem] persistence operations.
 * Lookups are always by external IDs — never by Jellyfin internal IDs.
 */
@ApplicationScoped
class MediaItemRepository : PanacheRepositoryBase<MediaItem, UUID> {

    /**
     * Finds a media item matching the given external IDs.
     * All non-null parameters are ANDed together.
     */
    fun findByExternalIds(imdbId: String?, tmdbId: String?, tvdbId: String?): MediaItem? {
        val conditions = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()

        if (imdbId != null) {
            conditions.add("imdbId = :imdbId")
            params["imdbId"] = imdbId
        }
        if (tmdbId != null) {
            conditions.add("tmdbId = :tmdbId")
            params["tmdbId"] = tmdbId
        }
        if (tvdbId != null) {
            conditions.add("tvdbId = :tvdbId")
            params["tvdbId"] = tvdbId
        }

        if (conditions.isEmpty()) return null

        return find(conditions.joinToString(" AND "), params).firstResult()
    }
}
