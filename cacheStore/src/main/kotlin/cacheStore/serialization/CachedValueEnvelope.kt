package cacheStore.serialization

/**
 * [valueType] carries the cached value's runtime class name so it can be deserialized back to
 * its original type without the caller supplying one (Spring's [org.springframework.cache.Cache]
 * SPI gives no type context on a plain `get(key)`). `valueType == null` represents a cached
 * `null` (as opposed to "no entry"), mirroring `RedisCacheManager`'s cached-null handling.
 *
 * Known limitation: generic type parameters are erased at runtime (e.g. a cached `List<User>`
 * deserializes as `List<LinkedHashMap<*, *>>`), the same limitation the app's existing
 * `GenericJackson2JsonRedisSerializer`-based `redisCacheManager` already has.
 */
// Deliberately not a `data class` with a primary constructor: Jackson needs a no-arg constructor
// plus bean getters/setters to deserialize this without requiring jackson-module-kotlin to be on
// the *consuming* app's classpath — this envelope is internal plumbing, not something callers
// should need any particular Jackson module installed to round-trip.
internal class CachedValueEnvelope() {
    var valueType: String? = null
    var json: String? = null

    constructor(valueType: String?, json: String?) : this() {
        this.valueType = valueType
        this.json = json
    }
}
