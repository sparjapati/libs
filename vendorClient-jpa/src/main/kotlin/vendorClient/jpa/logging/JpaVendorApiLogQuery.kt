package vendorClient.jpa.logging

import com.sparjapati.vendorClient.logging.VendorApiLog
import com.sparjapati.vendorClient.logging.VendorApiLogPage
import com.sparjapati.vendorClient.logging.VendorApiLogQuery
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional
import vendorClient.jpa.mapping.toDto
import vendorClient.jpa.repository.VendorApiLogRepository

class JpaVendorApiLogQuery(
    private val repository: VendorApiLogRepository,
) : VendorApiLogQuery {

    @Transactional(readOnly = true)
    override fun findByRequestIdPrefix(requestIdPrefix: String): List<VendorApiLog> =
        repository.findByRequestIdStartingWith(requestIdPrefix).map { it.toDto() }

    @Transactional(readOnly = true)
    override fun findByApiName(apiName: String, page: Int, pageSize: Int): VendorApiLogPage {
        require(page >= 0) { "JpaVendorApiLogQuery.findByApiName: page must be >= 0, was $page" }
        require(pageSize > 0) { "JpaVendorApiLogQuery.findByApiName: pageSize must be > 0, was $pageSize" }
        val result = repository.findByApiNameOrderByCreatedAtDesc(
            apiName = apiName,
            pageable = PageRequest.of(page, pageSize),
        )
        return VendorApiLogPage(
            content = result.content.map { it.toDto() },
            totalElements = result.totalElements,
            page = page,
            pageSize = pageSize,
        )
    }
}
