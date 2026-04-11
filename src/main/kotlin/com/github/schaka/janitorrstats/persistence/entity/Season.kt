package com.github.schaka.janitorrstats.persistence.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

/**
 * Represents a single season of a series, linked to external season IDs where available.
 */
@Entity
@Table(name = "seasons")
class Season {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 36)
    var id: UUID = UUID.randomUUID()

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_item_id", nullable = false)
    lateinit var mediaItem: MediaItem

    @Column(name = "season_number", nullable = false)
    var seasonNumber: Int = 0

    @Column(name = "tmdb_season_id", length = 20)
    var tmdbSeasonId: String? = null

    @Column(name = "tvdb_season_id", length = 20)
    var tvdbSeasonId: String? = null

    @Column(name = "jellyfin_season_id", length = 100)
    var jellyfinSeasonId: String? = null
}
