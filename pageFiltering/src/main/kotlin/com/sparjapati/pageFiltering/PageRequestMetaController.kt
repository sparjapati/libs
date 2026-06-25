package com.sparjapati.pageFiltering

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Exposes filter/sort/search schema for paginated resources so frontends can build
 * dynamic filter UIs without hardcoding field names or types.
 *
 * Every endpoint annotated with [@PageRequestFilters][PageRequestFilters] is automatically
 * registered under [PageRequestFilters.filterResourceKey].
 */
@RestController
@RequestMapping("/page-meta")
class PageRequestMetaController(
    private val registry: PageRequestMetaRegistry,
) {

    /**
     * Returns filter/sort/search metadata for [resource].
     *
     * @throws IllegalArgumentException if no paginated endpoint is registered for [resource].
     */
    @GetMapping("/{resource}")
    fun getPageMeta(@PathVariable resource: String): PageRequestMetaDto =
        registry.getMeta(resource)
            ?: throw IllegalArgumentException(
                "No page meta registered for resource: '$resource'. " +
                    "Available resources: ${registry.getAll().keys}"
            )

    @GetMapping
    fun getAllPageMeta(): Map<String, PageRequestMetaDto> = registry.getAll()
}
