# Implementation Plan: Dynatrace File Pipeline — From Zero to Demo

> **Goal:** Build a complete 3-stage File Processing Pipeline (using 3 separate Spring Boot microservices) on your personal laptop, instrument it with Dynatrace Business Events, create a monitoring dashboard, and demo it to your team lead.

---

## Phase 0: Software Installation (Your Laptop)

### Step 0.1 — Install JDK 17
| What | Details |
|------|---------|
| Download | Adoptium JDK 17 Windows x64 `.msi` installer |
| Install | Run the `.msi`, check "Set JAVA_HOME" during install |
| Verify | Open PowerShell → `java -version` → should show `17.x.x` |

### Step 0.2 — Install Maven
| What | Details |
|------|---------|
| Download | Maven 3.9.x Binary zip |
| Install | Extract to `C:\tools\apache-maven-3.9.9` |
| Verify | Run `& "C:\tools\apache-maven-3.9.9\apache-maven-3.9.16\bin\mvn.cmd" -version` |

### Step 0.3 — Install Docker Desktop (for PostgreSQL)
| What | Details |
|------|---------|
| Download | Docker Desktop for Windows |
| Install | Run installer, enable WSL2 backend |
| Verify | PowerShell → `docker --version` |

### Step 0.4 — Install Postman
| What | Details |
|------|---------|
| Download | Postman |
| Why | To send test file uploads and trigger the pipeline |

---

## Phase 1: Dynatrace Setup

### Step 1.1 — Get a Dynatrace Free Trial
1. Go to https://www.dynatrace.com/trial/
2. Sign up with your email (15-day free trial).
3. Save your tenant URL (e.g., `https://abc12345.live.dynatrace.com`).

### Step 1.2 — Create an API Token
1. In Dynatrace UI → **Access Tokens**
2. Click **Generate new token**
3. Name: `file-pipeline-token`
4. Enable scopes: `bizevents.ingest`, `metrics.ingest`, `logs.ingest`, `Read entities`, `Write settings`
5. **Copy the token immediately** and save it.

### Step 1.3 — Install OneAgent on Your Laptop
1. In Dynatrace UI → **Deploy Dynatrace**
2. Click **Start installation** → Select **Windows**
3. Download the installer and run as Administrator.
4. Verify in Dynatrace UI → **Hosts**.

---

## Phase 2: Create the Spring Boot Projects

We use **3 separate microservices**:
1. **file-ingestion-service** (Port 8081)
2. **file-transformation-service** (Port 8082)
3. **file-persistence-service** (Port 8083)

**Dependencies used:** Spring Web, Spring Data JPA, PostgreSQL Driver, Spring Boot Actuator, Lombok, Spring Validation.

---

## Phase 3: Write All Application Code

### Key Design Decisions
- **3 Microservices** — S1 handles file upload, S2 parses the document, S3 saves it to the database.
- **`file_id` (correlation ID)** passed through all 3 stages.
- **Business Events** sent via REST API call to Dynatrace at each stage transition (SUCCESS or FAILED).
- **Database:** PostgreSQL running in Docker on port 5433 to avoid conflicts with any local Windows installations.

---

## Phase 4: Build the Dashboard

We create a Dashboard in Dynatrace with 8 tiles using DQL (Dynatrace Query Language):
1. Total Files Processed
2. Successful Pipelines
3. Overall Failure %
4. Pipeline Stage Health
5. S1 Errors by Type
6. S2 Transformation Errors
7. S3 Database Errors
8. Pipeline Flow Over Time

---

## Phase 5: Set Up Alerts

1. **DB Authentication Failure** — Any `DB_AUTH_FAILURE` event triggers a Critical alert.
2. **High Failure Rate** — Pipeline failure rate > 10% triggers a Warning.

---

## Phase 6: End-to-End Testing with Postman

| Test | What You Send | Expected Result |
|------|--------------|-----------------|
| ✅ Happy Path | Valid `.txt` file | S1→S2→S3 all SUCCESS |
| ❌ Invalid Format | A `.exe` file | S1 FAILED, HTTP 400 |
| ❌ Missing Footer | `.txt` file without `[FOOTER]` | S2 FAILED |
| ❌ Missing Content| `.txt` file without `[CONTENT]`| S2 FAILED |
| ❌ DB Down | Stop PostgreSQL container | S3 FAILED |
