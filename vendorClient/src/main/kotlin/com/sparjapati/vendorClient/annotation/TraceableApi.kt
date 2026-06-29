package com.sparjapati.vendorClient.annotation

import com.sparjapati.vendorClient.VendorApiKey
import kotlin.reflect.KClass

/**
 * Marks a Retrofit interface method as traceable.
 * The interceptor chain uses this to look up the [VendorApiKey] instance at intercept time.
 *
 * Usage:
 * ```
 * @TraceableApi(api = MyApi::class, name = "STRIPE_CHARGE")
 * @GET("v1/charges/{id}")
 * fun getCharge(@Path("id") id: String): Call<NetworkResponse<ChargeResponse>>
 * ```
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class TraceableApi(
    val api: KClass<out VendorApiKey>,
    val name: String,
)
