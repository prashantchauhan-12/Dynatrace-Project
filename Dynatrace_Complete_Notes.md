# Dynatrace: Complete Notes (Beginner → Advanced)

> A self-contained study guide. Read top to bottom once, then use it as a reference.
> Last verified against Dynatrace documentation: August 2026.

---

## Table of Contents
1. [What is Dynatrace](#1-what-is-dynatrace)
2. [Core Architecture](#2-core-architecture)
3. [The Modern Data Backend: Grail, OpenPipeline, DQL](#3-the-modern-data-backend-grail-openpipeline-dql)
4. [Core Modules (What There Is to Learn)](#4-core-modules)
5. [Davis AI & Smartscape](#5-davis-ai--smartscape)
6. [PurePath & Distributed Tracing Deep Dive](#6-purepath--distributed-tracing-deep-dive)
7. [Business Events & Business Analytics](#7-business-events--business-analytics)
8. [Alerting: Anomaly Detection, Metric Events, Workflows](#8-alerting)
9. [Dashboards, Notebooks & DQL Cookbook](#9-dashboards-notebooks--dql-cookbook)
10. [Spring Boot / Java Specific Integration](#10-spring-boot--java-specific-integration)
11. [OpenTelemetry with Dynatrace](#11-opentelemetry-with-dynatrace)
12. [Application Security (AppSec)](#12-application-security-appsec)
13. [Glossary of Every Term You'll Hear](#13-glossary)
14. [Structured Learning Path (Day by Day)](#14-structured-learning-path)
15. [Certifications](#15-certifications)
16. [Hands-On Exercises](#16-hands-on-exercises)

---

## 1. What is Dynatrace?

**Monitoring** tells you *something* is wrong ("CPU at 100%").
**Observability** lets you *ask any question* about a system's internal state from the data it produces (metrics, logs, traces).
**Dynatrace** goes one step further — it's an **AI-driven Software Intelligence Platform** that doesn't just collect telemetry, it automatically:
- Maps every host, process, service, and dependency (**Smartscape**)
- Correlates events across that map
- Identifies **root cause**, not just symptoms
- Quantifies **business impact** ("this outage affected 45 users / cost $X")

Example of the difference:
- *Monitoring alert:* "CPU is at 100% on host-42."
- *Dynatrace Problem:* "CPU is at 100% on host-42 **because** `PersistenceService` is blocked waiting on `PostgreSQL`, which is impacting 45 real users trying to upload files."

---

## 2. Core Architecture

### 2.1 OneAgent — the universal collector
- One binary installed per host (VM, bare metal, container host, Kubernetes node).
- Auto-discovers OS, processes, containers, and application runtimes (Java, .NET, Node.js, Python, Go, PHP).
- Injects itself at runtime ("code-level instrumentation") — **no code changes required** for standard frameworks.
- Automatically discovers new processes as they start — no manual config per microservice.

### 2.2 ActiveGate — the secure gateway
- A proxy/routing component that sits between OneAgents and the Dynatrace cluster.
- Used for: environments without direct internet access, extension execution, synthetic monitoring from private networks, secure data forwarding, and reducing the number of direct outbound connections.
- Types: **Environment ActiveGate** (general purpose) and **Environment ActiveGate for Extensions/Synthetic**.

### 2.3 Dynatrace Cluster — the brain
- **SaaS**: Dynatrace-hosted, multi-tenant, on AWS/Azure/GCP. Fastest to start with (what most trials use).
- **Managed**: Self-hosted on the customer's own infrastructure — used by regulated industries (banks, government) needing full data control.

### 2.4 Data flow, end to end
```
Your App / Host / Container
      │  (OneAgent instruments & captures)
      ▼
   ActiveGate (optional secure relay)
      │
      ▼
  OpenPipeline (ingest, enrich, route, transform)
      │
      ▼
     Grail (unified data lakehouse: logs, metrics, traces, events, security & business data)
      │
      ▼
 Davis AI + DQL + Dashboards + Notebooks + Workflows
```

---

## 3. The Modern Data Backend: Grail, OpenPipeline, DQL

This is the part of Dynatrace that changed the most in the last few years — old Dynatrace had separate storage for logs, metrics, and traces. Modern Dynatrace unifies everything.

### 3.1 Grail — the data lakehouse
- A single, schemaless storage engine for **all** data types: logs, metrics, traces, events, security findings, and business data.
- Separates storage from compute and uses a Massive Parallel Processing (MPP) query engine, so you can query **any data any time** without pre-defining indexes.
- No rehydration needed (unlike traditional cold-storage log systems where you "restore" data before querying it).
- Provides fine-grained access control down to the field level (useful for masking PII).

### 3.2 OpenPipeline — the ingest layer
- The **central front door** for all data entering Grail — logs, spans, metrics, business events, security/SDLC events, generic events.
- At ingest time it can: parse, enrich, mask/filter sensitive fields, convert one record type into another (e.g., turn a log line into a business event), route data to different Grail buckets, and even forward a copy to external cloud storage.
- Configured under **Settings → Process and contextualize → OpenPipeline**.
- Recent addition: **Smartscape node/edge stages** inside OpenPipeline, letting you define custom topology (your own entities and relationships) directly from ingested data.

### 3.3 DQL (Dynatrace Query Language)
SQL-like, but uses pipes (`|`) to chain operations — similar in spirit to Splunk SPL or KQL.

```sql
fetch logs
| filter matchesValue(loglevel, "ERROR")
| summarize count(), by: {host.name}
| sort count() desc
```

Common DQL verbs to know:
| Verb | Purpose |
|---|---|
| `fetch` | Choose the data source table (`logs`, `spans`, `events`, `bizevents`, `dt.entity.host`, etc.) |
| `filter` | Keep only matching records |
| `summarize` | Aggregate (count, avg, sum, percentile) grouped `by:` |
| `sort` | Order results |
| `fields` | Select / rename specific fields |
| `limit` | Cap the number of returned rows |
| `parse` | Extract structured fields out of unstructured text (e.g., log lines) |
| `join` | Combine two data sets (e.g., logs + entity metadata) |

DQL is used to build **Dashboards**, **Notebooks**, and **custom alerts**.

---

## 4. Core Modules

Dynatrace is broad — most practitioners specialize. Here's the full map.

### 4.1 Infrastructure Monitoring
- Hosts: CPU, memory, disk I/O, network.
- Containers: Docker, Kubernetes pods, namespaces, workloads.
- Cloud: AWS, Azure, GCP resource monitoring via native integrations.

### 4.2 Application Performance Monitoring (APM)
- **PurePath**: full distributed trace of a single request across every service and database it touches, down to the method and SQL statement.
- **Services**: Dynatrace auto-groups identical processes into a logical "Service" (e.g., `file-ingestion-service`).
- **Database Monitoring**: captures actual SQL text, execution times, and flags slow queries.

### 4.3 Digital Experience Monitoring (DEM)
- **Real User Monitoring (RUM)**: a JS tag (or mobile SDK) tracks real page loads, clicks, JS errors, and Core Web Vitals from actual users' browsers/devices.
- **Synthetic Monitoring**: scripted "robot" users that run your critical flows (login, checkout) on a schedule from multiple global locations, so you know about an outage before a real user hits it.

### 4.4 Log Management & Analytics
- Centralizes logs from every host/container into Grail.
- Query with DQL instead of grepping individual servers.
- **Log module self-monitoring** (introduced 2026) reports on which log sources OneAgent auto-detected and their health.

### 4.5 Business Analytics
- **Business Events (bizevents)**: custom events emitted from your code representing business milestones ("file received," "order placed," "payment failed").
- Lets you correlate a technical outage directly with business impact (revenue lost, files not processed, users blocked).

### 4.6 Application Security (AppSec)
- Because OneAgent runs inside your process, it sees every loaded library/dependency in memory — enabling real-time detection of vulnerable libraries (e.g., a Log4j CVE) without a separate scan.
- Includes Runtime Vulnerability Analytics, Runtime Application Protection, and (via Grail) scheduled DQL-based **security Detections** that continuously hunt for threats across stored data.

### 4.7 Automation / Workflows
- Low-code automation engine that reacts to Problems, Davis findings, or scheduled triggers — e.g., auto-restart a service, post to Slack, open a Jira ticket, or run a remediation script.

---

## 5. Davis AI & Smartscape

**Smartscape** is Dynatrace's real-time, always-up-to-date topology map: every host, process, service, application, and their dependencies, drawn automatically — you never draw this diagram by hand.

**Davis AI** is Dynatrace's built-in AI engine, and it's important to understand it is **deterministic / causal**, not a generative language model:
- It doesn't "guess" the answer the way an LLM predicts the next word.
- It walks the real-time Smartscape topology and applies causal analysis: "the database got slow → that made Service A slow → that made Service B time out → that's what the user saw."
- Result: instead of 4 separate alerts for 4 symptoms, Davis groups them into **one Problem** with a stated root cause.

Dynatrace has also layered generative AI on top for natural-language use cases (asking questions about your environment in plain English, summarizing problems), but the causal root-cause engine remains deterministic — this is a key differentiator worth remembering for interviews/certification.

---

## 6. PurePath & Distributed Tracing Deep Dive

A **PurePath** is the end-to-end trace of one single request/transaction:

```
Browser click "Upload"
   → Load Balancer
      → API Gateway
         → file-ingestion-service   (120ms)
            → persistence-service   (890ms)  ← bottleneck
               → PostgreSQL query   (860ms)  ← root cause: slow SQL
```

Skills to build:
- Open a PurePath and read the **call tree** (nested method calls with timing).
- Identify the "hot" node — the one consuming most of the total request time.
- Drill into the exact **SQL statement**, **HTTP call**, or **exception** at the bottleneck.
- Use **Request Attributes** (see below) to filter/search PurePaths by business meaning, not just technical ID.

### Custom Request Attributes
Key-value metadata you attach to a trace, e.g. `file_id=abc123`, `file_size_mb=42`, `tenant=customerA`. This lets you search: "show me every PurePath where `file_size_mb > 100` and it was slow" — turning generic traces into business-aware ones.

---

## 7. Business Events & Business Analytics

Business Events are how you bridge "the code is doing X" with "the business cares about X."

Typical usage pattern in a Spring Boot service:
```java
// Pseudocode: emitting a business event via the Business Events API
BizEvent event = BizEvent.builder()
    .type("file.processed")
    .attribute("file_id", fileId)
    .attribute("size_mb", sizeMb)
    .attribute("duration_ms", durationMs)
    .attribute("status", "SUCCESS")
    .build();
bizEventsClient.send(event);
```
Once ingested (via OpenPipeline into Grail), you can:
- Build a dashboard: "Files Processed per Hour," "Revenue at Risk During This Outage."
- Correlate a Davis Problem directly against a drop in `bizevents` — proving business impact, not just technical severity.

---

## 8. Alerting

Three layers, from simplest to most powerful:

1. **Davis Problem detection** — automatic, works out of the box on availability/performance anomalies (no config needed).
2. **Metric Events** — you define a threshold or anomaly-detection rule on any metric (e.g., "error rate > 5% for 5 minutes") and choose a notification channel (Slack, PagerDuty, email, ServiceNow, webhook).
3. **Workflows (Automation Engine)** — trigger multi-step remediation: notify → gather diagnostics → attempt auto-remediation → escalate if unresolved.

---

## 9. Dashboards, Notebooks & DQL Cookbook

- **Dashboards**: pinned, shareable visual tiles (charts, single values, tables) built from DQL or metrics — good for "always-on" views for a team or leadership.
- **Notebooks**: ad-hoc, exploratory, mixes DQL queries + markdown text — good for investigating a specific incident.

Cookbook — common DQL patterns:

```sql
-- Top 10 slowest database statements in the last hour
fetch spans
| filter span.kind == "client" and db.system == "postgresql"
| summarize avg(duration), by: {db.statement}
| sort avg(duration) desc
| limit 10
```

```sql
-- Error rate trend by service, last 24h
fetch spans
| filter isNotNull(error)
| summarize count(), by: {service.name, bin(timestamp, 1h)}
```

```sql
-- Business events: files failed vs succeeded today
fetch bizevents
| filter event.type == "file.processed"
| summarize count(), by: {status}
```

---

## 10. Spring Boot / Java Specific Integration

Since Spring Boot is a common stack, here's what to specifically learn:

| Topic | Why it matters |
|---|---|
| **OneAgent auto-instrumentation** | Zero-code: OneAgent detects the JVM and instruments Spring MVC, JDBC, Kafka, etc. automatically. |
| **Dynatrace OneAgent SDK** | For custom code paths OneAgent can't see automatically (e.g., a proprietary queue), you manually wrap calls to create custom PurePath nodes. |
| **Micrometer + Dynatrace** | Spring Boot Actuator exposes metrics via Micrometer; Dynatrace has a Micrometer registry so Actuator metrics (`/actuator/metrics`) flow straight into Dynatrace without extra agents. |
| **Business Events API** | Call directly from your service layer (as shown above) to emit business milestones. |
| **Custom Request Attributes** | Configure in Dynatrace (Settings → Server-side request attributes) to extract values from HTTP headers, params, or Java method arguments into every relevant PurePath. |

---

## 11. OpenTelemetry with Dynatrace

- **OpenTelemetry (OTel)** is the vendor-neutral, industry-standard framework for producing traces, metrics, and logs.
- Dynatrace **natively ingests OTel data** (via OTLP endpoints, often through ActiveGate or OpenPipeline), meaning:
  - If you already instrument your code with OTel SDKs, you don't need OneAgent for that specific telemetry — but OneAgent still adds deeper auto-instrumentation, infrastructure context, and Smartscape mapping that raw OTel alone doesn't give you.
  - Many teams run **both**: OTel for custom/vendor-neutral spans, OneAgent for automatic full-stack coverage.
- Worth learning: OTel Collector configuration, OTLP exporters, and how OTel spans map onto Dynatrace's PurePath/Smartscape model.

---

## 12. Application Security (AppSec)

- **Runtime Vulnerability Analytics**: OneAgent sees every library loaded in memory in real time, so a zero-day (e.g., a new Log4j CVE) is flagged the moment it's known — no separate scan job needed.
- **Runtime Application Protection (RAP)**: can actively block exploit attempts against known vulnerabilities.
- **Detections**: scheduled, DQL-based security queries that continuously scan everything stored in Grail (logs, traces, events) to surface threats — this runs on the same data you're already collecting for observability, no separate SIEM pipeline required.

---

## 13. Glossary

| Term | Meaning |
|---|---|
| OneAgent | The single agent installed per host that auto-discovers and instruments everything. |
| ActiveGate | Secure proxy/gateway between OneAgents and the Dynatrace cluster. |
| Grail | Unified, schemaless data lakehouse storing logs, metrics, traces, events, security & business data. |
| OpenPipeline | The ingestion layer that parses, enriches, masks, routes, and stores data into Grail. |
| DQL | Dynatrace Query Language — pipe-based query syntax used across dashboards, notebooks, alerts. |
| Smartscape | The auto-generated, real-time topology map of your entire environment. |
| Davis AI | Dynatrace's deterministic, causal AI engine for root-cause problem detection. |
| PurePath | An end-to-end distributed trace of a single request. |
| Service | An auto-grouped logical entity representing identical running processes. |
| RUM | Real User Monitoring — tracks actual end users in their browser/app. |
| Synthetic Monitoring | Scripted "robot" checks that simulate user flows on a schedule. |
| Business Event (bizevent) | A custom event representing a business milestone, correlated with technical data. |
| Request Attribute | Custom key-value metadata attached to a PurePath for business-aware filtering. |
| Problem | Davis AI's grouped, root-caused incident (vs. many raw individual alerts). |
| Workflow | Low-code automation triggered by problems/events (Automation Engine). |
| SaaS vs. Managed | Dynatrace-hosted cloud tenant vs. customer's self-hosted cluster. |

---

## 14. Structured Learning Path

### Week 1 — Foundations (Associate-level)
| Day | Topic | Time | Goal |
|---|---|---|---|
| 1 | OneAgent, ActiveGate, Cluster architecture | 1 hr | Understand how data physically moves |
| 1 | Entities & Smartscape topology | 1 hr | Navigate Hosts → Processes → Services in the UI |
| 2 | PurePath / distributed tracing | 2 hrs | Open a real PurePath, find the slowest node |
| 3 | Custom Request Attributes | 1 hr | Attach a business ID to a trace |
| 3 | Log Management basics | 1 hr | Search logs with DQL instead of grep |
| 4 | Metric Events & Alerting | 1 hr | Create one Slack alert on error rate |
| 5 | Dashboards & DQL basics | 2 hrs | Build one dashboard tile from a DQL query |

### Week 2 — Applied depth (Professional-track)
| Day | Topic | Time | Goal |
|---|---|---|---|
| 6 | Business Events | 2 hrs | Emit and query one bizevent end-to-end |
| 7 | Grail + OpenPipeline concepts | 2 hrs | Understand pipeline/processing model |
| 8 | Davis AI root-cause reading | 1.5 hrs | Read a real Problem, trace root cause manually to confirm Davis |
| 9 | Spring Boot: Micrometer + OneAgent SDK | 2 hrs | Wire Actuator metrics into Dynatrace |
| 10 | OpenTelemetry basics | 2 hrs | Send one OTel trace into Dynatrace |
| 11 | AppSec overview | 1 hr | Find a flagged vulnerable library |
| 12 | Notebooks + advanced DQL (joins, parse) | 2 hrs | Investigate a mock incident using a Notebook |

---

## 15. Certifications

*(Verified as of August 2026 — Dynatrace certification formats change; always re-check inside Dynatrace University before booking.)*

1. **Dynatrace Associate Certification**
   - Entry-level, validates foundational platform knowledge across ~12 topic areas (Infrastructure Observability, Foundational Platform Capabilities, Log Investigation, PurePath/Service Flow, RUM, dashboards/alerts, problem analysis, etc.).
   - Format and pass score can vary by cohort — confirm current details in Dynatrace University before scheduling.

2. **Dynatrace Professional Certification**
   - As of 2026, this is a **two-part credential**: a ~100-question written exam (about 2 hours), followed by a **hands-on practical exam** that must be completed within 3 months of passing the written portion.
   - Covers deeper topics: Davis tuning, AppSec, OpenPipeline, DQL, and Site Reliability Guardians (SRG).
   - Certification is valid for 2 years.
   - Recommended prerequisite: comfort with everything in the Associate exam, even if not strictly enforced.

3. Free training is available through **Dynatrace University** (self-paced learning paths per module).

---

## 16. Hands-On Exercises

Do these in order using a free Dynatrace SaaS trial:

1. **Install OneAgent** on one host/VM/container and watch it auto-discover processes within a few minutes.
2. **Break something on purpose** (e.g., add an artificial delay in a DB call) and find the resulting PurePath — identify the exact slow method/SQL.
3. **Emit one Business Event** from your app (a simple `curl` to the Business Events API works too) and build a one-tile dashboard counting it.
4. **Write a DQL query** that filters your logs for `ERROR` and groups by host — reproduce the example in Section 3.3 with your own data.
5. **Create one Metric Event alert** (e.g., error rate > 5% for 5 min) that posts to a Slack channel or webhook.
6. **Open a Davis Problem** (real or synthetically triggered) and manually verify its stated root cause against the raw PurePath/topology data — this builds the intuition for how Davis reasons.
7. **Add a Custom Request Attribute** and use it to filter PurePaths by a business ID (e.g., `tenant_id`).

---

### Final Summary
Dynatrace is not a dashboard tool — it's an AI-driven observability platform. OneAgent auto-collects data; OpenPipeline ingests, enriches, and routes it; Grail stores it all in one unified, schemaless place; DQL lets you query any of it; Davis AI causally root-causes problems using the live Smartscape topology; and Business Events tie all of that technical detail back to business impact. Master these seven pieces — OneAgent, Smartscape, PurePath, Grail/OpenPipeline, DQL, Davis, Business Events — and you understand the whole platform.
