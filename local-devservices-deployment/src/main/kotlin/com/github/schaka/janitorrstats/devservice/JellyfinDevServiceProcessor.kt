package com.github.schaka.janitorrstats.devservice

import com.github.schaka.janitorrstats.containers.JellyfinContainer
import com.github.schaka.janitorrstats.setup.JellyfinSetup
import com.github.schaka.janitorrstats.setup.MediaLibrarySetup
import io.quarkus.deployment.IsProduction
import io.quarkus.deployment.annotations.BuildStep
import io.quarkus.deployment.builditem.CuratedApplicationShutdownBuildItem
import io.quarkus.deployment.builditem.DevServicesResultBuildItem
import org.jboss.logging.Logger
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

class JellyfinDevServiceProcessor {

    companion object {
        private val log = Logger.getLogger(JellyfinDevServiceProcessor::class.java)
        private val lock = Any()
        private var container: JellyfinContainer? = null
        private var cachedConfig: Map<String, String>? = null
    }

    @BuildStep(onlyIfNot = [IsProduction::class])
    fun startJellyfin(shutdown: CuratedApplicationShutdownBuildItem): DevServicesResultBuildItem =
        synchronized(lock) {
            val running = container
            val config = cachedConfig
            if (running != null && running.isRunning && config != null) {
                log.info("Reusing running Jellyfin dev service container")
                return@synchronized DevServicesResultBuildItem.discovered()
                    .feature("jellyfin")
                    .containerId(running.containerId)
                    .config(config)
                    .build()
            }

            val projectRoot = Path.of(System.getProperty("user.dir"))
            val localRuntime = projectRoot.resolve("local-runtime")
            val mediaRoot = localRuntime.resolve("media")

            val mediaFuture = CompletableFuture.runAsync {
                MediaLibrarySetup(mediaRoot).prepare()
            }

            log.info("Starting Jellyfin dev service container...")
            val jellyfin = JellyfinContainer(localRuntime, mediaRoot)
            jellyfin.start()
            container = jellyfin
            log.info("Jellyfin container started")

            mediaFuture.join()

            val url = "http://localhost:${jellyfin.getMappedPort(8096)}"
            val apiKey = JellyfinSetup(url).setup()

            // DevServicesResultBuildItem config is intentionally injected at a lower ordinal
            // than application.yaml (by design, so users can override dev service values via
            // their own config). This means that if application.yaml has a root-level default
            // for a key (e.g. jellyfin.base-url), that default wins over the dev service value.
            //
            // The fix is architectural: application-level keys that a dev service must control
            // must NOT have root-level defaults in application.yaml. Their defaults belong in
            // the %prod profile section, which only applies in production mode.
            //
            // Convention for every container-backed dev service added to this project:
            //   1. Set the application config key (e.g. jellyfin.base-url) — no root default in YAML.
            //   2. Set the exact Quarkus framework key (e.g. quarkus.rest-client.{key}.url)
            //      in addition, so REST clients do not depend on expression expansion to see it.
            val newConfig = mapOf(
                "jellyfin.base-url" to url,
                "jellyfin.api-key" to apiKey,
                "quarkus.rest-client.jellyfin-api.url" to url
            )
            cachedConfig = newConfig

            shutdown.addCloseTask({
                log.info("Stopping Jellyfin dev service container...")
                container?.stop()
                container = null
                cachedConfig = null
            }, true)

            log.info("Jellyfin dev service running at $url")

            DevServicesResultBuildItem.discovered()
                .feature("jellyfin")
                .containerId(jellyfin.containerId)
                .config(newConfig)
                .build()
        }
}
