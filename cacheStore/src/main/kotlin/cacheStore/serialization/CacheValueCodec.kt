package cacheStore.serialization

import com.fasterxml.jackson.databind.ObjectMapper

/** Encodes/decodes cached values through a [CachedValueEnvelope] so a plain `get(key)` can round-trip type. */
internal class CacheValueCodec(private val objectMapper: ObjectMapper) {

    fun encode(value: Any?): String {
        val envelope = if (value == null) {
            CachedValueEnvelope(valueType = null, json = null)
        } else {
            CachedValueEnvelope(valueType = value::class.java.name, json = objectMapper.writeValueAsString(value))
        }
        return objectMapper.writeValueAsString(envelope)
    }

    fun decode(raw: String): Any? {
        val envelope = objectMapper.readValue(raw, CachedValueEnvelope::class.java)
        val valueType = envelope.valueType ?: return null
        val targetClass = try {
            Class.forName(valueType)
        } catch (e: ClassNotFoundException) {
            throw IllegalStateException(
                "Failed to decode cached value: class '$valueType' not found on the classpath",
                e,
            )
        }
        return objectMapper.readValue(envelope.json, targetClass)
    }
}
