# Dynatrace Pipeline Architecture - Presentation FAQ

This document contains key architectural questions and answers to help you prepare for your presentation with the Senior Engineer.

---

### Q1: How does this architecture scale when we add more services (e.g., 20+ microservices)?
**Answer:** It scales perfectly and automatically because the DAG is completely dynamic!
1. **Dynamic DAG:** Our DQL query uses `summarize ... by: {stage}`. We didn't hardcode "3 honeycombs". If we add 17 more microservices tomorrow, as long as they emit an event with their own unique `stage` name, Dynatrace will automatically draw a 20-node Honeycomb DAG. No dashboard code changes required!
2. **Correlation ID:** The only requirement is that the 20+ microservices must pass the `X-Correlation-Id` forward (via HTTP, Kafka, SQS, etc.). As long as that ID is attached to the Business Event, Dynatrace instantly links all 20 hops together.
3. **Single Point of Failure:** With 20 microservices, finding which one dropped a file is difficult. By filtering the DAG by `file_id`, we can instantly see the exact hops the file took, which ones are green, which one is red, and which ones were never reached.

---

### Q2: This pipeline may be used by many services and thousands of files at the same moment. How do we track exactly WHICH file failed by just seeing the dashboard?
**Answer:** In a real production environment, we **do not** filter the main dashboard by a single `file_id`. The main dashboard watches the **aggregate health** of all files. 

Here is the "Find and Trace" workflow used by Operations/SRE teams:
1. **The Alert (Aggregate Monitoring):** The team watches the aggregate dashboard. If the `Overall Failure %` tile spikes, we know there is an issue.
2. **Identifying the File (The Error Log):** The team looks at the **Failures by Stage (Error Log)** table on the dashboard. Because this table explicitly filters for `status == "FAILED"`, it acts as a live feed of broken files. It lists the exact `file_id` of the failures (e.g., `fx-8924`).
3. **Tracing the Individual File (The Deep Dive):** The team copies the `file_id` from the error log, opens a Notebook (or uses a dashboard variable), and pastes the ID into the DAG query. The DAG instantly filters down from thousands of concurrent files to just the hops for that specific file, showing exactly where it broke!

---

### Q3: If the dashboard represents aggregate data, what does the DAG (Honeycomb) show? Do we need to manually trace every file?
**Answer:** **No, you do not trace every file!** You only trace when there is an error.

When the DAG is not filtered by a single `file_id`, it acts as an **Overall Stage Health Map**. 
For an aggregate DAG, the query is adjusted to count failures (e.g., `summarize failed_count = countIf(status == "FAILED")`). 
- If 1,000 files run through the pipeline and all succeed, all 3 honeycombs remain Green.
- If a database issue occurs and 50 files fail at S3, the S3 Honeycomb turns **Red** (because `failed_count > 0`), while S1 and S2 remain Green.

This immediately tells the Operations Team: *"We have a systemic issue at the Database Persistence stage."* They don't need to trace every file; they just need to look at the Error Log to see the specific error messages for those 50 failures and fix the underlying database issue. Individual file tracing is only used for debugging isolated edge cases!

---

### Q4: We send 10 parameters to Dynatrace, but we only want to show 2 on the DAG. How is this handled?
**Answer:** We handle this by strictly separating the JSON payload in the Java application and utilizing Dynatrace's Data Mapping.
1. **Top-Level:** We place `file_type` and `status` at the top level of the JSON payload. By selecting these in the "Names" Data Mapping setting in Dynatrace, they are permanently attached to the surface of the Honeycomb (visible on hover).
2. **Data Block:** The remaining 8 parameters (file size, exact error details, timestamp, etc.) are nested inside a `"data"` JSON object. 
3. **Drill-Down:** When a manager clicks on a specific Honeycomb tile and selects "Open with Notebook" (Drill-Down), Dynatrace opens the raw record, revealing all 10 parameters beautifully formatted.

---

### Q5: What happens when we upload the exact same file multiple times?
**Answer:** The architecture protects itself against data corruption. 
Because the database enforces a unique constraint on the `file_id`, uploading the same file twice triggers a `DataIntegrityViolationException`. 
- S1 (Ingestion) succeeds.
- S2 (Transformation) succeeds.
- S3 (Persistence) catches the duplicate error, safely rejects it, and emits a `FAILED` Business Event with the error detail `DUPLICATE_FILE`.
The DAG correctly reflects this: S1 is Green, S2 is Green, and S3 is Red, proving the pipeline gracefully handled the duplicate without crashing.
