# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Git
      - Never commit automatically. Only commit when the user explicitly asks.

## Kotlin Style

- **Reified generics over `Class<*>`** — prefer `inline fun <reified T>` for type-safe lookups; use `filterIsInstance<T>()` in the body rather than `filter { type.isInstance(it) }`.
- **Named parameters** — always use named parameters in function calls for clarity: `HttpResponse(statusCode = 200, body = "ok")` not `HttpResponse(200, "ok")`.
- **Builtin Builders** — prefer `buildList { }` / `buildMap { }` / `buildSet { }` over `mutableListOf()` + explicit return when building collections, `buildString{ }` for strings
- **Diagnostic exception/log messages** — must include enough context to diagnose without a debugger: which bean/class/field/method was involved, what was expected, what was found. e.g. `"Failed to wire 'OrderService': no bean of type 'UserRepository' for constructor param 'repository'"` not `"No bean of type 'UserRepository'"`.
- **KDoc on every generated function and class** — add `/** ... */` describing what it does, its parameters, and return value where non-obvious.
- **Monetary values use `BigDecimal`** — never `Float` or `Double` for money; precision loss is unacceptable.
- **Logging via SLF4J** — use `LoggerFactory.getLogger(ClassName::class.java)`; never `System.out.println` or `println`.
- **Constructor injection only** — never field injection (`@Autowired` on a field); declare dependencies as constructor parameters so they are immutable and testable.
- **No N+1 queries** — review every repository method that is called inside a loop or on a collection; use `JOIN FETCH`, batch loading, or a single bulk query instead.
- **No hardcoded strings** — values that belong to config (URLs, limits, names) go in `application.yml` or a dedicated constants object; string literals in business logic are a red flag.
- **Error handling and logging** — every caught exception must either be rethrown with context or logged at the appropriate level (warn/error) with enough detail to diagnose without a debugger; swallowing exceptions silently is forbidden.
- **Prefer method references over bean parameters** — utility objects and helper classes (non-Spring components) must not accept Spring bean types as parameters. Instead, accept only the specific operations they need as Kotlin function types and let the caller pass method references. This keeps utilities decoupled from Spring and trivially testable with lambdas. e.g. `saveLog: (VendorApiLogEntity) -> Unit` instead of `repository: VendorApiLogRepository`; call site: `logRepository::save`.
- **Name every database constraint and index** — always supply an explicit `name` on `@Index`, `@UniqueConstraint`, and `@ForeignKey` (via `@JoinColumn(foreignKey = @ForeignKey(name = "..."))`). Never rely on Hibernate-generated names; they are unreadable in migration scripts and error messages. Convention: `idx_{table}_{col(s)}` for indexes, `uq_{table}_{col}` for unique constraints, `fk_{table}_{referenced_table}` for foreign keys. Never use `@Column(unique = true)` — it produces an unnamed constraint; use `@Table(uniqueConstraints = [UniqueConstraint(name = "...", columnNames = [...])])` instead.
- **Use typealiases for named string roles** — when a `String` carries a specific semantic role (e.g. an identifier, a param name), declare a `typealias` in the relevant `utils/` file and use it throughout. This makes signatures self-documenting without a wrapper type. Example: `typealias SortParamName = String` in `utils/filters/PageRequestKeys.kt`. Never leave a raw `String` where a named alias already exists.
- **Use Kotlin precondition functions** — prefer `require(condition) { message }` over `if (!condition) throw IllegalArgumentException(message)`, and `check(condition) { message }` over `if (!condition) throw IllegalStateException(message)`. Use `requireNotNull(value) { message }` / `checkNotNull(value) { message }` instead of manual null checks with throws. The message lambda is evaluated lazily so string interpolation is free when the check passes.