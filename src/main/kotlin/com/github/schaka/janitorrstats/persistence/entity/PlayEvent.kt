package com.github.schaka.janitorrstats.persistence.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/**
 * A discrete play session for a media item by a user.
 * For TV episodes, season and episode numbers are stored directly rather than via a foreign key to seasons,
 * keeping queries simple while still allowing season-level filtering.
 */
@Entity
@Table(name = "play_events")
class PlayEvent {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 36)
    var id: UUID = UUID.randomUUID()

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    lateinit var user: User

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_item_id", nullable = false)
    lateinit var mediaItem: MediaItem

    @Column(name = "season_number")
    var seasonNumber: Int? = null

    @Column(name = "episode_number")
    var episodeNumber: Int? = null

    @Column(name = "played_at", nullable = false)
    var playedAt: Instant = Instant.now()

    @Column(name = "duration_ms", nullable = false)
    var durationMs: Long = 0

    @Column(name = "position_ms", nullable = false)
    var positionMs: Long = 0

    @Column(name = "percent_complete", nullable = false)
    var percentComplete: Int = 0

    @Column(nullable = false)
    var completed: Boolean = false
}
