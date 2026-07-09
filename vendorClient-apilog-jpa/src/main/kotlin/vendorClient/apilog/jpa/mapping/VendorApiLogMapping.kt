package vendorClient.apilog.jpa.mapping

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import vendorClient.apilog.jpa.entity.VendorApiLogEntity
import vendorClient.logging.VendorApiLog

private val mapper = ObjectMapper()
private val HEADER_TYPE = object : TypeReference<Map<String, List<String>>>() {}

private fun Map<String, List<String>>.toJson(): String = mapper.writeValueAsString(this)
private fun String.toHeaderMap(): Map<String, List<String>> =
    runCatching { mapper.readValue(this, HEADER_TYPE) }.getOrDefault(emptyMap())

fun VendorApiLogEntity.toDto(): VendorApiLog = VendorApiLog(
    apiName = apiName,
    requestId = requestId,
    attemptId = attemptId,
    httpMethod = httpMethod,
    url = url,
    requestHeaders = requestHeaders.toHeaderMap(),
    requestBody = requestBody,
    responseCode = responseCode,
    responseHeaders = responseHeaders.toHeaderMap(),
    responseBody = responseBody,
    success = success,
    errorMessage = errorMessage,
    durationMs = durationMs,
)

fun VendorApiLog.toEntity(): VendorApiLogEntity = VendorApiLogEntity(
    apiName = apiName,
    requestId = requestId,
    attemptId = attemptId,
    httpMethod = httpMethod,
    url = url,
    requestHeaders = requestHeaders.toJson(),
    requestBody = requestBody,
    responseCode = responseCode,
    responseHeaders = responseHeaders.toJson(),
    responseBody = responseBody,
    success = success,
    errorMessage = errorMessage,
    durationMs = durationMs,
)
