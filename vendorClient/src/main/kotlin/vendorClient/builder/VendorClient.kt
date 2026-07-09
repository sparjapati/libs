package vendorClient.builder

import vendorClient.VendorApiKey
import vendorClient.config.VendorApiConfigProvider
import vendorClient.config.VendorClientSettings
import vendorClient.interceptor.HttpLoggingInterceptor
import vendorClient.interceptor.TraceForwardingInterceptor
import vendorClient.interceptor.VendorApiLoggingInterceptor
import vendorClient.interceptor.VendorApiRateLimitInterceptor
import vendorClient.interceptor.VendorApiResilienceInterceptor
import vendorClient.logging.VendorApiLogSink
import vendorClient.ratelimit.RateLimitStore
import vendorClient.retrofit.RetrofitNetworkCallAdapterFactory
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object VendorClient {
    fun builder(): Builder = Builder()

    class Builder {
        private var baseUrl: String? = null
        private var settings: VendorClientSettings = VendorClientSettings()
        private var configProvider: VendorApiConfigProvider? = null
        private var rateLimitStore: RateLimitStore? = null
        private var onTempDisable: ((VendorApiKey, Long) -> Unit)? = null
        private var resilienceEnabled: Boolean = false
        private var logSink: VendorApiLogSink? = null
        private var httpLogLevel: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.NONE
        private var httpLog: ((String) -> Unit)? = null
        private var requestIdProvider: (() -> String?)? = null
        private var okHttpCustomizer: (OkHttpClient.Builder) -> OkHttpClient.Builder = { it }

        fun baseUrl(url: String) = apply { this.baseUrl = url }
        fun settings(settings: VendorClientSettings) = apply { this.settings = settings }
        fun configProvider(provider: VendorApiConfigProvider) = apply { this.configProvider = provider }

        /**
         * Enables rate limiting. [onTempDisable] is called with the API key and cooldown expiry
         * when a limit breach triggers a temporary disable. Wire
         * [vendorClient.config.VendorApiConfigManager.tempDisable]
         * directly: `.rateLimiter(store, configManager::tempDisable)`.
         */
        fun rateLimiter(
            store: RateLimitStore,
            onTempDisable: (VendorApiKey, Long) -> Unit,
        ) = apply {
            this.rateLimitStore = store
            this.onTempDisable = onTempDisable
        }

        /** Enables Resilience4j circuit breaker + retry. Config is read per-API from the [configProvider]. */
        fun resilience() = apply { this.resilienceEnabled = true }

        /** Enables structured audit logging of every annotated request/response via [sink]. */
        fun apiLogging(sink: VendorApiLogSink) = apply { this.logSink = sink }

        /** Enables HTTP traffic logging. Defaults to an SLF4J info-level logger if [log] is omitted. */
        fun httpLogging(
            level: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.BODY,
            log: (String) -> Unit = LoggerFactory.getLogger(HttpLoggingInterceptor::class.java)::info,
        ) = apply {
            this.httpLogLevel = level
            this.httpLog = log
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
                        logSink = it,
                        requestIdProvider = requestIdProvider ?: { null },
                    )
                )
            }
            httpLog?.let {
                okHttpBuilder.addInterceptor(
                    HttpLoggingInterceptor(
                        log = it,
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
