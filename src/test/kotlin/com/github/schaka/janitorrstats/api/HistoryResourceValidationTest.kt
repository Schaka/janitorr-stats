package com.github.schaka.janitorrstats.api

import com.github.schaka.janitorrstats.persistence.repository.PlayEventRepository
import jakarta.ws.rs.BadRequestException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Pure unit tests for [HistoryResource] input validation. These do not require a running
 * container or persistence context because the validation branches short-circuit before
 * the repository is touched.
 */
class HistoryResourceValidationTest {

    private val resource = HistoryResource(PlayEventRepository())

    @Test
    fun `movie history rejects request without any ID`() {
        assertThrows<BadRequestException> {
            resource.getMovieHistory(imdbId = null, tmdbId = null, page = 0, pageSize = 100)
        }
    }

    @Test
    fun `show history rejects request without any ID`() {
        assertThrows<BadRequestException> {
            resource.getShowHistory(
                imdbId = null, tmdbId = null, tvdbId = null,
                season = null, episode = null, page = 0, pageSize = 100
            )
        }
    }
}
