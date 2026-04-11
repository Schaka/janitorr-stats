package com.github.schaka.janitorrstats.persistence.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

/**
 * Canonical record for a piece of media, keyed on external IDs.
 * Jellyfin's internal item ID is stored for debugging only and plays no role in queries or joins.
 */
@Entity
@Table(name = "media_items")
class MediaItem {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 36)
    var id: UUID = UUID.randomUUID()

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 10)
    var mediaType: MediaType = MediaType.MOVIE

    @Column(nullable = false, length = 500)
    var title: String = ""

    var year: Int? = null

    @Column(name = "imdb_id", length = 20)
    var imdbId: String? = null

    @Column(name = "tmdb_id", length = 20)
    var tmdbId: String? = null

    @Column(name = "tvdb_id", length = 20)
    var tvdbId: String? = null

    @Column(name = "jellyfin_item_id", length = 100)
    var jellyfinItemId: String? = null

    @OneToMany(mappedBy = "mediaItem", cascade = [CascadeType.ALL], orphanRemoval = true)
    var seasons: MutableList<Season> = mutableListOf()
}
