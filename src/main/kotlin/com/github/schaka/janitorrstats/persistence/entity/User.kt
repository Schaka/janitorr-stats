package com.github.schaka.janitorrstats.persistence.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

/**
 * A Jellyfin user observed in play events.
 * The Jellyfin user ID is the only place where an internal Jellyfin identifier is stored as a lookup key.
 */
@Entity
@Table(name = "users")
class User {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 36)
    var id: UUID = UUID.randomUUID()

    @Column(name = "jellyfin_user_id", nullable = false, unique = true, length = 100)
    var jellyfinUserId: String = ""

    @Column(nullable = false, length = 200)
    var username: String = ""
}
