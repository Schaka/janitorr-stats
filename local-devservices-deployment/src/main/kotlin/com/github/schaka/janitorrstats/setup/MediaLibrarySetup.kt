package com.github.schaka.janitorrstats.setup

import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path

private val log = LoggerFactory.getLogger(MediaLibrarySetup::class.java)

/**
 * Video file URLs from https://gist.github.com/jsturgis/3b19447b304616f18657
 * These are DRM-free, openly licensed sample videos used as stand-ins for real content.
 */
private val VIDEO_URLS = listOf(
    "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
    "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
    "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
    "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
    "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
    "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4",
    "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMeltdowns.mp4",
    "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
    "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4",
    "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
    "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/VolkswagenGTIReview.mp4",
    "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4",
    "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WhatCarCanYouGetForAGrand.mp4"
)

private data class Movie(val title: String, val year: Int, val videoIndex: Int)

private data class Episode(val season: Int, val episode: Int, val title: String)

private data class TvShow(val title: String, val year: Int, val episodes: List<Episode>, val videoIndex: Int)

private val MOVIES = listOf(
    Movie("Star Wars", 1977, 0),
    Movie("The Empire Strikes Back", 1980, 1),
    Movie("Return of the Jedi", 1983, 2),
    Movie("The Lord of the Rings The Fellowship of the Ring", 2001, 3),
    Movie("The Lord of the Rings The Two Towers", 2002, 4),
    Movie("The Lord of the Rings The Return of the King", 2003, 5),
    Movie("The Dark Knight", 2008, 6),
    Movie("Inception", 2010, 7),
    Movie("The Matrix", 1999, 8),
    Movie("Forrest Gump", 1994, 9),
)

private val TV_SHOWS = listOf(
    TvShow(
        title = "Breaking Bad", year = 2008, videoIndex = 10,
        episodes = listOf(
            Episode(1, 1, "Pilot"), Episode(1, 2, "Cats in the Bag"), Episode(1, 3, "And the Bags in the River"),
            Episode(2, 1, "Seven Thirty-Seven"), Episode(2, 2, "Down"), Episode(2, 3, "Bit by a Dead Bee"),
            Episode(3, 1, "No Mas"), Episode(3, 2, "Caballo Sin Nombre"), Episode(3, 3, "IFT"),
            Episode(4, 1, "Box Cutter"), Episode(4, 2, "Thirty-Eight Snub"), Episode(4, 3, "Open House"),
        )
    ),
    TvShow(
        title = "Friends", year = 1994, videoIndex = 11,
        episodes = listOf(
            Episode(1, 1, "The One Where Monica Gets a Roommate"), Episode(1, 2, "The One with the Sonogram at the End"), Episode(1, 3, "The One with the Thumb"),
            Episode(2, 1, "The One with Ross New Girlfriend"), Episode(2, 2, "The One with the Breast Milk"), Episode(2, 3, "The One Where Heckles Dies"),
            Episode(3, 1, "The One with the Princess Leia Fantasy"), Episode(3, 2, "The One Where No Ones Ready"), Episode(3, 3, "The One with the Jam"),
        )
    ),
    TvShow(
        title = "Game of Thrones", year = 2011, videoIndex = 12,
        episodes = listOf(
            Episode(1, 1, "Winter Is Coming"), Episode(1, 2, "The Kingsroad"), Episode(1, 3, "Lord Snow"),
            Episode(2, 1, "The North Remembers"), Episode(2, 2, "The Night Lands"), Episode(2, 3, "What Is Dead May Never Die"),
        )
    ),
    TvShow(
        title = "The Office", year = 2005, videoIndex = 0,
        episodes = listOf(
            Episode(1, 1, "Pilot"), Episode(1, 2, "Diversity Day"), Episode(1, 3, "Health Care"),
            Episode(2, 1, "The Dundies"), Episode(2, 2, "Sexual Harassment"), Episode(2, 3, "Office Olympics"),
        )
    ),
    TvShow(
        title = "Stranger Things", year = 2016, videoIndex = 1,
        episodes = listOf(
            Episode(1, 1, "The Vanishing of Will Byers"),
            Episode(1, 2, "The Weirdo on Maple Street"),
            Episode(1, 3, "Holly Jolly"),
            Episode(1, 4, "The Body"),
        )
    ),
)

class MediaLibrarySetup(private val mediaRoot: Path) {

    private val cacheDir = mediaRoot.resolve("cache")
    private val moviesDir = mediaRoot.resolve("Movies")
    private val tvShowsDir = mediaRoot.resolve("TV Shows")

    /**
     * Downloads video files to cache if missing, then creates the full directory/symlink
     * structure that Jellyfin will scan.
     */
    fun prepare() {
        Files.createDirectories(cacheDir)
        Files.createDirectories(moviesDir)
        Files.createDirectories(tvShowsDir)

        downloadCacheFiles()
        createMovieStructure()
        createTvShowStructure()
    }

    private fun downloadCacheFiles() {
        val http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build()

        VIDEO_URLS.forEachIndexed { index, url ->
            val filename = url.substringAfterLast("/")
            val target = cacheDir.resolve(filename)

            if (Files.exists(target) && Files.size(target) > 0) {
                log.info("Cache hit [{}/{}]: {}", index + 1, VIDEO_URLS.size, filename)
                return@forEachIndexed
            }

            log.info("Downloading [{}/{}]: {}", index + 1, VIDEO_URLS.size, filename)
            try {
                val request = HttpRequest.newBuilder(URI.create(url)).GET().build()
                http.send(request, HttpResponse.BodyHandlers.ofFile(target))
                log.info("Downloaded: {} ({} MB)", filename, Files.size(target) / 1_048_576)
            } catch (e: Exception) {
                log.warn("Failed to download {} — creating empty placeholder: {}", filename, e.message)
                Files.write(target, ByteArray(0))
            }
        }
    }

    private fun createMovieStructure() {
        for (movie in MOVIES) {
            val dir = moviesDir.resolve("${movie.title} (${movie.year})")
            Files.createDirectories(dir)

            val linkPath = dir.resolve("${movie.title} (${movie.year}).mp4")
            if (Files.exists(linkPath)) continue

            val sourceUrl = VIDEO_URLS[movie.videoIndex]
            val cacheName = sourceUrl.substringAfterLast("/")
            val target = dir.relativize(cacheDir.resolve(cacheName))
            Files.createSymbolicLink(linkPath, target)
        }
    }

    private fun createTvShowStructure() {
        for (show in TV_SHOWS) {
            val sourceUrl = VIDEO_URLS[show.videoIndex]
            val cacheName = sourceUrl.substringAfterLast("/")

            val seasonNumbers = show.episodes.map { it.season }.distinct()
            for (seasonNum in seasonNumbers) {
                val seasonDir = tvShowsDir
                    .resolve("${show.title} (${show.year})")
                    .resolve("Season %02d".format(seasonNum))
                Files.createDirectories(seasonDir)

                for (ep in show.episodes.filter { it.season == seasonNum }) {
                    val filename = "${show.title} - S%02dE%02d - ${ep.title}.mp4"
                        .format(ep.season, ep.episode)
                    val linkPath = seasonDir.resolve(filename)
                    if (!Files.exists(linkPath)) {
                        val target = seasonDir.relativize(cacheDir.resolve(cacheName))
                        Files.createSymbolicLink(linkPath, target)
                    }
                }
            }
        }
    }
}
