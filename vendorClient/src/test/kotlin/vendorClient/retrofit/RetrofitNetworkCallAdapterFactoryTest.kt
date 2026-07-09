package vendorClient.retrofit

import vendorClient.exception.*
import vendorClient.response.NetworkExceptionType
import vendorClient.response.NetworkResponse
import io.mockk.*
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Call
import retrofit2.Response
import java.io.IOException
import kotlin.test.*

class RetrofitNetworkCallAdapterFactoryTest {

    enum class TestApi : vendorClient.VendorApiKey { A }

    private val adapter = NetworkResponseAdapter<String>(String::class.java)

    private fun callThatThrows(t: Throwable): Call<String> = mockk {
        every { execute() } throws t
        every { request() } returns Request.Builder().url("https://example.com").build()
        every { timeout() } returns mockk(relaxed = true)
        every { clone() } returns this
    }

    private fun callThatReturns(response: Response<String>): Call<String> = mockk {
        every { execute() } returns response
        every { request() } returns Request.Builder().url("https://example.com").build()
        every { timeout() } returns mockk(relaxed = true)
        every { clone() } returns this
    }

    private fun adaptFailure(t: Throwable): NetworkResponse.Error =
        adapter.adapt(callThatThrows(t)) as NetworkResponse.Error

    @Test fun `IOException maps to NETWORK`() =
        assertEquals(NetworkExceptionType.NETWORK, adaptFailure(IOException()).exceptionType)

    @Test fun `VendorApiCircuitOpenException maps to CIRCUIT_OPEN`() =
        assertEquals(NetworkExceptionType.CIRCUIT_OPEN, adaptFailure(VendorApiCircuitOpenException(TestApi.A)).exceptionType)

    @Test fun `VendorApiRateLimitExceededException maps to RATE_LIMITED`() =
        assertEquals(NetworkExceptionType.RATE_LIMITED, adaptFailure(VendorApiRateLimitExceededException("x")).exceptionType)

    @Test fun `VendorApiDisabledException maps to API_DISABLED`() =
        assertEquals(NetworkExceptionType.API_DISABLED, adaptFailure(VendorApiDisabledException(TestApi.A)).exceptionType)

    @Test fun `VendorApiTemporarilyDisabledException maps to TEMP_API_DISABLED`() =
        assertEquals(NetworkExceptionType.TEMP_API_DISABLED, adaptFailure(VendorApiTemporarilyDisabledException(TestApi.A)).exceptionType)

    @Test fun `unknown Throwable maps to UNEXPECTED`() =
        assertEquals(NetworkExceptionType.UNEXPECTED, adaptFailure(RuntimeException("x")).exceptionType)

    @Test fun `adapt runs synchronously and maps a successful response`() {
        val result = adapter.adapt(callThatReturns(Response.success("hello")))
        val success = assertIs<NetworkResponse.Success<String>>(result)
        assertEquals("hello", success.data)
    }

    @Test fun `adapt maps an unsuccessful response to Error instead of throwing`() {
        val result = adapter.adapt(callThatReturns(Response.error(500, "".toResponseBody())))
        assertEquals(NetworkExceptionType.HTTP, (result as NetworkResponse.Error).exceptionType)
    }

    @Test fun `adapt maps a thrown exception to Error instead of propagating it`() {
        val result = adapter.adapt(callThatThrows(IOException("boom")))
        assertEquals(NetworkExceptionType.NETWORK, (result as NetworkResponse.Error).exceptionType)
    }
}
