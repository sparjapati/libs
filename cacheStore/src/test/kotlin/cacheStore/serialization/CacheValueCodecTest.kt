package cacheStore.serialization

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CacheValueCodecTest {

    data class Person(val name: String, val age: Int)

    // A Kotlin-aware ObjectMapper, as any Kotlin/Spring Boot consumer's would be — this codec
    // doesn't work around a caller-supplied mapper lacking jackson-module-kotlin for arbitrary
    // Kotlin data class values (only the internal envelope itself is deserializable without it).
    private val codec = CacheValueCodec(jacksonObjectMapper())

    @Test fun `round-trips a simple value`() {
        val encoded = codec.encode(Person(name = "Ada", age = 30))
        assertEquals(Person(name = "Ada", age = 30), codec.decode(encoded))
    }

    @Test fun `round-trips a cached null`() {
        val encoded = codec.encode(null)
        assertNull(codec.decode(encoded))
    }

    @Test fun `throws when the encoded class no longer exists`() {
        val bogus = ObjectMapper().writeValueAsString(
            CachedValueEnvelope(valueType = "com.example.DoesNotExist", json = "{}")
        )
        assertThrows(IllegalStateException::class.java) { codec.decode(bogus) }
    }
}
