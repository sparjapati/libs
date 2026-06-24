package com.sparjapati.bulkFileProcessing.batch

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import java.io.File
import java.time.Duration
import java.time.Instant

/**
 * Deletes stale bulk-processing temp files left behind by a previous JVM crash or
 * ungraceful shutdown.
 *
 * Runs once at startup via [ApplicationRunner]. Scans the system temp directory for
 * files whose names start with any of the library's well-known prefixes and deletes
 * those older than [staleAfter].
 *
 * Registered only when [@EnableBulkFileProcessing][com.sparjapati.bulkFileProcessing.config.EnableBulkFileProcessing]
 * is present on a configuration class. Override the `bulkTempFileCleanupRunner` bean
 * in the consuming application to change [staleAfter] or disable cleanup entirely.
 *
 * @param staleAfter age threshold after which a temp file is considered stale and deleted.
 *   Defaults to 24 hours.
 */
class BulkTempFileCleanupRunner(
    private val staleAfter: Duration = Duration.ofHours(24),
) : ApplicationRunner {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(BulkTempFileCleanupRunner::class.java)

        /** Prefix used by [RowAccumulator] for inline working files in the system temp directory. */
        const val PREFIX_INLINE = "bulk-inline-"
    }

    override fun run(args: ApplicationArguments) {
        val tempDir = File(System.getProperty("java.io.tmpdir"))
        val cutoff = Instant.now().minus(staleAfter)

        val stale = tempDir.listFiles { file ->
            file.name.startsWith(PREFIX_INLINE) &&
                file.lastModified() < cutoff.toEpochMilli()
        } ?: return

        if (stale.isEmpty()) {
            LOGGER.debug("No stale bulk temp files found in {}", tempDir.absolutePath)
            return
        }

        LOGGER.info("Found {} stale bulk temp file(s) older than {} — deleting", stale.size, staleAfter)
        stale.forEach { file ->
            if (file.delete()) {
                LOGGER.info("Deleted stale temp file: {}", file.absolutePath)
            } else {
                LOGGER.warn("Failed to delete stale temp file: {}", file.absolutePath)
            }
        }
    }
}
