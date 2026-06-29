package vendorClient.response

import io.mockk.mockk
import okhttp3.Request
import retrofit2.Response
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkResponseTest {

    private val request: Request = mockk(relaxed = true)

    private fun success(data: String = "ok") = NetworkResponse.Success(
        data = data,
        responseCode = 200,
        request = request,
        rawResponse = mockk<Response<String>>(relaxed = true),
    )

    private fun error() = NetworkResponse.Error(
        message = "failed",
        exceptionType = NetworkExceptionType.NETWORK,
        errorBody = null,
        responseCode = 0,
        request = request,
        rawResponse = null,
    )

    @Test fun `success() is true for Success`() = assertTrue(success().success())
    @Test fun `success() is false for Error`() = assertFalse(error().success())

    @Test fun `map transforms data on Success`() {
        val result = success("hello").map { it.length }
        assertEquals(5, (result as NetworkResponse.Success).data)
    }

    @Test fun `map passes Error through unchanged`() {
        val e = error()
        assertEquals(e, e.map { "ignored" })
    }

    @Test fun `fold calls onSuccess branch`() {
        val result = success("hi").fold(onSuccess = { it.uppercase() }, onError = { "error" })
        assertEquals("HI", result)
    }

    @Test fun `fold calls onError branch`() {
        val result = error().fold(onSuccess = { "ok" }, onError = { it.exceptionType.name })
        assertEquals("NETWORK", result)
    }

    @Test fun `getOrElse returns data on Success`() = assertEquals("ok", success().getOrElse { "x" })
    @Test fun `getOrElse returns default on Error`() = assertEquals("fallback", error().getOrElse { "fallback" })
}
