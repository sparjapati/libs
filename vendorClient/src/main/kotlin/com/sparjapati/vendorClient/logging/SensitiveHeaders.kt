package com.sparjapati.vendorClient.logging

import com.sparjapati.vendorClient.config.DEFAULT_SENSITIVE_HEADERS

fun Map<String, List<String>>.maskSensitive(
    sensitiveHeaders: Set<String> = DEFAULT_SENSITIVE_HEADERS,
): Map<String, List<String>> = mapValues { (key, values) ->
    if (key.lowercase() in sensitiveHeaders) listOf("***") else values
}
