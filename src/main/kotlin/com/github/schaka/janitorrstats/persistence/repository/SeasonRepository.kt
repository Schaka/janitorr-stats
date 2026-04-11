package com.github.schaka.janitorrstats.persistence.repository

import com.github.schaka.janitorrstats.persistence.entity.Season
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/**
 * Repository for [Season] persistence operations.
 */
@ApplicationScoped
class SeasonRepository : PanacheRepositoryBase<Season, UUID> {

    /**
     * Finds a season by its parent media item ID and season number.
     */
    fun findByMediaItemAndNumber(mediaItemId: UUID, seasonNumber: Int): Season? {
        return find("mediaItem.id = ?1 AND seasonNumber = ?2", mediaItemId, seasonNumber).firstResult()
    }
}
