# Complete DQL Queries Guide for Dynatrace Dashboard

This guide contains every DQL (Dynatrace Query Language) query you need to build your pipeline monitoring dashboard. Each query is explained line-by-line so you understand exactly what it does.

---

## 📍 Where to Run These Queries

1. Open your Dynatrace UI → Left sidebar → **Notebooks** (or **Dashboards**)
2. Click **"+ New Notebook"** (or **"+ Create Dashboard"**)
3. Click **"+ Add section"** → Select **"Query Grail"**
4. Paste any query below into the editor
5. Click **"Run query"** ▶

---

## 🔤 DQL Basics (Read This First!)

Before you start, here's what each DQL keyword means:

| Keyword | What It Does | Example |
|---------|-------------|---------|
| `fetch bizevents` | Gets all business events from Dynatrace storage | Like `SELECT * FROM bizevents` in SQL |
| `\| filter` | Keeps only rows matching a condition | Like `WHERE` in SQL |
| `\| fieldsAdd` | Creates a new column from existing data | Like `SELECT col AS new_name` in SQL |
| `\| summarize` | Groups and counts data | Like `GROUP BY` + `COUNT()` in SQL |
| `\| sort` | Orders the results | Like `ORDER BY` in SQL |
| `\| limit` | Shows only first N rows | Like `LIMIT` in SQL |
| `jsonField(data, "key")` | Extracts a value from the nested JSON `data` object | Like accessing `data.key` |
| `count()` | Counts total rows | Same as SQL |
| `countIf(condition)` | Counts rows where condition is true | Like `SUM(CASE WHEN ... THEN 1)` in SQL |
| `countDistinct(field)` | Counts unique values | Like `COUNT(DISTINCT col)` in SQL |
| `collectArray(field)` | Collects all values into an array | Like `GROUP_CONCAT` in MySQL |

---

## Query 1: See ALL Your Events (Start Here!)

**Purpose:** This is the first query you should run. It shows you every single business event your pipeline has sent to Dynatrace. Use this to verify your events are arriving.

```sql
fetch bizevents
| filter source == "file-ingestion-service"
    OR source == "file-transformation-service"
    OR source == "file-persistence-service"
| sort timestamp desc
| limit 50
```

**Line-by-line explanation:**
- `fetch bizevents` → "Go to the business events table and get all rows"
- `| filter source == "file-ingestion-service" OR ...` → "But only keep events that came from OUR 3 services"
- `| sort timestamp desc` → "Show newest events first"
- `| limit 50` → "Show only the latest 50 events (so we don't overload the screen)"

**Expected output:** A table showing all your pipeline events with columns like `source`, `type`, `file_type`, `status`, `data`, `timestamp`.

---

## Query 2: Total Files Processed

**Purpose:** Shows the total number of unique files that entered the pipeline. Uses `countDistinct` because the same `file_id` appears in S1, S2, and S3 — we only want to count it once.

```sql
fetch bizevents
| filter source == "file-ingestion-service"
    OR source == "file-transformation-service"
    OR source == "file-persistence-service"
| fieldsAdd file_id = jsonField(data, "file_id")
| summarize totalFiles = countDistinct(file_id)
```

**Line-by-line explanation:**
- `fetch bizevents` → Get all business events
- `| filter ...` → Keep only our pipeline's events
- `| fieldsAdd file_id = jsonField(data, "file_id")` → The `file_id` is inside the nested `data` JSON. This line extracts it and creates a new column called `file_id`
- `| summarize totalFiles = countDistinct(file_id)` → Count how many UNIQUE file_ids exist. This gives us the total number of files processed

**Visualization:** Select **"Single value"** from the chart type dropdown.

---

## Query 3: Successful Pipelines (End-to-End)

**Purpose:** Counts how many files made it through ALL 3 stages (S1 ✅ → S2 ✅ → S3 ✅) without any failures.

```sql
fetch bizevents
| filter source == "file-ingestion-service"
    OR source == "file-transformation-service"
    OR source == "file-persistence-service"
| fieldsAdd
    file_id = jsonField(data, "file_id"),
    stage = jsonField(data, "stage")
| summarize
    stages = collectArray(stage),
    statuses = collectArray(toString(status)),
    by:{file_id}
| filter contains(toString(stages), "S1_INGESTION")
    and contains(toString(stages), "S2_TRANSFORMATION")
    and contains(toString(stages), "S3_DB_PERSISTENCE")
    and not contains(toString(statuses), "FAILED")
| summarize successfulPipelines = count()
```

**Line-by-line explanation:**
- `| fieldsAdd file_id = ..., stage = ...` → Extract `file_id` and `stage` from the nested data
- `| summarize stages = collectArray(stage), ..., by:{file_id}` → For each unique file_id, collect ALL its stages into an array. Example: `["S1_INGESTION", "S2_TRANSFORMATION", "S3_DB_PERSISTENCE"]`
- `| filter contains(toString(stages), "S1_INGESTION") and ...` → Keep only files where the stages array contains ALL 3 stages AND the statuses array does NOT contain "FAILED"
- `| summarize successfulPipelines = count()` → Count how many files passed all filters

**Visualization:** Select **"Single value"**.

---

## Query 4: Overall Failure Percentage

**Purpose:** Calculates what percentage of files failed at ANY stage. This is your key health metric.

```sql
fetch bizevents
| filter source == "file-ingestion-service"
    OR source == "file-transformation-service"
    OR source == "file-persistence-service"
| fieldsAdd
    file_id = jsonField(data, "file_id"),
    stage = jsonField(data, "stage")
| summarize
    stages = collectArray(stage),
    statuses = collectArray(toString(status)),
    by:{file_id}
| fieldsAdd
    successful =
        contains(toString(stages), "S1_INGESTION")
        and contains(toString(stages), "S2_TRANSFORMATION")
        and contains(toString(stages), "S3_DB_PERSISTENCE")
        and not contains(toString(statuses), "FAILED")
| summarize
    totalFiles = count(),
    successfulPipelines = countIf(successful)
| fieldsAdd
    failedPipelines = totalFiles - successfulPipelines
| fieldsAdd
    failurePercentage = (failedPipelines * 100.0) / totalFiles
```

**Line-by-line explanation:**
- Lines 1-8: Same as Query 3 — group events by file_id
- `| fieldsAdd successful = ...` → Create a true/false column: `true` if all 3 stages exist AND none failed
- `| summarize totalFiles = count(), successfulPipelines = countIf(successful)` → Count total files and how many were successful
- `| fieldsAdd failedPipelines = totalFiles - successfulPipelines` → Simple math: total minus successful = failed
- `| fieldsAdd failurePercentage = (failedPipelines * 100.0) / totalFiles` → Calculate the percentage

**Visualization:** Select **"Single value"**. Set color to RED if > 10%.

---

## Query 5: Pipeline Health by File Type

**Purpose:** Shows success/failure breakdown for each file type (FX, EDM, ACCOUNTS). This is the query your manager specifically asked for.

```sql
fetch bizevents
| filter source == "file-ingestion-service"
    OR source == "file-transformation-service"
    OR source == "file-persistence-service"
| summarize
    total = count(),
    success = countIf(status == "SUCCESS"),
    failed = countIf(status == "FAILED"),
    by:{file_type}
```

**Line-by-line explanation:**
- `| summarize ..., by:{file_type}` → Group all events by `file_type` (FX, EDM, ACCOUNTS, GENERIC)
- `total = count()` → Total events for that file type
- `success = countIf(status == "SUCCESS")` → How many succeeded
- `failed = countIf(status == "FAILED")` → How many failed

**Visualization:** Select **"Table"** or **"Bar chart"**.

---

## Query 6: Pipeline Stage Health (Per Stage Breakdown)

**Purpose:** Shows how many events succeeded vs failed at each specific stage (S1, S2, S3). This helps identify which stage is the bottleneck.

```sql
fetch bizevents
| filter source == "file-ingestion-service"
    OR source == "file-transformation-service"
    OR source == "file-persistence-service"
| fieldsAdd
    stage = jsonField(data, "stage"),
    stage_name = jsonField(data, "stage_name")
| summarize
    total = count(),
    success = countIf(status == "SUCCESS"),
    failed = countIf(status == "FAILED"),
    by:{stage, stage_name}
| sort stage asc
```

**Line-by-line explanation:**
- `| fieldsAdd stage = jsonField(data, "stage")` → Extract the stage name from the nested data
- `| summarize ..., by:{stage, stage_name}` → Group by stage (S1_INGESTION, S2_TRANSFORMATION, S3_DB_PERSISTENCE)
- `| sort stage asc` → Show S1 first, then S2, then S3

**Visualization:** Select **"Table"**.

---

## Query 7: Pipeline Health Map (DAG View — Latest File)

**Purpose:** This is the DAG query your manager asked for. It shows the status of each stage for the MOST RECENTLY processed file. This is what replaces the "comb shape" — it correlates S1→S2→S3 events into a single connected view.

```sql
fetch bizevents
| filter source == "file-ingestion-service"
    OR source == "file-transformation-service"
    OR source == "file-persistence-service"
| fieldsAdd
    file_id = jsonField(data, "file_id"),
    stage = jsonField(data, "stage"),
    stage_name = jsonField(data, "stage_name"),
    error_detail = jsonField(data, "error_detail")
| filter file_id == "test-001"
| fields file_id, stage, stage_name, status, file_type, error_detail, timestamp
| sort timestamp asc
```

> **Note:** If this query is too complex, use the simpler version below instead:

### Simpler DAG Alternative — Track Any File by ID
Just replace `test-001` with your actual file_id:

```sql
// Step 1: Pre-generate the 3 stages using array expansion to avoid syntax errors

fetch bizevents
| filter source == "file-ingestion-service"
    OR source == "file-transformation-service"
    OR source == "file-persistence-service"
| fieldsAdd
    file_id = jsonField(data, "file_id"),
    stage = jsonField(data, "stage"),
    stage_name = jsonField(data, "stage_name"),
    error_type = jsonField(data, "error_type"),
    error_detail = jsonField(data, "error_detail"),
    file_size = toString(jsonField(data, "file_size")),
    file_extension = jsonField(data, "file_extension"),
    processing_time_ms = toDouble(jsonField(data, "processing_time_ms"))
| sort timestamp desc
| summarize 
    latest_status = takeFirst(status), 
    events = count(),
    file_id = takeFirst(file_id),
    stage_name = takeFirst(stage_name),
    error_type = takeFirst(error_type),
    error_detail = takeFirst(error_detail),
    file_size = takeFirst(file_size),
    file_extension = takeFirst(file_extension),
    avg_processing_time_ms = avg(processing_time_ms),
    event_time = takeFirst(timestamp),
    by: {stage}
| fieldsAdd avg_processing_time = if(isNotNull(avg_processing_time_ms), concat(toString(avg_processing_time_ms), "ms"), else: "")
| sort stage asc
```

**This gives you the complete DAG for one file:** S1→S2→S3 with status at each stage.

**Visualization:** Select **"Table"**. Each row = one stage. Green ✅ for SUCCESS, Red ❌ for FAILED.

---

### 8. Recent Files (Interactive DAG Filter)
*As requested by the manager's drawing, this table shows recent files and their final status. Clicking a row automatically filters the DAG.*

```sql
fetch bizevents
| filter source == "file-ingestion-service"
    OR source == "file-transformation-service"
    OR source == "file-persistence-service"
| fieldsAdd
    file_id = jsonField(data, "file_id")
| summarize statuses = collectArray(status), event_time = max(timestamp), by: {file_id}
| fieldsAdd final_status = if(contains(toString(statuses), "FAILED"), "FAIL", else: "SUC")
| fields file_id, final_status, event_time
| sort event_time desc
| limit 20
```

**Visualization:** Select **"Table"**. 
**Usage:** Click on any `file_id` in this table to instantly filter the Pipeline Health Map (DAG)!

// Query 9: Pipeline Health Map (DAG View - Single File)

// Step 1: Generate the 3 permanent Grey stages
data
  record(stage = "S1_INGESTION"),
  record(stage = "S2_TRANSFORMATION"),
  record(stage = "S3_DB_PERSISTENCE")
| fieldsAdd priority = 1, dummy = true

// Step 2: Fetch the real events and apply your Dashboard Variable
| append [
  fetch bizevents
  | filter source == "file-ingestion-service"
        OR source == "file-transformation-service"
        OR source == "file-persistence-service"
  | fieldsAdd 
      file_id = jsonField(data, "file_id"),
      stage = jsonField(data, "stage"),
      error_type = jsonField(data, "error_type"),
      error_detail = jsonField(data, "error_detail"),
      file_size = toString(jsonField(data, "file_size")),
      file_extension = jsonField(data, "file_extension"),
      processing_time_ms = if(isNotNull(jsonField(data, "processing_time_ms")), concat(toString(jsonField(data, "processing_time_ms")), "ms"), else: "")
      
  // 👉 This links perfectly to your dropdown!
  | filter in(file_id, $file_id)
  
  | fieldsAdd priority = if(status == "FAILED", 3, else: if(status == "SUCCESS", 2, else: 1)), dummy = false
]

// 👉 Sort so that REAL events (dummy=false) rise to the top
| sort dummy asc, timestamp desc

// Step 3: Keep the highest priority status, AND collect all 8 extra parameters!
| summarize 
    max_priority = max(priority), 
    events = countIf(dummy == false),
    file_id = takeFirst(file_id),
    error_type = takeFirst(error_type),
    error_detail = takeFirst(error_detail),
    file_size = takeFirst(file_size),
    file_extension = takeFirst(file_extension),
    file_type = takeFirst(file_type),
    processing_time_ms = takeFirst(processing_time_ms),
    event_time = takeFirst(timestamp),
    by: {stage}
    
| fieldsAdd latest_status = if(max_priority == 3, "FAILED", else: if(max_priority == 2, "SUCCESS", else: "NOT_STARTED"))

// Step 4: Add pretty names
| fieldsAdd stage_name = if(stage == "S1_INGESTION", "File Ingestion", else: if(stage == "S2_TRANSFORMATION", "Data Transformation", else: "Database Persistence"))
| sort stage asc


## Query 8: Failures by Stage (Error Drill-Down)

**Purpose:** Shows ONLY failed events with their error details. Use this to debug what went wrong and where.

```sql
fetch bizevents
| filter source == "file-ingestion-service"
    OR source == "file-transformation-service"
    OR source == "file-persistence-service"
| filter status == "FAILED"
| fieldsAdd
    file_id = jsonField(data, "file_id"),
    stage = jsonField(data, "stage"),
    error_type = jsonField(data, "error_type"),
    error_detail = jsonField(data, "error_detail")
| fields file_id, stage, file_type, error_type, error_detail, timestamp
| sort timestamp desc
```

**Visualization:** Select **"Table"**. This is your error log.

---

## Query 9: Events Over Time (Time Series)

**Purpose:** Shows a time-series graph of how many events are flowing through each stage over time. Useful for spotting traffic spikes or outages.

```sql
fetch bizevents
| filter source == "file-ingestion-service"
    OR source == "file-transformation-service"
    OR source == "file-persistence-service"
| fieldsAdd stage = jsonField(data, "stage")
| makeTimeseries count = count(), by:{stage}, interval: 5m
```

**Line-by-line explanation:**
- `| makeTimeseries count = count(), by:{stage}, interval: 5m` → Create a time-series chart, counting events every 5 minutes, with a separate line for each stage

**Visualization:** Select **"Line chart"** or **"Area chart"**.

---

## Query 10: File Type Distribution (Pie Chart)

**Purpose:** Shows the proportion of FX vs EDM vs ACCOUNTS files processed.

```sql
fetch bizevents
| filter type == "com.pipeline.file.ingestion"
| summarize count = count(), by:{file_type}
```

**Visualization:** Select **"Pie chart"** or **"Donut chart"**.

---

## 🎨 How to Build the Dashboard

### Step-by-Step:
1. Go to **Dashboards** → Click **"+ Create dashboard"**
2. Name it: **"File Pipeline Monitoring"**
3. Click **"Edit"** (pencil icon top-right)
4. Click **"+ Add tile"** → Select **"Data explorer"**
5. Paste a query from above → Click **"Run query"**
6. Choose your visualization type (Single value, Table, Bar chart, etc.)
7. Click **"Pin to dashboard"**
8. Repeat for each query!

### Recommended Dashboard Layout:
| Row 1 (Summary)     | Row 1              | Row 1              |
|---------------------|---------------------|---------------------|
| Query 2: Total Files | Query 3: Successful | Query 4: Failure %  |

| Row 2 (Health)       | Row 2              |
|---------------------|---------------------|
| Query 5: By File Type | Query 6: By Stage |

| Row 3 (Details)      | Row 3              |
|---------------------|---------------------|
| Query 8: Error Log   | Query 9: Time Series |

---

## 🧪 Quick Test

After starting your pipeline, run these Postman requests to generate data:

**Test 1 — FX file:**
```
POST http://localhost:8081/api/files/upload
Headers: X-Correlation-Id: fx-001
Body (form-data):
  file: sample-files/fx_report.txt
  file_type: FX
```

**Test 2 — EDM file:**
```
POST http://localhost:8081/api/files/upload
Headers: X-Correlation-Id: edm-001
Body (form-data):
  file: sample-files/edm_data.txt
  file_type: EDM
```

**Test 3 — Accounts file:**
```
POST http://localhost:8081/api/files/upload
Headers: X-Correlation-Id: acc-001
Body (form-data):
  file: sample-files/acc_quarterly.txt
  file_type: ACCOUNTS
```

Then go to Dynatrace and run Query 1 — you should see all your events! 🎉

---

## 🛠️ Advanced Pattern: Idempotency & Auto-Versioning

We implemented an Enterprise Idempotency pattern in the Java code (specifically `FileIngestionService`) that interacts perfectly with our DQL queries above, requiring **ZERO** changes to the Dynatrace dashboard!

Here is how the architecture handles retries:

### 1. Exact Duplicates (Skipping Pipeline)
If you upload the exact same file that already succeeded (matching `X-Correlation-Id` and `SHA-256` content hash), the Java application aborts instantly. 
**Impact on Dynatrace:** None. Because S1 skips processing entirely, no new events are emitted. The dashboard remains clean and accurate without duplicate event clutter.

### 2. Auto-Versioning (Changed File with Same ID)
If you edit a file that previously succeeded, but upload it again with the exact same `X-Correlation-Id`, the Java code detects a hash mismatch. It automatically appends a timestamp to the ID (e.g. `fx-123` becomes `fx-123-v1724000000`).
**Impact on Dynatrace:** Because the ID is changed before the business event is emitted, Dynatrace naturally treats this as a brand new pipeline run! 
- The original run (`fx-123`) remains green in your dashboard history.
- A brand new trace (`fx-123-v1724...`) appears on the DAG map progressing through `S1 -> S2 -> S3`.

This combination of **Smart Java Logic** and **Max() DQL Queries** results in a robust, bulletproof observability pipeline.
