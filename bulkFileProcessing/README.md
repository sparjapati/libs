# Bulk File Processing

A Spring Boot library for processing large CSV and XLSX uploads asynchronously using Spring Batch. Upload a file, get back a `jobId` immediately, and receive a per-row annotated result file when the job finishes — without blocking the HTTP request or holding the file in memory.

---

## Table of Contents

- [How It Works](#how-it-works)
- [Data Flow](#data-flow)
- [Setup](#setup)
- [Implementing a Processor](#implementing-a-processor)
- [Handling Job Completion](#handling-job-completion)
- [Querying Job Status](#querying-job-status)
- [Triggering an Upload](#triggering-an-upload)
- [Result File](#result-file)
- [RowResult — Returning Errors Without Throwing](#rowresult--returning-errors-without-throwing)
- [Internals](#internals)
  - [File Reading](#file-reading)
  - [Chunk Processing](#chunk-processing)
  - [Error Collection and Result File](#error-collection-and-result-file)
  - [Startup Cleanup](#startup-cleanup)
  - [Thread Model](#thread-model)

---

## How It Works

```
HTTP thread
    │
    ├── jobId = UUID.randomUUID().toString()
    ├── executor.submit { batchJobService.launch(file, processorType, jobId) }
    └── return jobId          ← HTTP response sent immediately

Background thread
    │
    ▼
BatchJobService.launch(file, processorType, jobId)
    │
    ├── throws IllegalArgumentException if processorType isn't registered
    └── runs Spring Batch job synchronously (blocks until complete)
               │
               ▼
           Spring Batch Job
               ├── SpreadsheetItemReader reads rows one at a time (streaming, never full file in RAM)
               │       └── each row written to a temp CSV inline file as SUCCESS
               │
               ├── For each chunk of rows:
               │       ├── rowReader(chunk) → Map<SpreadsheetRow, RowResult<T>>
               │       │       ├── RowResult.Failure → error recorded, row excluded from write
               │       │       └── RowResult.Success → domain object T forwarded
               │       │
               │       └── rowProcessor(successMap) → Map<SpreadsheetRow, RowResult<ExtraColumns>>
               │               ├── RowResult.Failure → error recorded in result file
               │               └── RowResult.Success(extras) → extra columns appended to result row
               │
               └── BatchJobCompletionListener.afterJob()
                       ├── ResultFileWriter.write()  ← produces annotated output
                       └── BulkJobCompletionHandler.onJobCompleted(result)  ← if registered
```

---

## Data Flow

This section traces exactly how data moves through each file in the library.

### 1. `BatchJobService` — entry point

The consuming app calls `launch()` on a background thread.

```
BatchJobService.launch(
    sourceFile: File,       ← caller wrote the MultipartFile bytes here
    processorType: String,  ← routes to the right FileProcessor
    jobId: String = <random UUID>, ← caller generated this before submitting to executor
): String                  ← the jobId that was used (caller-supplied or generated)
```

- Looks up the `FileProcessor` from `FileProcessorRegistry` by `processorType`; throws `IllegalArgumentException` if none is registered.
- Builds Spring Batch `JobParameters` (jobId, processorType, filePath, fileType, startedAt).
- Calls `FileProcessingJobFactory.create(...)` to assemble the `Job`.
- Calls `job.execute(jobExecution)` — **blocks** until the job finishes.
- Returns the resolved `jobId` — the caller-supplied value, or the generated UUID if omitted. For an immediate HTTP response, generate and pass `jobId` in yourself rather than relying on the return value.

### 2. `FileProcessingJobFactory` — job assembly

Called once per upload. Produces a uniquely named `Job` and `Step` so concurrent uploads never collide in the `JobRepository`.

Wires together:
- `SpreadsheetItemReader` — reads rows from the source file
- `RowAccumulator` — collects every row and any errors during the job
- `ResultFileWriter` — writes the annotated result file after the job
- A combined writer lambda — calls `rowReader()` then `rowProcessor()`
- `RowSkipListener` — records system-level exceptions (DB failures, etc.) as row errors
- `BatchJobCompletionListener` — fires after the job finishes

### 3. `SpreadsheetItemReader` → `RowAccumulator` — reading

Spring Batch calls `reader.read()` once per row. Internally delegates to either `CsvSpreadsheetReader` or `XlsxSpreadsheetReader` based on file extension.

Every row is immediately passed to `RowAccumulator.recordRow(row)`:

```
SpreadsheetItemReader.read()
    └── accumulator.recordRow(row)
            └── writes row to an inline temp CSV file, pre-stamped SUCCESS
```

The inline file is a CSV regardless of input format and acts as the working copy for the result file. All rows are on disk at this point — nothing is held in memory except error messages and extra column values.

### 4. Writer lambda — `rowReader()` + `rowProcessor()`

Spring Batch calls the writer once per chunk with `chunkSize` rows (default: 100).

```
writer.write(chunk: List<SpreadsheetRow>)
    │
    ├── rowReader(chunk)
    │       returns Map<SpreadsheetRow, RowResult<T>>
    │       │
    │       ├── RowResult.Failure(error)
    │       │       └── accumulator.recordError(row.rowNumber, error)
    │       │               held in memory: errors: HashMap<Int, String>
    │       │
    │       └── RowResult.Success(domainObject)
    │               └── added to successMap
    │
    └── rowProcessor(successMap)   ← only called if successMap is non-empty
            returns Map<SpreadsheetRow, RowResult<ExtraColumns>>
            │
            ├── RowResult.Failure(error)
            │       └── accumulator.recordError(row.rowNumber, error)
            │
            └── RowResult.Success(extras)
                    └── accumulator.recordExtra(row.rowNumber, extras)
                            extras appended as additional columns in the result file
```

If either `rowReader` or `rowProcessor` **throws** (a system error, not a business error):
- Spring Batch rolls back the chunk transaction
- Retries each row individually (single-row chunks)
- If the single-row call still throws → `RowSkipListener.onSkipInWrite(row, t)` → `accumulator.recordError(row.rowNumber, error)`

### 5. `BatchJobCompletionListener.afterJob()` — post-job

Fires after every job, success or failure.

```
afterJob(jobExecution)
    │
    ├── writer.write()
    │       │
    │       ├── [CSV, no errors, no extras] — fast path: inline file moved to result path (0 extra passes)
    │       │
    │       ├── [CSV, errors or extras present] — re-reads inline file, stamps error rows FAILED,
    │       │       appends extra columns
    │       │
    │       └── [XLSX input] — converts inline CSV → XLSX via SXSSFWorkbook (100-row sliding window)
    │
    ├── builds BulkJobResult { jobId, processorType, status, writeCount, skipCount, resultFilePath }
    │
    └── BulkJobCompletionHandler.onJobCompleted(result)  ← if a handler is registered for this processorType
```

### 6. `RowAccumulator` + `ResultFileWriter` — data collection and result file generation

**`RowAccumulator`** holds three pieces of state during the job:
- `inlineFile` — temp CSV on disk written during reading (every row pre-stamped SUCCESS)
- `errors: HashMap<Int, String>` — rowNumber → error message (in-memory only)
- `rowExtras: HashMap<Int, ExtraColumns>` — rowNumber → extra column values (in-memory only)

**`ResultFileWriter`** consumes the accumulator after the job to write the output:

Result file location: `{resultBaseDir}/{processorType}/{date}/result-{originalFileName}.{ext}`

At `write()`:
- If no errors, no extras, and CSV: moves the inline file to the result path directly (single pass total — no rewrite)
- Otherwise: reads the inline file row-by-row, checks each row number against the error map and extras map, writes annotated output file with `status` / `failure-reason` / extra columns

### File-to-responsibility map

| File | Responsibility |
|---|---|
| `BatchJobService` | Entry point — validates processor, builds job params, executes job |
| `FileProcessingJobFactory` | Assembles Job + Step per upload; wires reader, writer, listener |
| `SpreadsheetItemReader` | Delegates to CSV or XLSX reader; feeds rows to the accumulator |
| `CsvSpreadsheetReader` | Streams CSV rows via Apache Commons CSV |
| `XlsxSpreadsheetReader` | Streams XLSX rows via Apache POI SAX (no DOM loading) |
| `RowAccumulator` | Job-time data collection — streams rows to an inline temp file; holds error and extras maps in memory |
| `ResultFileWriter` | Post-job file production — reads inline file, merges errors/extras, writes annotated CSV or XLSX output |
| `FileProcessor<T>` | **You implement this** — `rowReader()` transforms, `rowProcessor()` persists |
| `RowResult<T>` | Return type for both methods — `Success(value)` or `Failure(error)`; `ExtraColumns` variant used by `rowProcessor` |
| `ExtraColumns` | Typealias for `Map<String, String>` — extra columns appended to each result row by `rowProcessor` |
| `BatchJobCompletionListener` | `afterJob()` hook — triggers result file write and completion handler call |
| `BulkJobCompletionHandler` | **You implement this** (optional) — receives `BulkJobResult` after the job |
| `BulkJobCompletionHandlerRegistry` | Maps `processorType → BulkJobCompletionHandler`; validated at startup |
| `FileProcessorRegistry` | Maps `processorType → FileProcessor`; validated at startup |
| `BulkTempFileCleanupRunner` | On startup: deletes stale inline temp files older than 24 h |
| `HasProcessorType` | Shared interface for `processorType` — implemented by both `FileProcessor` and `BulkJobCompletionHandler` |

---

## Setup

Add the dependency:

```kotlin
// build.gradle.kts
implementation("com.sparjapati:bulkFileProcessing:<version>")
```

Enable the library on your main application class or any `@Configuration` class:

```kotlin
@SpringBootApplication
@EnableBulkFileProcessing
class MyApplication
```

`@EnableBulkFileProcessing` imports `BulkFileProcessingConfiguration`, which registers these beans:

| Bean | Role |
|---|---|
| `FileProcessorRegistry` | Discovers and routes to all `FileProcessor` implementations |
| `BulkJobCompletionHandlerRegistry` | Discovers all `BulkJobCompletionHandler` beans |
| `FileProcessingJobFactory` | Builds Spring Batch jobs at runtime per upload |
| `BatchJobService` | Validates processorType, executes the job, returns the resolved `jobId` |
| `BulkTempFileCleanupRunner` | Deletes stale inline temp files on startup |

**No beans are registered unless `@EnableBulkFileProcessing` is present.** The library will not interfere with applications that include it as a dependency without opting in.

The library requires Spring Batch's `JobRepository` and a `PlatformTransactionManager` — both are auto-configured by `spring-boot-starter-batch`.

---

## Implementing a Processor

Create a `@Component` implementing `FileProcessor<T>`, where `T` is your domain object.

Both `rowReader` and `rowProcessor` receive the **full chunk** as a `List` / `Map`, enabling batch DB lookups (e.g. a single `findAllByIdIn` for 100 rows instead of 100 individual queries).

`rowProcessor` returns `Map<SpreadsheetRow, RowResult<ExtraColumns>>`. The `ExtraColumns` value (`Map<String, String>`) is appended as additional columns in the result file — useful for returning generated IDs, status codes, or any other derived data back to the uploader.

```kotlin
@Component
class InvoiceUploadProcessor(
    private val vendorRepository: VendorRepository,
    private val invoiceRepository: InvoiceRepository,
) : FileProcessor<Invoice> {

    override val processorType = "invoice-upload"
    override val chunkSize = 50          // rows per DB transaction (default: 100)
    override val skipLimit = 500L        // abort job if more than 500 rows fail (default: unlimited)

    // Declare the extra column names and their order in the result file (optional but recommended).
    // Without this, column order is inferred from the first non-empty extras map returned.
    override val extraColumns = listOf("invoice_id", "created_at")

    // Called once per chunk: transform rows → domain objects
    override fun rowReader() = { rows: List<SpreadsheetRow> ->

        // One DB call for the whole chunk
        val vendorIds = rows.mapNotNull { it.values["vendor_id"] }
        val vendors = vendorRepository.findAllByIdIn(vendorIds).associateBy { it.id }

        rows.associateWith { row ->
            val vendorId = row.values["vendor_id"]
            val vendor = vendors[vendorId]
            when {
                vendorId.isNullOrBlank() -> RowResult.failure("missing vendor_id")
                vendor == null           -> RowResult.failure("vendor '$vendorId' not found")
                else -> RowResult.success(
                    Invoice(
                        number  = row.values["invoice_number"] ?: return@associateWith RowResult.failure("missing invoice_number"),
                        amount  = row.values["amount"]?.toBigDecimalOrNull() ?: return@associateWith RowResult.failure("invalid amount"),
                        vendor  = vendor,
                        dueDate = LocalDate.parse(row.values["due_date"]),
                    )
                )
            }
        }
    }

    // Called once per chunk: persist successfully transformed objects and return extra columns
    override fun rowProcessor() = { results: Map<SpreadsheetRow, Invoice> ->
        val saved = invoiceRepository.saveAll(results.values.toList()).associateBy { it.sourceId }
        results.toRowResults { _, invoice ->
            val created = saved[invoice.sourceId]
                ?: return@toRowResults RowResult.failure("save returned no entity")
            RowResult.withExtras("invoice_id" to created.id, "created_at" to created.createdAt.toString())
        }
    }
}
```

If there are no extra columns to return, use `allSaved()`:

```kotlin
override fun rowProcessor() = { results: Map<SpreadsheetRow, Invoice> ->
    invoiceRepository.saveAll(results.values.toList())
    results.allSaved()
}
```

### `SpreadsheetRow`

| Property | Type | Description |
|---|---|---|
| `rowNumber` | `Int` | 1-based row index in the source file, excluding the header |
| `values` | `Map<String, String>` | Column name → cell value (trimmed) |

Access values with `row.values["column_name"]`.

### `rowReader()` contract

- Receives the entire chunk as `List<SpreadsheetRow>` — ideal for batch DB lookups.
- Returns `Map<SpreadsheetRow, RowResult<T>>` — one entry per input row.
- `RowResult.failure("reason")` — error is written to the result file; row is excluded from `rowProcessor`.
- `RowResult.success(domainObject)` — row proceeds to `rowProcessor`.
- **Throw an exception** for unexpected system errors — Spring Batch isolates and skips the failing row.

### `rowProcessor()` contract

- Receives only the rows that produced `RowResult.Success` from `rowReader`.
- Returns `Map<SpreadsheetRow, RowResult<ExtraColumns>>` — one entry per input row.
- `RowResult.failure("reason")` — error is written to the result file.
- `RowResult.noExtras()` / `RowResult.withExtras(...)` / `results.allSaved()` / `results.toRowResults { ... }` — row counted as successfully written; optional extra columns appended to the result file.
- **Throw an exception** for system errors — Spring Batch isolates and skips the failing row.

### `rowProcessor` helpers

| Helper | When to use |
|---|---|
| `results.allSaved()` | All rows persisted successfully, no extra columns |
| `RowResult.noExtras()` | Single row succeeded, no extra columns |
| `RowResult.withExtras("col" to value, ...)` | Single row succeeded with inline extra columns |
| `RowResult.withExtras(map)` | Single row succeeded with a pre-built extras map |
| `results.toRowResults { row, value -> ... }` | Per-row result differs (some failures, or row-specific extras) |

### `extraColumns` property

Declare the names and order of extra columns added by `rowProcessor`. Without this, column order is discovered from the first non-empty `ExtraColumns` map returned during the job, which can be non-deterministic when multiple chunks run. Override for stable column ordering:

```kotlin
override val extraColumns = listOf("account_id", "status_code")
```

### Rules

- `processorType` must be unique — the app fails to start on a duplicate.
- `chunkSize` controls rows per DB transaction (default: 100).
- `skipLimit` caps how many rows may be skipped before the job aborts (default: unlimited).

---

## Handling Job Completion

Optionally implement `BulkJobCompletionHandler` to receive a callback when your processor's job finishes. It does **not** need to be the same class as `FileProcessor`.

```kotlin
@Component
class InvoiceJobCompletionHandler(
    private val notificationService: NotificationService,
) : BulkJobCompletionHandler {

    override val processorType = "invoice-upload"

    override fun onJobCompleted(result: BulkJobResult) {
        when (result.status) {
            BatchStatus.COMPLETED -> notificationService.send(
                subject = "Invoice upload complete",
                body    = "${result.writeCount} rows saved, ${result.skipCount} skipped. " +
                          "Result file: ${result.resultFilePath}",
            )
            else -> notificationService.send(
                subject = "Invoice upload failed",
                body    = "Job ${result.jobId} ended with status ${result.status}.",
            )
        }
    }
}
```

Or combine with `FileProcessor` in one class — both work:

```kotlin
@Component
class InvoiceUploadProcessor(...) : FileProcessor<Invoice>, BulkJobCompletionHandler {
    override val processorType = "invoice-upload"
    override fun rowReader() = ...
    override fun rowProcessor() = ...
    override fun onJobCompleted(result: BulkJobResult) { ... }
}
```

### `BulkJobResult`

| Field | Type | Description |
|---|---|---|
| `jobId` | `String` | Unique identifier for this job run |
| `processorType` | `String` | Which processor handled the file |
| `status` | `BatchStatus` | Final Spring Batch status (`COMPLETED`, `FAILED`, etc.) |
| `writeCount` | `Long` | Total rows successfully written |
| `skipCount` | `Long` | Total rows skipped due to exceptions |
| `resultFilePath` | `String?` | Absolute path to the annotated result file, or `null` if no rows were read |

**Thread safety:** handler beans are singletons. If multiple jobs of the same `processorType` run concurrently, `onJobCompleted` is called from different threads — guard any shared state.

**Exceptions:** any exception thrown from `onJobCompleted` is re-thrown after logging. It does not affect temp file cleanup or the job's recorded status in Spring Batch's `JobRepository`.

---

## Querying Job Status

While `BulkJobCompletionHandler` pushes a result to you when a job finishes, `BulkJobStore` lets you *pull* a job's current status at any time — including while it's still running.

```kotlin
@Service
class JobStatusService(private val jobStore: BulkJobStore) {
    fun status(jobId: String): BulkJobRecord? = jobStore.findById(jobId)

    fun recentInvoiceJobs(page: Int, size: Int) = jobStore.findAll(
        processorType = "invoice-upload",
        pageable = PageRequest.of(page, size),
    )
}
```

### `BulkJobStore`

| Method | Returns | Notes |
|---|---|---|
| `save(record)` | — | Upsert by `jobId`. Called internally by the library — you don't call this yourself. |
| `findById(jobId)` | `BulkJobRecord?` | `null` if the job doesn't exist (or was never persisted — see below). |
| `findAll(processorType?, status?, pageable)` | `Page<BulkJobRecord>` | Both filters optional; pass `null` to match all. |

### `BulkJobRecord`

| Field | Type | Description |
|---|---|---|
| `jobId` | `String` | Unique identifier for this job run |
| `processorType` | `String` | Which processor handled the file |
| `status` | `BatchStatus` | `STARTED` while running, terminal status (`COMPLETED`, `FAILED`, etc.) once finished |
| `writeCount` | `Long` | Rows successfully written so far |
| `skipCount` | `Long` | Rows skipped due to errors so far |
| `resultFilePath` | `String?` | Absolute path to the annotated result file; `null` before completion or if no rows were read |
| `errorMessage` | `String?` | Failure reason; non-null only when `status == FAILED` |
| `originalFileName` | `String` | The original uploaded filename |
| `startedAt` | `Long` | Epoch millis when the job was launched |
| `completedAt` | `Long?` | Epoch millis when the job reached a terminal status; `null` while running |

### Choosing a storage backend

`BulkJobStore` has three implementations, selected automatically:

1. **A store adapter module on the classpath** (e.g. `bulkFileProcessing-mysql`) — always wins if present, even if `bulk.job-store.type` is also set.
2. **`bulk.job-store.type: in-memory`** in `application.yml`, if no adapter is present — records are kept in a process-local map, lost on restart, not shared across instances.
3. **Neither (the default)** — `NoOpBulkJobStore`: `save()` discards the record, `findById()`/`findAll()` return nothing. No behavior change for existing consumers who don't need this feature.

See [`bulkFileProcessing-mysql`](../bulkFileProcessing-mysql/README.md) for the durable, queryable option.

---

## Triggering an Upload

Inject `BatchJobService` into your controller. Write the uploaded file to disk (so it survives the HTTP thread), generate a `jobId`, submit `launch()` to a background executor, and return the `jobId` immediately. `jobId` has a default (a random UUID) for callers who don't need it before dispatch, but the immediate-HTTP-response pattern below only works if you generate and pass it in explicitly, as shown:

```kotlin
@RestController
@RequestMapping("/bulk")
class BulkUploadController(
    private val batchJobService: BatchJobService,
    private val executor: ExecutorService,
) {
    @PostMapping("/upload")
    fun upload(
        @RequestParam file: MultipartFile,
        @RequestParam processorType: String,
    ): String {
        val jobId = UUID.randomUUID().toString()

        // Write to disk on the HTTP thread — the MultipartFile stream may be gone by the time
        // the background thread runs.
        val tempFile = Files.createTempFile("upload-$processorType-$jobId", ".${file.originalFilename?.substringAfterLast('.') ?: "csv"}").toFile()
        file.transferTo(tempFile)

        executor.submit {
            try {
                batchJobService.launch(
                    sourceFile    = tempFile,
                    processorType = processorType,
                    jobId         = jobId,
                )
            } finally {
                tempFile.delete()
            }
        }

        return jobId
    }
}
```

`launch()` runs synchronously on the background thread and blocks until the job completes. The HTTP thread returns `jobId` immediately without waiting.

`launch()` throws `IllegalArgumentException` if `processorType` has no registered `FileProcessor`. An uncaught exception inside `executor.submit { ... }` is only seen by the executor's uncaught-exception handling, not the HTTP response already sent. Wrap the call in a `try`/`catch` (e.g. to update a job-status store) to react to that failure.

---

## Result File

After a job finishes, the library writes an annotated copy of the input to a structured directory:

```
{resultBaseDir}/{processorType}/{date}/result-{originalFileName}.{ext}
```

The file contains all original columns plus these appended columns:

| Column | Values |
|---|---|
| `status` | `SUCCESS` or `FAILED` |
| `failure-reason` | Human-readable error for failed rows; empty for successful rows |
| *(extra columns)* | Any key-value pairs returned via `RowResult.withExtras(...)` from `rowProcessor` |

- A CSV upload produces a CSV result; XLSX produces XLSX.
- The absolute path is in `BulkJobResult.resultFilePath`. It is `null` only when the file had zero data rows.
- `RowResult.failure("reason")` from `rowReader` or `rowProcessor` writes the reason into `failure-reason`.
- Rows that throw a system exception (caught by Spring Batch's skip) also appear as FAILED with the exception message.
- Extra columns are appended after `failure-reason`. Column order follows `FileProcessor.extraColumns` if declared, otherwise discovered from the first non-empty extras map.

---

## RowResult — Returning Errors Without Throwing

`RowResult<T>` is a sealed class that separates **expected business errors** from **unexpected system exceptions**.

```kotlin
sealed class RowResult<out T : Any> {
    data class Success<out T : Any>(val value: T) : RowResult<T>()
    data class Failure(val error: String)         : RowResult<Nothing>()

    companion object {
        // rowReader helpers
        fun <T : Any> success(value: T): RowResult<T>    = Success(value)
        fun failure(error: String): RowResult<Nothing>   = Failure(error)

        // rowProcessor helpers
        fun noExtras(): RowResult<ExtraColumns>                          = Success(emptyMap())
        fun withExtras(vararg columns: Pair<String, String>): RowResult<ExtraColumns>
        fun withExtras(columns: ExtraColumns): RowResult<ExtraColumns>
    }
}

// Map extension helpers for rowProcessor
fun <T : Any> Map<SpreadsheetRow, T>.allSaved(): Map<SpreadsheetRow, RowResult<ExtraColumns>>
fun <T : Any> Map<SpreadsheetRow, T>.toRowResults(
    block: (row: SpreadsheetRow, value: T) -> RowResult<ExtraColumns>,
): Map<SpreadsheetRow, RowResult<ExtraColumns>>
```

`ExtraColumns` is a typealias for `Map<String, String>`. Key-value pairs in a `RowResult.Success` from `rowProcessor` are appended as additional columns in the result file.

| Scenario | How to signal it |
|---|---|
| Invalid row value, missing reference, validation error | Return `RowResult.failure("reason")` |
| Row persisted, no extra data to surface | Return `RowResult.noExtras()` or use `results.allSaved()` |
| Row persisted, return extra columns (e.g. generated ID) | Return `RowResult.withExtras("col" to value, ...)` or use `results.toRowResults { ... }` |
| DB unavailable, network error, unexpected NPE | Throw an exception — Spring Batch isolates and skips the row |

- `RowResult.Failure` is recorded immediately, with no exception overhead and no Spring Batch retry cycle.
- Throwing triggers Spring Batch's retry: the chunk is retried item-by-item.

---

## Internals

### File Reading

Two readers are selected automatically by file extension:

**CSV** (`CsvSpreadsheetReader`)
- Uses Apache Commons CSV.
- First row is the header; subsequent rows become `SpreadsheetRow` instances with trimmed cell values.

**XLSX** (`XlsxSpreadsheetReader`)
- Uses Apache POI's SAX event model (`XSSFReader` + `XSSFSheetXMLHandler`) — not the DOM-based `XSSFWorkbook`.
- DOM parsing loads the entire workbook into heap (~5–10× file size). SAX walks the XML incrementally, producing one `SpreadsheetRow` per event.
- Sparse rows (missing cells) default to empty string.
- Only the first sheet is processed.

Both readers call `collector.recordRow(row)` for every row as it is read, so the result file includes rows that failed at any stage.

### Chunk Processing

Spring Batch's fault-tolerant step:

```
Job  "job-{jobId}"
 └── Step  "job-{jobId}"
       ├── reader:     SpreadsheetItemReader     (reads one SpreadsheetRow at a time)
       ├── writer:     combined lambda           (rowReader + rowProcessor per chunk)
       ├── chunkSize:  FileProcessor.chunkSize   (default 100)
       ├── faultTolerant()
       │     .skip(Exception::class)
       │     .skipLimit(FileProcessor.skipLimit) (default: unlimited)
       └── skipListener: RowSkipListener         (records system exceptions as row errors)
```

There is **no separate processor phase** — `rowReader()` (transform) and `rowProcessor()` (persist) are both called inside the writer lambda. This gives both functions access to the full chunk simultaneously.

**On chunk exception:** Spring Batch rolls back the transaction and replays each row individually. For each single-row replay, if the writer throws, `RowSkipListener.onSkipInWrite(row, t)` is called — the library records the error against that specific row number.

**Unique job/step names** are required by Spring Batch's `JobRepository` — reusing the same name with different parameters would be treated as a restart of the previous run.

### Error Collection and Result File

`RowAccumulator` collects data during the job; `ResultFileWriter` produces the output file after it.

**During the job (`RowAccumulator`):**
```
reader.read() → accumulator.recordRow(row)
    └── streams row to inline temp CSV on disk (pre-stamped SUCCESS)

rowReader() returns RowResult.Failure
    └── accumulator.recordError(rowNumber, error)
            errors: HashMap<Int, String>   ← in-memory only

rowProcessor() returns RowResult.Success(extras)
    └── accumulator.recordExtra(rowNumber, extras)
            rowExtras: HashMap<Int, ExtraColumns>  ← in-memory only

rowProcessor() returns RowResult.Failure
    └── accumulator.recordError(rowNumber, error)

RowSkipListener.onSkipInWrite(row, t)
    └── accumulator.recordError(row.rowNumber, t.message)
```

**After the job (`ResultFileWriter.write()`):**

| Condition | Passes | Notes |
|---|---|---|
| CSV, zero errors, zero extras | 1 | Inline file is moved to result path — no rewrite |
| CSV, errors or extras present | 2 | Reads inline CSV, rewrites with corrected `status`/`failure-reason`/extra columns |
| XLSX | 2 always | Reads inline CSV, converts to XLSX via `SXSSFWorkbook` (100-row sliding window) |

Only error messages and extra column values are held in memory — the full file content is always on disk.

### Startup Cleanup

`BulkTempFileCleanupRunner` runs once at startup (`ApplicationRunner`). It scans the system temp directory and deletes:
- Files matching `bulk-inline-*` (inline working files)

…that are older than 24 hours (configurable by overriding the `bulkTempFileCleanupRunner` bean).

This recovers disk space left by temp files from a previous JVM crash or ungraceful shutdown. Result files are written to a structured directory (not the system temp dir) and are not cleaned up automatically.

### Thread Model

```
HTTP thread
    ├── generates jobId
    ├── writes MultipartFile bytes to temp disk file
    ├── submits launch() to your executor
    └── returns jobId immediately

Your executor thread
    ├── batchJobService.launch(sourceFile, processorType, jobId)  ← blocks here
    │     ├── Spring Batch job executes (read → transform → persist, chunk by chunk)
    │     ├── BatchJobCompletionListener.afterJob()
    │     │     ├── ResultFileWriter.write()
    │     │     └── BulkJobCompletionHandler.onJobCompleted(result)
    │     └── returns (job is done)
    └── thread released; caller deletes tempFile in finally block
```

The library has **no internal thread pool** — threading is the caller's responsibility. This lets consuming apps propagate MDC, request context, or security context to the background thread using their existing mechanism (`ContextExecutorService`, virtual threads, coroutines, etc.).
