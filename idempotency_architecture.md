# Enterprise Pipeline Architecture: Idempotency & Auto-Versioning

This document outlines the architecture, integration, and data flow of the three microservices (Ingestion, Transformation, Persistence) and explains how they handle file retries, duplicate uploads, and content modifications using **Smart Idempotency** and **Content Hashing**.

## 1. High-Level Architecture
The system consists of three distinct Spring Boot microservices communicating synchronously via HTTP REST calls:
- **S1 (File Ingestion Service):** Port `8081`. The entry point. Handles format/size validation, generates the SHA-256 fingerprint, and runs the idempotency logic.
- **S2 (File Transformation Service):** Port `8082`. Parses custom text files to extract `[TITLE]`, `[LOGO]`, `[CONTENT]`, and `[FOOTER]` sections.
- **S3 (File Persistence Service):** Port `8083`. Saves the final transformed document into a PostgreSQL database.

All three services emit Business Events directly to Dynatrace, enabling a real-time observability DAG (Directed Acyclic Graph) showing the flow `S1 -> S2 -> S3`.

## 2. The Idempotency Workflow (Smart Resumes)
To handle duplicates without cluttering the Dynatrace dashboard or wasting compute resources, we implemented a highly efficient idempotency strategy driven by the `X-Correlation-Id` and a **SHA-256 Content Hash**.

### The Flow:
1. **File Upload:** A file arrives at S1 (with an optional `X-Correlation-Id` header).
2. **Fingerprinting:** S1 immediately reads the raw byte array and computes a SHA-256 hash of the content.
3. **Status Check:** If an `X-Correlation-Id` was provided, S1 makes an HTTP GET call to S3 (`http://localhost:8083/api/status/{fileId}`) to ask: *"Does this file already exist in the database?"*
4. **Decision Engine:**
   - **Scenario A (New File or Failed Previous Run):** The DB says "No". S1 processes the file normally. It passes the `contentHash` down to S2, which forwards it to S3, which finally saves it to the database.
   - **Scenario B (Exact Duplicate):** The DB says "Yes" and the `contentHash` matches exactly. S1 **aborts** processing immediately, returning a 200 OK without running S2 or S3, and without emitting any new Dynatrace events. This prevents polluting the dashboard!
   - **Scenario C (Auto-Versioning):** The DB says "Yes", but the `contentHash` DOES NOT match (the user edited the file but uploaded it with the exact same ID). S1 **auto-versions** the ID (e.g., `fx-123` becomes `fx-123-v1724000000`) and processes it as a brand new run.

## 3. Microservice Integration & Data Flow

Here is exactly how the data moves across the network and how the Data Transfer Objects (DTOs) are structured to support this feature:

### Stage 1 (Ingestion) -> Stage 2 (Transformation)
- **Method:** HTTP POST to `http://localhost:8082/api/transform`
- **Payload (`IngestionPayload.java`):** Contains `fileId`, `fileName`, `fileSize`, `fileExtension`, `fileType`, `fileContent`, and `contentHash`.
- **Headers:** `X-Correlation-Id`, `X-File-Type`, `Content-Type: application/json`

### Stage 2 (Transformation) -> Stage 3 (Persistence)
- **Method:** HTTP POST to `http://localhost:8083/api/persist`
- **Payload (`TransformedDocument.java` in S2 -> `PersistenceRequest.java` in S3):** Contains the extracted `title`, `logoUrl`, `content`, `footer`, and preserves the `contentHash` sent from S1.
- **Headers:** `X-Correlation-Id`, `X-File-Type`, `Content-Type: application/json`

### Stage 1 (Ingestion) <-> Stage 3 (Persistence) [The Status Check]
- **Method:** HTTP GET to `http://localhost:8083/api/status/{fileId}`
- **Response:** JSON object indicating presence and the hash. Example: 
  `{ "exists": true, "contentHash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855" }`

## 4. Why this is Enterprise-Grade
1. **Zero Wasted Compute:** By failing fast at S1 for exact duplicates, we save CPU cycles in S2 and eliminate unnecessary database connections in S3.
2. **Perfect Observability:** Because exact duplicates don't trigger new Dynatrace events, the dashboard remains clean. Auto-versioned files create entirely new visual nodes, preserving the history of both versions perfectly.
3. **Stateless Intermediate Stages:** By placing the idempotency check at S1 and querying the final authority (S3), we do not need to build complex intermediate tracking databases for S1 or S2.
