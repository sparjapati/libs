package com.idempotency.support

import com.fasterxml.jackson.databind.ObjectMapper
import java.lang.reflect.Method
import java.security.MessageDigest

class IdempotencySupport(private val objectMapper: ObjectMapper) {

    fun hashArgs(args: Array<Any?>): String {
        val json = objectMapper.writeValueAsString(args)
        val digest = MessageDigest.getInstance("SHA-256").digest(json.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun serialize(value: Any?): String = objectMapper.writeValueAsString(value)

    fun deserialize(method: Method, value: String): Any? {
        val javaType = objectMapper.typeFactory.constructType(method.genericReturnType)
        return objectMapper.readValue(value, javaType)
    }
}
