package com.sparjapati.vendorClient.retrofit

import com.sparjapati.vendorClient.exception.VendorApiCircuitOpenException
import com.sparjapati.vendorClient.exception.VendorApiDisabledException
import com.sparjapati.vendorClient.exception.VendorApiRateLimitExceededException
import com.sparjapati.vendorClient.exception.VendorApiTemporarilyDisabledException
import com.sparjapati.vendorClient.response.NetworkExceptionType
import com.sparjapati.vendorClient.response.NetworkResponse
import retrofit2.*
import java.io.IOException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

class RetrofitNetworkCallAdapterFactory private constructor() : CallAdapter.Factory() {

    override fun get(
        returnType: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): CallAdapter<*, *>? {
        if (getRawType(returnType) != Call::class.java) return null
        val callType = getParameterUpperBound(0, returnType as ParameterizedType)
        if (getRawType(callType) != NetworkResponse::class.java) return null
        val responseType = getParameterUpperBound(0, callType as ParameterizedType)
        return NetworkResponseAdapter<Any>(responseType)
    }

    companion object {
        fun create(): CallAdapter.Factory = RetrofitNetworkCallAdapterFactory()
    }
}

private class NetworkResponseAdapter<T>(private val responseType: Type) : CallAdapter<T, Call<NetworkResponse<T>>> {
    override fun responseType(): Type = responseType
    override fun adapt(call: Call<T>): Call<NetworkResponse<T>> = NetworkResponseCall(call)
}

internal class NetworkResponseCall<T>(private val delegate: Call<T>) : Call<NetworkResponse<T>> {
    override fun enqueue(callback: Callback<NetworkResponse<T>>) {
        delegate.enqueue(object : Callback<T> {
            override fun onResponse(call: Call<T>, response: retrofit2.Response<T>) {
                val networkResponse = if (response.isSuccessful) {
                    @Suppress("UNCHECKED_CAST")
                    NetworkResponse.Success(
                        data = response.body() as T,
                        responseCode = response.code(),
                        request = delegate.request(),
                        rawResponse = response,
                    )
                } else {
                    NetworkResponse.Error(
                        message = response.message(),
                        exceptionType = NetworkExceptionType.HTTP,
                        errorBody = response.errorBody()?.string(),
                        responseCode = response.code(),
                        request = delegate.request(),
                        rawResponse = response,
                    )
                }
                callback.onResponse(this@NetworkResponseCall, retrofit2.Response.success(networkResponse))
            }

            override fun onFailure(call: Call<T>, t: Throwable) {
                val networkResponse = NetworkResponse.Error(
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
                    request = delegate.request(),
                    rawResponse = null,
                )
                callback.onResponse(this@NetworkResponseCall, retrofit2.Response.success(networkResponse))
            }
        })
    }

    override fun execute(): retrofit2.Response<NetworkResponse<T>> = throw UnsupportedOperationException()
    override fun isExecuted(): Boolean = delegate.isExecuted
    override fun cancel() = delegate.cancel()
    override fun isCanceled(): Boolean = delegate.isCanceled
    override fun clone(): Call<NetworkResponse<T>> = NetworkResponseCall(delegate.clone())
    override fun request(): okhttp3.Request = delegate.request()
    override fun timeout(): okio.Timeout = delegate.timeout()
}
