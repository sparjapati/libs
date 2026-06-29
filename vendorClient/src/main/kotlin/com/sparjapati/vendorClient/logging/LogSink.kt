package com.sparjapati.vendorClient.logging

import org.slf4j.Logger
import org.slf4j.Marker

interface LogSink {
    fun log(message: String)
}

class Slf4jLogSink(
    private val logger: Logger,
    private val marker: Marker? = null,
) : LogSink {
    override fun log(message: String) {
        if (marker != null) logger.debug(marker, message) else logger.debug(message)
    }
}
