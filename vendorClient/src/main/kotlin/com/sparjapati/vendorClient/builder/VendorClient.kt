package com.sparjapati.vendorClient.builder

import com.sparjapati.vendorClient.VendorApiKey
import com.sparjapati.vendorClient.config.VendorApiConfigProvider
import com.sparjapati.vendorClient.config.VendorClientSettings
import com.sparjapati.vendorClient.interceptor.HttpLoggingInterceptor
import com.sparjapati.vendorClient.interceptor.TraceForwardingInterceptor
import com.sparjapati.vendorClient.interceptor.VendorApiLoggingInterceptor
import com.sparjapati.vendorClient.interceptor.VendorApiRateLimitInterceptor
import com.sparjapati.vendorClient.interceptor.VendorApiResilienceInterceptor
import com.sparjapati.vendorClient.logging.LogSink
import com.sparjapati.vendorClient.logging.Slf4jLogSink
import com.sparjapati.vendorClient.logging.VendorApiLogSink
import com.sparjapati.vendorClient.ratelimit.RateLimitStore
import com.sparjapati.vendorClient.retrofit.RetrofitNetworkCallAdapterFactory
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import retrofit2.Retrofit
import java.time.Instant
import java.util.concurrent.TimeUnit

object VendorClient {
    fun builder(): Builder = Builder()

    class Builder {
        private var baseUrl: String? = null
        private var settings: VendorClientSettings = VendorClientSettings()
        private var configProvider: VendorApiConfigProvider? = null
        private var rateLimitStore: RateLimitStore? = null
        private var onTempDisable: ((VendorApiKey, Instant) -> Unit)? = null
        private var resilienceEnabled: Boolean = false
        private var logSink: VendorApiLogSink? = null
        private var httpLogLevel: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.NONE
        private var httpLogSink: LogSink? = null
        private var requestIdProvider: (() -> String?)? = null
        private var okHttpCustomizer: (OkHttpClient.Builder) -> OkHttpClient.Builder = { it }

        fun baseUrl(url: String) = apply { this.baseUrl = url }
        fun settings(settings: VendorClientSettings) = apply { this.settings = settings }
        fun configProvider(provider: VendorApiConfigProvider) = apply { this.configProvider = provider }

        /**
         * Enables rate limiting. [onTempDisable] is called with the API key and cooldown expiry
         * when a limit breach triggers a temporary disable. Wire
         * [com.sparjapati.vendorClient.config.VendorApiConfigManager.tempDisable]
         * directly: `.rateLimiter(store, configManager::tempDisable)`.
         */
        fun rateLimiter(
            store: RateLimitStore,
            onTempDisable: (VendorApiKey, Instant) -> Unit,
        ) = apply {
            this.rateLimitStore = store
            this.onTempDisable = onTempDisable
        }

        /** Enables Resilience4j circuit breaker + retry. Config is read per-API from the [configProvider]. */
        fun resilience() = apply { this.resilienceEnabled = true }

        /** Enables structured audit logging of every annotated request/response via [sink]. */
        fun apiLogging(sink: VendorApiLogSink) = apply { this.logSink = sink }

        /** Enables debug HTTP logging. Defaults to [Slf4jLogSink] if [sink] is omitted. */
        fun httpLogging(
            level: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.BODY,
            sink: LogSink = Slf4jLogSink(LoggerFactory.getLogger(HttpLoggingInterceptor::class.java)),
        ) = apply {
            this.httpLogLevel = level
            this.httpLogSink = sink
        }

        /**
         * Enables trace-id forwarding. [requestIdProvider] returns the current request-id from your
         * context holder (e.g. `{ AaiseHiContext.getRequestContext()?.requestId }`).
         */
        fun trace(requestIdProvider: () -> String?) = apply { this.requestIdProvider = requestIdProvider }

        /** Escape hatch for custom OkHttp configuration (SSL, custom interceptors, etc). Runs after all library interceptors. */
        fun customizeOkHttp(customizer: (OkHttpClient.Builder) -> OkHttpClient.Builder) =
            apply { this.okHttpCustomizer = customizer }

        /**
         * Builds the [Retrofit] instance.
         *
         * Interceptor order is ALWAYS: RateLimit → Resilience → Trace → ApiLogging → HttpLogging,
         * regardless of the order the fluent builder methods were called.
         */
        fun build(): Retrofit {
            val url = requireNotNull(baseUrl) { "VendorClient.builder: baseUrl must be set" }
            val config = requireNotNull(configProvider) { "VendorClient.builder: configProvider must be set" }

            val okHttpBuilder = OkHttpClient.Builder()
                .connectTimeout(settings.connectTimeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(settings.readTimeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(settings.writeTimeoutSeconds, TimeUnit.SECONDS)

            // Fixed order — do not reorder these blocks
            rateLimitStore?.let { store ->
                okHttpBuilder.addInterceptor(
                    VendorApiRateLimitInterceptor(
                        getConfig = config,
                        rateLimitStore = store,
                        onTempDisable = requireNotNull(onTempDisable),
                    )
                )
            }
            if (resilienceEnabled) {
                okHttpBuilder.addInterceptor(VendorApiResilienceInterceptor(config))
            }
            requestIdProvider?.let {
                okHttpBuilder.addInterceptor(TraceForwardingInterceptor(settings, it))
            }
            logSink?.let {
                okHttpBuilder.addInterceptor(
                    VendorApiLoggingInterceptor(
                        settings = settings,
                        logSink = it,
                        requestIdProvider = requestIdProvider ?: { null },
                    )
                )
            }
            httpLogSink?.let {
                okHttpBuilder.addInterceptor(
                    HttpLoggingInterceptor(
                        log = it::log,
                        level = httpLogLevel,
                        sensitiveHeaders = settings.sensitiveHeaders,
                    )
                )
            }

            return Retrofit.Builder()
                .baseUrl(url)
                .client(okHttpCustomizer(okHttpBuilder).build())
                .addCallAdapterFactory(RetrofitNetworkCallAdapterFactory.create())
                .build()
        }
    }
}
