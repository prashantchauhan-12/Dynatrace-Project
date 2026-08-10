# File Pipeline Monitoring & Optimization Report

## 1. Executive Summary
We have successfully designed and implemented a comprehensive, real-time observability dashboard in Dynatrace for the File Processing Pipeline (Ingestion -> Transformation -> Persistence). The dashboard provides instant, visual health tracking for all files entering the system, categorized by file type, with full drill-down capabilities for failure analysis.

## 2. Dashboard Accomplishments
The new Dynatrace dashboard features the following capabilities, powered by advanced DQL (Dynatrace Query Language):

### A. Real-Time Pipeline DAGs
We built visual Directed Acyclic Graphs (DAGs) that instantly show the exact stage of files. This includes a tile for tracking a specific file via a dropdown (`$file_id`), and an **Auto-Detect Latest File** tile that automatically hunts down the newest uploaded file without manual intervention.

![Pipeline DAGs](screenshots/dashboard_top.png)

### B. Recent Files & Error Analysis
We created an interactive "Recent Files" tracker. Clicking on any file instantly filters the entire dashboard. We also implemented a "Failures by Stage" log that pulls up critical parameters (including `error_type` and `error_detail`), enabling developers to instantly debug failures without searching through logs.

![Recent Files and Error Analysis](screenshots/dashboard_middle.png)

### C. Categorized Pipeline Heatmaps
We designed multi-file Heatmap matrices that perfectly align 3 distinct pipeline stages (S1, S2, S3) per file. We created dedicated, filtered matrices for **FX**, **EDM**, and **ACCOUNTS** file types, allowing teams to monitor large volumes of concurrent files at a glance.

![Pipeline Heatmaps (All & FX)](screenshots/dashboard_heatmaps_1.png)

![Pipeline Heatmaps (EDM & Accounts)](screenshots/dashboard_heatmaps_2.png)

## 3. Exploration: Enterprise Retriggering & Idempotency
As part of the final requirement, we explored how to handle file re-uploads (retries) gracefully without corrupting the pipeline's history. 

### The Current State
Currently, the pipeline uses an `X-Correlation-Id` header. 
- If omitted, a retry generates a new UUID, creating a brand new pipeline run in the dashboard.
- If included, the retry uses the same `file_id`. Because our Dynatrace queries utilize a `max(status)` aggregation, a subsequent "SUCCESS" will automatically override a previous "FAILED" status in the visual DAG.

### The "Smart Resume" Architecture (Recommended)
To achieve a perfect enterprise-grade retry mechanism without requiring any changes to our Dynatrace dashboard, we recommend implementing **Idempotency (Smart Resumes)** in the Java Spring Boot layer.

**How it works:**
1. A failed file (e.g., failed at `S3_DB_PERSISTENCE`) is re-uploaded via Postman with its original `X-Correlation-Id`.
2. The `FileIngestionService` queries the database and recognizes that `S1` and `S2` were already completed successfully yesterday.
3. The Java application **skips** `S1` and `S2` to save compute resources, and forwards the file directly to `S3`.
4. `S3` completes and emits a single new `SUCCESS` event to Dynatrace.

**Why this is perfect:**
Because our Dynatrace queries use `max(status)`, Dynatrace will automatically combine the old `SUCCESS` events from S1 and S2 with the new `SUCCESS` event from S3. The dashboard Heatmap will instantly turn completely Green, visually representing a fully restored pipeline run. 

This requires **zero** changes to the Dynatrace DQL we just built, while saving massive amounts of compute time on large file retries.
