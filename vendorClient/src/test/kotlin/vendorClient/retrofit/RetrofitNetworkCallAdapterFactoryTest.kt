package vendorClient.retrofit

import vendorClient.exception.*
import vendorClient.response.NetworkExceptionType
import vendorClient.response.NetworkResponse
import io.mockk.*
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import kotlin.test.*

class RetrofitNetworkCallAdapterFactoryTest {

    enum class TestApi : vendorClient.VendorApiKey { A }

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

    private fun callThatReturns(response: Response<String>): Call<String> = mockk {
        every { execute() } returns response
        every { request() } returns Request.Builder().url("https://example.com").build()
        every { timeout() } returns mockk(relaxed = true)
        every { clone() } returns this
    }

    @Test fun `execute runs synchronously and maps a successful response`() {
        val delegate = callThatReturns(Response.success("hello"))
        val result = NetworkResponseCall(delegate).execute().body()
        val success = assertIs<NetworkResponse.Success<String>>(result)
        assertEquals("hello", success.data)
    }

    @Test fun `execute maps an unsuccessful response to Error instead of throwing`() {
        val delegate = callThatReturns(Response.error(500, "".toResponseBody()))
        val result = NetworkResponseCall(delegate).execute().body()
        assertEquals(NetworkExceptionType.HTTP, (result as NetworkResponse.Error).exceptionType)
    }

    @Test fun `execute maps a thrown exception to Error instead of propagating it`() {
        val delegate: Call<String> = mockk {
            every { execute() } throws IOException("boom")
            every { request() } returns Request.Builder().url("https://example.com").build()
            every { timeout() } returns mockk(relaxed = true)
            every { clone() } returns this
        }
        val result = NetworkResponseCall(delegate).execute().body()
        assertEquals(NetworkExceptionType.NETWORK, (result as NetworkResponse.Error).exceptionType)
    }
}
