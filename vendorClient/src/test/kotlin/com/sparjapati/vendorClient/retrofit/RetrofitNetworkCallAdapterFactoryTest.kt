package com.sparjapati.vendorClient.retrofit

import com.sparjapati.vendorClient.exception.*
import com.sparjapati.vendorClient.response.NetworkExceptionType
import com.sparjapati.vendorClient.response.NetworkResponse
import io.mockk.*
import okhttp3.Request
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import kotlin.test.*

class RetrofitNetworkCallAdapterFactoryTest {

    enum class TestApi : com.sparjapati.vendorClient.VendorApiKey { A }

    private fun callThatFails(t: Throwable): Call<String> = mockk {
        every { enqueue(any()) } answers {
            @Suppress("UNCHECKED_CAST")
            (firstArg() as Callback<String>).onFailure(mockk(), t)
        }
        every { request() } returns Request.Builder().url("https://example.com").build()
        every { timeout() } returns mockk(relaxed = true)
        every { clone() } returns this
    }

    private fun executeFailure(t: Throwable): NetworkResponse.Error {
        val delegate = callThatFails(t)
        val call = NetworkResponseCall<String>(delegate)
        var result: NetworkResponse<String>? = null
        call.enqueue(object : Callback<NetworkResponse<String>> {
            override fun onResponse(call: Call<NetworkResponse<String>>, response: Response<NetworkResponse<String>>) {
                result = response.body()
            }
            override fun onFailure(call: Call<NetworkResponse<String>>, t: Throwable) {}
        })
        return result as NetworkResponse.Error
    }

    @Test fun `IOException maps to NETWORK`() =
        assertEquals(NetworkExceptionType.NETWORK, executeFailure(IOException()).exceptionType)

    @Test fun `VendorApiCircuitOpenException maps to CIRCUIT_OPEN`() =
        assertEquals(NetworkExceptionType.CIRCUIT_OPEN, executeFailure(VendorApiCircuitOpenException(TestApi.A)).exceptionType)

    @Test fun `VendorApiRateLimitExceededException maps to RATE_LIMITED`() =
        assertEquals(NetworkExceptionType.RATE_LIMITED, executeFailure(VendorApiRateLimitExceededException("x")).exceptionType)

    @Test fun `VendorApiDisabledException maps to API_DISABLED`() =
        assertEquals(NetworkExceptionType.API_DISABLED, executeFailure(VendorApiDisabledException(TestApi.A)).exceptionType)

    @Test fun `VendorApiTemporarilyDisabledException maps to TEMP_API_DISABLED`() =
        assertEquals(NetworkExceptionType.TEMP_API_DISABLED, executeFailure(VendorApiTemporarilyDisabledException(TestApi.A)).exceptionType)

    @Test fun `unknown Throwable maps to UNEXPECTED`() =
        assertEquals(NetworkExceptionType.UNEXPECTED, executeFailure(RuntimeException("x")).exceptionType)
}
