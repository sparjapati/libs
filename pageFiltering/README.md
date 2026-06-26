# PageFiltering

A Spring Boot library that provides annotation-driven pagination, filtering, sorting, and full-text search for Spring MVC controller endpoints. A single `@PageRequestFilters` annotation on a controller parameter resolves all query-string inputs into a typed `PageRequestParams` object, which then converts directly into a JPA `Specification` and `Pageable`.

---

## Table of Contents

- [How It Works](#how-it-works)
- [Setup](#setup)
- [Usage Pattern](#usage-pattern)
  - [1. Define the filter enum](#1-define-the-filter-enum)
  - [2. Define the sort enum](#2-define-the-sort-enum)
  - [3. Define the search enum](#3-define-the-search-enum)
  - [4. Annotate the controller parameter](#4-annotate-the-controller-parameter)
  - [5. Use PageRequestParams in the service](#5-use-pagerequestparams-in-the-service)
- [@PageRequestFilters Parameters](#pagerequestfilters-parameters)
- [Filter Value Types](#filter-value-types)
- [Query Parameters](#query-parameters)
- [Meta Endpoint](#meta-endpoint)
- [Internals](#internals)

---

## How It Works

```
GET /users?filter=status:ACTIVE&filter=age:18,40&sort=name:asc&pageNumber=0&pageSize=20
    │
    └── PageRequestResolver (HandlerMethodArgumentResolver)
            │
            ├── Parses filter strings → Set<Filter>
            ├── Parses sort strings   → Set<SortOrder>
            ├── Parses page/size      → Int, Int
            └── Returns PageRequestParams
                    │
                    ├── .getFilterSpecification() → JPA Specification<T>
                    └── .getPageable()            → Pageable
```

---

## Setup

Add the dependency:

```kotlin
// build.gradle.kts
implementation("com.sparjapati:pageFiltering:<version>")
```

The library is activated automatically via Spring Boot auto-configuration — no `@Enable` annotation is required.

> **Note — argument resolver registration**
>
> Spring MVC does **not** auto-discover `HandlerMethodArgumentResolver` beans from the application context. `PageRequestResolver` must be explicitly registered via `WebMvcConfigurer.addArgumentResolvers`. The auto-configuration does this automatically, but if you are wiring the beans manually (e.g. in a test slice or a non-Boot Spring application) you must register the resolver yourself:
>
> ```kotlin
> @Configuration
> class WebConfig(private val pageRequestResolver: PageRequestResolver) : WebMvcConfigurer {
>     override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
>         resolvers.add(pageRequestResolver)
>     }
> }
> ```

---

## Usage Pattern

### 1. Define the filter enum

Implement `FilterKey` for each filterable field:

```kotlin
enum class UserFilter(
    override val filterQueryParamName: String,
    override val field: String,
    override val dataType: KClass<*>,
    override val filterValueType: KClass<out FilterValue<*>>,
    override val enumClass: KClass<out Enum<*>>? = null,
) : FilterKey {

    STATUS(
        filterQueryParamName = "status",
        field                = "status",
        dataType             = String::class,
        filterValueType      = FilterValue.Single::class,
        enumClass            = UserStatus::class,      // restricts accepted values to enum constants
    ),
    AGE(
        filterQueryParamName = "age",
        field                = "age",
        dataType             = Int::class,
        filterValueType      = FilterValue.Range::class,
    ),
    NAME(
        filterQueryParamName = "name",
        field                = "name",
        dataType             = String::class,
        filterValueType      = FilterValue.Multi::class,
    ),
}
```

### 2. Define the sort enum

Implement `SortParamKey` for each sortable field:

```kotlin
enum class UserSort(
    override val queryParamName: String,
    override val field: String,
) : SortParamKey {
    NAME(queryParamName = "name", field = "name"),
    CREATED_AT(queryParamName = "createdAt", field = "createdAt"),
}
```

### 3. Define the search enum

Implement `SearchableField` for each full-text searchable field. Use an empty enum to disable search:

```kotlin
enum class UserSearch(
    override val field: String,
    override val type: KClass<*>,
) : SearchableField {
    NAME(field = "name", type = String::class),
    EMAIL(field = "email", type = String::class),
}
```

### 4. Annotate the controller parameter

```kotlin
@RestController
@RequestMapping("/users")
class UserController(private val userService: UserService) {

    @GetMapping
    fun listUsers(
        @PageRequestFilters(
            filterResourceKey  = "users",
            filterClass        = UserFilter::class,
            sortParamClass     = UserSort::class,
            searchClass        = UserSearch::class,
            defaultSortParam   = "createdAt",
            defaultSortDirection = "DESC",
            defaultPageSize    = 20,
        )
        params: PageRequestParams,
    ): Page<UserDto> = userService.listUsers(params)
}
```

### 5. Use PageRequestParams in the service

```kotlin
@Service
class UserService(private val userRepository: UserRepository) {

    fun listUsers(params: PageRequestParams): Page<UserDto> {
        val spec     = params.getFilterSpecification<UserEntity>()
        val pageable = params.getPageable()
        return userRepository.findAll(spec, pageable).map { it.toDto() }
    }
}
```

---

## @PageRequestFilters Parameters

| Parameter | Required | Default | Description |
|---|---|---|---|
| `filterResourceKey` | Yes | — | Unique key used to look up metadata at `GET /page-meta/{resource}` |
| `filterClass` | Yes | — | Enum implementing `FilterKey`; defines available filters |
| `sortParamClass` | Yes | — | Enum implementing `SortParamKey`; defines sortable fields |
| `searchClass` | Yes | — | Enum implementing `SearchableField`; use an empty enum to disable |
| `defaultSortParam` | Yes | — | Default sort field (must match a `SortParamKey.queryParamName`) |
| `defaultSortDirection` | No | `"ASC"` | Default sort direction: `"ASC"` or `"DESC"` |
| `defaultPageSize` | No | `20` | Default page size when `pageSize` is absent from the request |
| `queryParamName` | No | `"query"` | Query-string key for the search term |
| `filterQueryParamName` | No | `"filter"` | Query-string key for filter values |
| `sortQueryParamName` | No | `"sort"` | Query-string key for sort selection |
| `pageNumberQueryParam` | No | `"pageNumber"` | Query-string key for page number (0-based) |
| `pageSizeQueryParam` | No | `"pageSize"` | Query-string key for page size (clamped to 1–100) |

---

## Filter Value Types

Specify the value type via `FilterKey.filterValueType`:

### `FilterValue.Single`

Exact-match on a single value:

```
GET /users?filter=status:ACTIVE
```

The generated predicate is `field = value`.

### `FilterValue.Multi`

Match any value in a comma-separated list:

```
GET /users?filter=name:Alice,Bob,Carol
```

The generated predicate is `field IN (value1, value2, ...)`.

### `FilterValue.Range`

Match a value between two comma-separated bounds:

```
GET /users?filter=age:18,40
```

The generated predicate is `field BETWEEN from AND to`.

### Enum restriction

When `FilterKey.enumClass` is set, the library validates that the supplied value(s) match one of the enum constant names. Invalid values produce a `400 Bad Request`.

---

## Query Parameters

Full-text search is enabled by providing a non-empty `searchClass` enum. The `query` parameter (configurable via `queryParamName`) searches across all declared `SearchableField` entries:

```
GET /users?query=alice
```

- **String fields**: case-insensitive prefix match (`LIKE 'alice%'`)
- **Numeric fields**: exact equality match on the numeric value
- **Boolean fields**: exact equality match on `true`/`false`

All matching fields are combined with `OR`. The search predicate is AND-ed with any active filters.

---

## Meta Endpoint

The library automatically exposes filter/sort/search schema for every registered resource so frontends can build dynamic filter UIs without hardcoding field names.

**Get metadata for one resource:**

```
GET /page-meta/users
```

```json
{
  "filters": [
    { "paramName": "status", "dataType": "string", "allowedValues": ["ACTIVE", "INACTIVE"] },
    { "paramName": "age",    "dataType": "int",    "allowedValues": null },
    { "paramName": "name",   "dataType": "string", "allowedValues": null }
  ],
  "sortParams": ["name", "createdAt"],
  "searchable": true,
  "queryParamName": "query",
  "filterQueryParamName": "filter",
  "sortQueryParamName": "sort",
  "pageNumberQueryParam": "pageNumber",
  "pageSizeQueryParam": "pageSize"
}
```

**Get metadata for all registered resources:**

```
GET /page-meta
```

Returns a map keyed by `filterResourceKey`.

---

## Internals

### Beans registered automatically

| Bean | Role |
|---|---|
| `PageRequestResolver` | `HandlerMethodArgumentResolver` that parses query-string parameters into `PageRequestParams` |
| `PageRequestMetaRegistry` | Scans all Spring MVC handler methods at startup; indexes `PageRequestMetaDto` by `filterResourceKey` |
| `PageRequestMetaController` | Exposes `GET /page-meta/{resource}` and `GET /page-meta` |
| `pageFilteringWebMvcConfigurer` | `WebMvcConfigurer` that registers `PageRequestResolver` with Spring MVC |

### Caching

`PageRequestResolver` caches enum constants for `filterClass`, `sortParamClass`, and `searchClass` in a `ConcurrentHashMap` keyed by enum class. Constants are resolved once on the first request for each controller and reused on all subsequent calls.

### Page size bounds

`pageSize` is clamped to the range `[1, 100]`. Values below 1 become 1; values above 100 become 100. `pageNumber` is floored at 0.
