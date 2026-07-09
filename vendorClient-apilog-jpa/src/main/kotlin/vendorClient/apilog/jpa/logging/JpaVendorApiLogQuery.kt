package vendorClient.apilog.jpa.logging

import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional
import vendorClient.apilog.jpa.mapping.toDto
import vendorClient.apilog.jpa.repository.VendorApiLogRepository
import vendorClient.logging.VendorApiLog
import vendorClient.logging.VendorApiLogPage
import vendorClient.logging.VendorApiLogQuery

open class JpaVendorApiLogQuery(
    private val repository: VendorApiLogRepository,
) : VendorApiLogQuery {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(JpaVendorApiLogQuery::class.java)
    }

    @Transactional(readOnly = true)
    override fun findByRequestIdPrefix(requestIdPrefix: String): List<VendorApiLog> =
        repository.findByRequestIdStartingWith(requestIdPrefix).map { it.toDto() }
            .also { LOGGER.debug("Found {} log(s) for requestId prefix '{}'", it.size, requestIdPrefix) }

    @Transactional(readOnly = true)
    override fun findByApiName(apiName: String, page: Int, pageSize: Int): VendorApiLogPage {
        require(page >= 0) { "JpaVendorApiLogQuery.findByApiName: page must be >= 0, was $page" }
        require(pageSize > 0) { "JpaVendorApiLogQuery.findByApiName: pageSize must be > 0, was $pageSize" }
        val result = repository.findByApiNameOrderByCreatedAtDesc(
            apiName = apiName,
            pageable = PageRequest.of(page, pageSize),
        )
        LOGGER.debug(
            "Found {} log(s) for API '{}' (page={}, size={}, totalElements={})",
            result.content.size, apiName, page, pageSize, result.totalElements,
        )
        return VendorApiLogPage(
            content = result.content.map { it.toDto() },
            totalElements = result.totalElements,
            page = page,
            pageSize = pageSize,
        )
    }
}
