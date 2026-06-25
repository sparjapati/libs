package com.sparjapati.pageFiltering

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import java.math.BigDecimal

fun <T : Any> PageRequestParams.getFilterSpecification(): Specification<T> =
    Specification { root, _, cb ->
        val predicates = filters.map { filter ->
            when (val fv = filter.filterValue) {
                is FilterValue.Single -> cb.equal(
                    root.get<Any>(filter.filterKey),
                    fv.value
                )

                is FilterValue.Multi -> root.get<Any>(filter.filterKey)
                    .`in`(fv.values)

                is FilterValue.Range -> cb.between(
                    root.get(filter.filterKey),
                    fv.from as Comparable<Any>,
                    fv.to as Comparable<Any>
                )
            }
        }.toMutableSet()
        query?.trim()?.takeIf { it.isNotEmpty() }?.let { q ->
            predicates.add(buildQueryPredicate(cb, root, q, searchableFields))
        }

        cb.and(*predicates.toTypedArray())
    }

fun PageRequestParams.getPageable(): Pageable {
    val sort = if (sort.isEmpty()) {
        Sort.unsorted()
    } else {
        Sort.by(sort.map { order ->
            when (order) {
                is SortOrder.Asc -> Sort.Order(Sort.Direction.ASC, order.fieldKey)
                is SortOrder.Desc -> Sort.Order(Sort.Direction.DESC, order.fieldKey)
            }
        })
    }
    return PageRequest.of(page, size, sort)
}

private fun <T> buildQueryPredicate(
    cb: CriteriaBuilder,
    root: Root<T>,
    query: String,
    searchableFields: Set<SearchableField>,
): Predicate {
    require(searchableFields.isNotEmpty()) {
        "Query parameter is provided but no searchable fields are configured"
    }

    val orPredicates = mutableListOf<Predicate>()
    val numericValue = query.toBigDecimalOrNull()

    searchableFields.forEach { field ->
        when (field.type) {
            String::class -> {
                orPredicates += cb.like(
                    cb.lower(root.get(field.field)),
                    query.lowercase() + "%"
                )
            }

            Int::class, Long::class, Double::class, Number::class -> {
                if (numericValue != null) {
                    orPredicates += cb.equal(
                        root.get<BigDecimal>(field.field),
                        numericValue
                    )
                }
            }

            Boolean::class -> {
                query.toBooleanStrictOrNull()?.let {
                    orPredicates += cb.equal(
                        root.get<Boolean>(field.field),
                        it
                    )
                }
            }
        }
    }

    if (orPredicates.isEmpty()) return cb.disjunction()

    return cb.or(*orPredicates.toTypedArray())
}
