package com.sparjapati.vendorClient.annotation

import com.sparjapati.vendorClient.VendorApiKey
import okhttp3.Request
import retrofit2.Invocation

object VendorApiAnnotationResolver {

    /**
     * Extracts the [VendorApiKey] instance from an OkHttp [Request] tagged with a Retrofit [Invocation].
     * Returns null when the method has no [@TraceableApi] annotation (pass-through).
     * Throws [IllegalArgumentException] when [@TraceableApi.name] does not match any constant —
     * this is a programming error caught at first call.
     */
    fun resolve(request: Request): VendorApiKey? {
        val annotation = request.tag(Invocation::class.java)
            ?.method()
            ?.getAnnotation(TraceableApi::class.java)
            ?: return null

        val constants = annotation.api.java.enumConstants
            ?: throw IllegalArgumentException(
                "VendorApiAnnotationResolver: ${annotation.api.simpleName} is not an enum."
            )

        return constants
            .filterIsInstance<VendorApiKey>()
            .find { it.name == annotation.name }
            ?: throw IllegalArgumentException(
                "VendorApiAnnotationResolver: '${annotation.name}' is not a constant of " +
                    "${annotation.api.simpleName}. Valid values: ${constants.map { (it as VendorApiKey).name }}"
            )
    }
}
