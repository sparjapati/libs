package com.sparjapati.vendorClient.logging

import com.sparjapati.vendorClient.config.DEFAULT_SENSITIVE_HEADERS

fun Map<String, List<String>>.maskSensitive(
    sensitiveHeaders: Set<String> = DEFAULT_SENSITIVE_HEADERS,
): Map<String, List<String>> = mapValues { (key, values) ->
    // Each sensitive value is replaced with a run of '*' equal to the original length,
    // so logs reveal approximate secret length without exposing content.
    if (key.lowercase() in sensitiveHeaders) values.map { "*".repeat(it.length) } else values
}
