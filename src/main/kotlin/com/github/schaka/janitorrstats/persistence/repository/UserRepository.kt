package com.github.schaka.janitorrstats.persistence.repository

import com.github.schaka.janitorrstats.persistence.entity.User
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/**
 * Repository for [User] persistence operations.
 */
@ApplicationScoped
class UserRepository : PanacheRepositoryBase<User, UUID> {

    /**
     * Finds a user by their Jellyfin user ID.
     */
    fun findByJellyfinUserId(jellyfinUserId: String): User? {
        return find("jellyfinUserId", jellyfinUserId).firstResult()
    }
}
