package vendorClient.retrofit

import vendorClient.exception.VendorApiCircuitOpenException
import vendorClient.exception.VendorApiDisabledException
import vendorClient.exception.VendorApiRateLimitExceededException
import vendorClient.exception.VendorApiTemporarilyDisabledException
import vendorClient.response.NetworkExceptionType
import vendorClient.response.NetworkResponse
import retrofit2.*
import java.io.IOException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * Adapts interface methods declared as `fun api(): NetworkResponse<T>` — no `Call<...>` wrapper.
 * [NetworkResponseAdapter.adapt] runs the call synchronously and always returns a [NetworkResponse],
 * mapping both HTTP failures and thrown exceptions to [NetworkResponse.Error] instead of throwing.
 */
class RetrofitNetworkCallAdapterFactory private constructor() : CallAdapter.Factory() {

    override fun get(
        returnType: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): CallAdapter<*, *>? {
        if (getRawType(returnType) != NetworkResponse::class.java) return null
        val responseType = getParameterUpperBound(0, returnType as ParameterizedType)
        return NetworkResponseAdapter<Any>(responseType)
    }

    companion object {
        fun create(): CallAdapter.Factory = RetrofitNetworkCallAdapterFactory()
    }
}

internal class NetworkResponseAdapter<T>(private val responseType: Type) : CallAdapter<T, NetworkResponse<T>> {
    override fun responseType(): Type = responseType

    override fun adapt(call: Call<T>): NetworkResponse<T> =
        try {
            toNetworkResponse(call.execute(), call)
        } catch (t: Throwable) {
            toNetworkResponse(t, call)
        }

    private fun toNetworkResponse(response: Response<T>, call: Call<T>): NetworkResponse<T> =
        if (response.isSuccessful) {
            @Suppress("UNCHECKED_CAST")
            NetworkResponse.Success(
                data = response.body() as T,
                responseCode = response.code(),
                request = call.request(),
                rawResponse = response,
            )
        } else {
            NetworkResponse.Error(
                message = response.message(),
                exceptionType = NetworkExceptionType.HTTP,
                errorBody = response.errorBody()?.string(),
                responseCode = response.code(),
                request = call.request(),
                rawResponse = response,
            )
        }

    private fun toNetworkResponse(t: Throwable, call: Call<T>): NetworkResponse<T> =
        NetworkResponse.Error(
            message = t.message,
            exceptionType = when (t) {
                is VendorApiRateLimitExceededException -> NetworkExceptionType.RATE_LIMITED
                is VendorApiDisabledException -> NetworkExceptionType.API_DISABLED
                is VendorApiTemporarilyDisabledException -> NetworkExceptionType.TEMP_API_DISABLED
                is VendorApiCircuitOpenException -> NetworkExceptionType.CIRCUIT_OPEN
                is IOException -> NetworkExceptionType.NETWORK
                else -> NetworkExceptionType.UNEXPECTED
            },
            errorBody = null,
            responseCode = 0,
            request = call.request(),
            rawResponse = null,
        )
}
