package vendorClient.annotation

import vendorClient.VendorApiKey
import io.mockk.every
import io.mockk.mockk
import okhttp3.Request
import retrofit2.Invocation
import java.lang.reflect.Method
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class VendorApiAnnotationResolverTest {

    enum class TestApi : VendorApiKey { FOO, BAR }

    private fun requestWithAnnotation(name: String): Request {
        val method: Method = mockk()
        every { method.getAnnotation(TraceableApi::class.java) } returns TraceableApi(
            api = TestApi::class,
            name = name,
        )
        val invocation: Invocation = mockk()
        every { invocation.method() } returns method

        return Request.Builder()
            .url("https://example.com")
            .tag(Invocation::class.java, invocation)
            .build()
    }

    private fun requestWithNoAnnotation(): Request {
        val method: Method = mockk()
        every { method.getAnnotation(TraceableApi::class.java) } returns null
        val invocation: Invocation = mockk()
        every { invocation.method() } returns method
        return Request.Builder().url("https://example.com")
            .tag(Invocation::class.java, invocation).build()
    }

    @Test fun `returns null when method has no TraceableApi annotation`() {
        assertNull(VendorApiAnnotationResolver.resolve(requestWithNoAnnotation()))
    }

    @Test fun `resolves known constant by name`() {
        val result = VendorApiAnnotationResolver.resolve(requestWithAnnotation("FOO"))
        assertEquals(TestApi.FOO, result)
    }

    @Test fun `throws with helpful message for unknown constant`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            VendorApiAnnotationResolver.resolve(requestWithAnnotation("MISSING"))
        }
        assert("MISSING" in ex.message!!)
        assert("TestApi" in ex.message!!)
        assert("FOO" in ex.message!!)
    }
}
