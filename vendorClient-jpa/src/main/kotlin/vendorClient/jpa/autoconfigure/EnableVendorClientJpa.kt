package vendorClient.jpa.autoconfigure

import org.springframework.context.annotation.Import

/**
 * Enables all vendorClient-jpa beans: [JpaVendorApiConfigProvider], [JpaVendorApiConfigManager],
 * [JpaVendorApiLogSink], and [JpaVendorApiLogQuery].
 *
 * Requires a [VendorApiConfigRepository] and [VendorApiLogRepository] bean in the application context,
 * which are provided automatically when `@EnableJpaRepositories` scans the `vendorClient.jpa.repository`
 * package — included via this annotation.
 *
 * Usage:
 * ```kotlin
 * @SpringBootApplication
 * @EnableVendorClientJpa
 * class MyApplication
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Import(VendorClientJpaAutoConfiguration::class)
annotation class EnableVendorClientJpa
