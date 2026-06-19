# Bulk File Processing

A Spring Boot library for processing large CSV and XLSX uploads asynchronously using Spring Batch. Upload a file, get back a `jobId` immediately, and receive results when the job finishes — without blocking the HTTP request or holding the file in memory.

---

## Table of Contents

- [How It Works](#how-it-works)
- [Setup](#setup)
- [Implementing a Processor](#implementing-a-processor)
- [Handling Job Completion](#handling-job-completion)
- [Triggering an Upload](#triggering-an-upload)
- [Result File](#result-file)
- [Internals](#internals)
  - [Request Flow](#request-flow)
  - [File Reading](#file-reading)
  - [Spring Batch Job Structure](#spring-batch-job-structure)
  - [Error Collection](#error-collection)
  - [Thread Model](#thread-model)

---

## How It Works

```
HTTP thread
    │
    ├─ jobId = UUID.randomUUID().toString()
    ├─ executor.submit { batchJobService.launch(file, processorType, jobId) }
    └─ return jobId          ← caller unblocked immediately

Background thread
    │
    ▼
BatchJobService.launch(file, processorType, jobId)
    ├─ validates processorType exists
    ├─ writes file to temp disk location
    └─ runs Spring Batch job synchronously
           │
           ▼
        Spring Batch Job
            ├─ reads rows from CSV or XLSX (streaming, never full file in memory)
            ├─ passes each row through FileProcessor.rowReader()  (transform)
            ├─ passes domain objects through FileProcessor.rowProcessor()  (persist)
            ├─ skips failed rows and records per-row errors
            └─ writes annotated result file with status + failure-reason columns
           │
           ▼
        BatchJobCompletionListener.afterJob()
            ├─ writes result file to disk
            └─ calls FileProcessor.onJobCompleted(BulkJobResult)  ← if implemented
```

`BatchJobService.launch()` runs synchronously and blocks until the job completes. The caller is responsible for submitting it to a background executor — this keeps threading and context-propagation (MDC, request scope) out of the library.

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

`@EnableBulkFileProcessing` registers three beans into the Spring context:

| Bean | Role |
|---|---|
| `FileProcessorRegistry` | Discovers and routes to all `FileProcessor` implementations |
| `FileProcessingJobFactory` | Builds Spring Batch jobs at runtime per upload |
| `BatchJobService` | Accepts uploads, launches jobs, returns `jobId` |

The library requires Spring Batch's `JobRepository` and a `PlatformTransactionManager` to be present — both are auto-configured by Spring Boot's `spring-boot-starter-batch`.

---

## Implementing a Processor

Create a `@Component` that implements `FileProcessor<T>`, where `T` is your domain object:

```kotlin
@Component
class InvoiceUploadProcessor(
    private val invoiceRepository: InvoiceRepository,
) : FileProcessor<Invoice> {

    // Unique key — passed as a request parameter to identify this processor
    override val processorType = "invoice-upload"

    // Rows processed per database transaction (default: 100)
    override val chunkSize = 50

    // Step 1: transform a raw SpreadsheetRow into your domain object
    override fun rowReader() = ItemProcessor<SpreadsheetRow, Invoice> { row ->
        Invoice(
            number  = row["invoice_number"] ?: error("missing invoice_number"),
            amount  = row["amount"]?.toBigDecimal() ?: error("missing amount"),
            dueDate = LocalDate.parse(row["due_date"]),
        )
    }

    // Step 2: persist the transformed objects (called once per chunk)
    override fun rowProcessor() = ItemWriter<Invoice> { chunk ->
        invoiceRepository.saveAll(chunk.items)
    }
}
```

### `SpreadsheetRow`

Each row is a thin wrapper around the spreadsheet data:

| Property | Type | Description |
|---|---|---|
| `rowNumber` | `Int` | 1-based row index, excluding the header |
| `values` | `Map<String, String>` | Column name → cell value (trimmed) |

Access values by column header name: `row["column_name"]` or `row.values["column_name"]`.

### Rules

- `processorType` must be unique across all registered processors — the app will fail to start with a duplicate.
- Throwing from `rowReader()` causes that row to be skipped and its error recorded in the result file; it does not abort the job.
- Throwing from `rowProcessor()` causes all rows in that chunk to be skipped.
- `chunkSize` controls how many rows are processed per database transaction — tune it based on row complexity and DB write latency.

---

## Handling Job Completion

Implement `BulkJobCompletionHandler` alongside `FileProcessor` to receive a callback when your job finishes:

```kotlin
@Component
class InvoiceUploadProcessor(
    private val invoiceRepository: InvoiceRepository,
    private val notificationService: NotificationService,
) : FileProcessor<Invoice>, BulkJobCompletionHandler {

    override val processorType = "invoice-upload"

    override fun rowReader() = ...
    override fun rowProcessor() = ...

    override fun onJobCompleted(result: BulkJobResult) {
        when (result.status) {
            BatchStatus.COMPLETED -> notificationService.send(
                "Upload complete: ${result.writeCount} rows saved, ${result.skipCount} skipped."
            )
            else -> notificationService.send(
                "Upload failed. Check result file: ${result.resultFilePath}"
            )
        }
    }
}
```

### `BulkJobResult`

| Field | Type | Description |
|---|---|---|
| `jobId` | `String` | Unique identifier for this job run |
| `processorType` | `String` | Which processor handled the file |
| `status` | `BatchStatus` | Final Spring Batch status (`COMPLETED`, `FAILED`, etc.) |
| `writeCount` | `Long` | Total rows successfully written |
| `skipCount` | `Long` | Total rows skipped due to errors |
| `resultFilePath` | `String?` | Absolute path to the annotated result file, or `null` if no rows were read |

**Thread safety:** your processor bean is a singleton. If multiple uploads of the same `processorType` run concurrently, `onJobCompleted` will be called from different threads simultaneously — ensure any shared state in your processor is thread-safe.

**Exceptions:** any `Exception` thrown from `onJobCompleted` is re-thrown after logging; it does not affect temp file cleanup or the job's recorded status in Spring Batch's `JobRepository`.

---

## Triggering an Upload

Inject `BatchJobService` into your controller. Generate a `jobId`, submit `launch()` to a background executor, and return the `jobId` immediately:

```kotlin
@RestController
@RequestMapping("/bulk")
class BulkUploadController(
    private val batchJobService: BatchJobService,
    private val executor: ExecutorService,          // your app's executor
) {

    @PostMapping("/upload")
    fun upload(
        @RequestParam file: MultipartFile,
        @RequestParam processorType: String,
    ): String {
        val jobId = UUID.randomUUID().toString()
        executor.submit { batchJobService.launch(file = file, processorType = processorType, jobId = jobId) }
        return jobId
    }
}
```

`launch()` runs synchronously on the background thread and blocks until the job completes. The HTTP thread returns the `jobId` immediately without waiting.

Use `jobId` to correlate logs or notifications back to a specific upload.

---

## Result File

After a job finishes, the library writes an annotated copy of the original file with two extra columns appended to every row:

| Column | Values |
|---|---|
| `status` | `SUCCESS` or `FAILED` |
| `failure-reason` | Error message for failed rows; empty for successful rows |

The result file format matches the upload format — a CSV upload produces a CSV result, an XLSX upload produces an XLSX result.

The absolute path to the result file is available in `BulkJobResult.resultFilePath`. It is `null` only when the file had no data rows (header-only or empty).

---

## Internals

### Request Flow

```
// HTTP thread — caller's responsibility
jobId = UUID.randomUUID().toString()
executor.submit { batchJobService.launch(file, processorType, jobId) }
return jobId

// Background thread
batchJobService.launch(file, processorType, jobId)
    │
    ├─ FileProcessorRegistry.get(processorType)   // validates processor exists
    │
    ├─ Files.createTempFile("bulk-{type}-{jobId}", ".{ext}")
    ├─ file.transferTo(tempFile)
    │
    ├─ JobParametersBuilder
    │     .addString("jobId", ...)
    │     .addString("processorType", ...)
    │     .addString("filePath", ...)
    │     .addString("fileType", ...)
    │     .addLong("startedAt", ...)
    │
    ├─ FileProcessingJobFactory.create(processor, jobId, filePath, fileType)
    ├─ job.execute(jobExecution)                  // blocks until complete
    └─ tempFile.delete()                          // always, in finally block
```

### File Reading

The library uses two readers selected automatically by file extension:

**CSV** (`CsvSpreadsheetReader`)
- Uses Apache Commons CSV
- First row is the header; subsequent rows become `SpreadsheetRow` instances
- Cell values are trimmed

**XLSX** (`XlsxSpreadsheetReader`)
- Uses Apache POI's SAX event model (`XSSFReader` + `XSSFSheetXMLHandler`) — *not* the DOM-based `XSSFWorkbook`
- DOM parsing loads the entire workbook into heap (~5–10× file size); SAX walks the XML incrementally and produces one `SpreadsheetRow` at a time
- Missing cells (sparse rows) default to empty string via `sortedMapOf`
- Only the first sheet is processed

Both readers register each row with `RowResultCollector` as it is read, so the result file can include rows that failed at any stage.

### Spring Batch Job Structure

Each upload creates a brand-new, uniquely named `Job` and `Step`:

```
Job  "job-{jobId}"
 └─ Step  "step-{jobId}"
      ├─ reader:    SpreadsheetItemReader   (reads one SpreadsheetRow at a time)
      ├─ processor: FileProcessor.rowReader()  (SpreadsheetRow → T)
      ├─ writer:    FileProcessor.rowProcessor()  (List<T> → persist)
      ├─ chunkSize: FileProcessor.chunkSize  (default 100)
      ├─ faultTolerant()
      │    .skip(Exception::class)
      │    .skipLimit(Long.MAX_VALUE)         // never abort for row errors
      └─ skipListener: RowSkipListener        // records per-row errors
```

Unique job/step names are required by Spring Batch's `JobRepository` — reusing the same name with different parameters would be treated as a restart of the previous run.

**Chunk processing:** rows are read and buffered up to `chunkSize`, then `rowProcessor()` is called once with the full chunk, all inside one database transaction. If the transaction fails, all rows in the chunk are individually retried and skipped if they fail again.

**Skip behaviour:** `skipLimit(Long.MAX_VALUE)` means a single bad row never aborts the job. Each skipped row's error is recorded by `RowSkipListener` and included in the result file's `failure-reason` column.

### Error Collection

`RowResultCollector` coordinates result file generation across the entire job:

```
During job execution:
  SpreadsheetItemReader.read()
      └─ collector.recordRow(row)     ← streams row to a temp CSV on disk immediately

  RowSkipListener.onSkipInProcess()
      └─ collector.recordError(rowNumber, errorMessage)   ← held in memory (Map<Int, String>)

After job completion:
  BatchJobCompletionListener.afterJob()
      └─ collector.writeResultFile()
              ├─ reads temp CSV row by row
              ├─ looks up error for each row number
              └─ writes annotated output file (CSV via Commons CSV, XLSX via SXSSFWorkbook)
```

The design ensures the full file is never held in memory:
- Rows stream to a temp file during reading (one row at a time)
- Only error messages are held in memory (one string per failed row)
- XLSX output uses `SXSSFWorkbook` with a 100-row in-memory sliding window

### Thread Model

```
HTTP thread
    ├─ generates jobId
    ├─ submits launch() to executor   ──► your executor (library has no internal pool)
    └─ returns jobId immediately

Your executor thread
    ├─ batchJobService.launch(file, processorType, jobId)   // blocks here
    │     ├─ job.execute(jobExecution)
    │     ├─ ... (chunk processing) ...
    │     ├─ BatchJobCompletionListener.afterJob()
    │     │     └─ processor.onJobCompleted(result)
    │     └─ tempFile.delete()
    └─ thread released
```

The library has no internal thread pool — threading is entirely the caller's responsibility. This lets consuming apps propagate MDC, request context, or security context to the background thread using whatever mechanism they already use (`ContextExecutorService`, coroutines, etc.).

The temp file is always deleted in a `finally` block — it is removed whether the job succeeds, fails, or throws unexpectedly.
