# 🚀 Enterprise Observability Pipeline: Live Demo Execution Guide

Welcome to the definitive guide for executing the live portion of your Proof of Concept (POC). While the `README.md` serves as your architectural reference, this document is your **step-by-step script** for the live demonstration. 

It includes the latest testing scripts we generated, the intentional error files, and how to handle advanced observability concepts like Eventual Consistency on the fly.

---

## 1. Preparation (15 Minutes Before the Demo)

1. **Start the Backend:** Ensure S1, S2, and S3 Spring Boot services are running (`localhost:8081`, `8082`, `8083`).
2. **Start the Frontend:** Run `npm run dev` in the `monitoring-dashboard` directory.
3. **Clean Environment:** We have already wiped the `sample-files` directory to keep the demo clean. 
4. **Pre-Load Data:** Keep the Next.js dashboard open. The DAG should currently show the history of our recent uploads.

---

## 2. The Live Script (Step-by-Step)

### Phase 1: The "Happy Path" (Baseline)
**Goal:** Show the pipeline working perfectly and Dynatrace drawing the DAG.
* **Action:** Open Postman or your terminal. Send a valid FX file to the ingestion service.
  ```bash
  curl.exe -X POST "http://localhost:8081/api/files/upload" -F "file=@sample-files/fx_1.txt" -F "file_type=FX" -H "X-Correlation-Id: demo-happy-01"
  ```
* **Talking Point:** *"I'm uploading a standard Foreign Exchange file. The Java backend processes it and emits Business Events to Dynatrace at exactly 3 points: Ingestion, Transformation, and Persistence. Let's look at the dashboard."*
* **Visual:** Refresh the Next.js UI. Point to the fully green pipeline trace for `demo-happy-01`.

---

### Phase 2: Demonstrating Business Failures (The Intentional Errors)
**Goal:** Prove that the pipeline catches errors early and visualizes them instantly. We have pre-generated three specific error files in your `sample-files` directory.

**Action 1: S1 Validation Error**
* **Execute:** 
  ```bash
  curl.exe -X POST "http://localhost:8081/api/files/upload" -F "file=@sample-files/error_format.xml" -F "file_type=GENERIC" -H "X-Correlation-Id: err-s1-demo"
  ```
* **Talking Point:** *"I just uploaded an XML file. Our business rules only allow TXT/CSV/PDF/DOCX. Look at the dashboard: S1 instantly fails (Red), and S2/S3 are Grey (NOT_STARTED). We saved massive CPU cycles by failing fast at the edge."*

**Action 2: S2 Parsing Error**
* **Execute:**
  ```bash
  curl.exe -X POST "http://localhost:8081/api/files/upload" -F "file=@sample-files/error_missing_footer.txt" -F "file_type=FX" -H "X-Correlation-Id: err-s2-demo"
  ```
* **Talking Point:** *"This file is a valid `.txt`, so it passes S1. But I intentionally deleted the `[FOOTER]` section. Watch the dashboard: S1 is Green, but S2 is Red."*
* **Visual:** Click on the Red S2 node. Show the popover that says `missing_section="Footer"`. 

---

### Phase 3: The Idempotency & Auto-Versioning Masterclass
**Goal:** Show how the system handles duplicate files and content revisions without polluting the data lake.

**Action 1: The Exact Duplicate**
* **Execute:** Run the exact same "Happy Path" curl command from Phase 1 again, using the exact same file and `demo-happy-01` ID.
* **Talking Point:** *"I just uploaded the exact same file. The S1 Idempotency Engine calculated the SHA-256 hash, saw it was identical, and dropped it immediately. Notice the dashboard didn't duplicate the trace."*

**Action 2: The Content Revision**
* **Execute:** We have generated `fx_3_v1.txt`, `fx_3_v2.txt`, and `fx_3_v3.txt` (files with slightly different content but the same structure).
  ```bash
  # Send all three with the EXACT same Correlation ID
  curl.exe -X POST "http://localhost:8081/api/files/upload" -F "file=@sample-files/fx_3_v1.txt" -F "file_type=FX" -H "X-Correlation-Id: fx-103-demo"
  curl.exe -X POST "http://localhost:8081/api/files/upload" -F "file=@sample-files/fx_3_v2.txt" -F "file_type=FX" -H "X-Correlation-Id: fx-103-demo"
  ```
* **Talking Point:** *"I just uploaded a file with the same ID but modified content. The engine detected the hash mismatch, bypassed idempotency, and triggered Auto-Versioning."*
* **Visual:** Go to the UI and open the History Dropdown for `fx-103-demo`. Show how it dynamically grouped the `v178...` timestamped versions chronologically!

---

## 3. How to Handle the "Grey Node" Phenomenon (Eventual Consistency)

During rapid live testing, you might hit refresh on the dashboard and see a weird state: **S1 is Green, S3 is Green, but S2 is Grey (N/A).**

> [!IMPORTANT]
> **Do not panic if this happens.** This is not a bug; it is a feature of Enterprise Data Lakes, and explaining it makes you look like a Senior Architect.

**How to Explain It:**
1. *"What you are seeing right now is a perfect example of **Eventual Consistency** in a decoupled observability architecture."*
2. *"Our Java services do not wait for Dynatrace to save the event. They fire the JSON payload asynchronously and move on."*
3. *"Because I refreshed the page a split-second after the pipeline finished, S1 and S3 had instantly indexed in the Dynatrace Grail database, but S2 was momentarily sitting in an ingestion queue."*
4. *"If I refresh the page right now (wait 2 seconds and refresh), you will see S2 is now perfectly green. This proves that our observability layer never blocks or slows down our core business logic!"*

---

## 4. Closing the Demo

Wrap up by pointing to the **Active Alerts** tab in your dashboard (or discussing Dynatrace Workflows).
> *"Because all these failures (like the missing footer) are structured Business Events, we configure Dynatrace to automatically create Jira tickets or Slack messages when they occur, completely eliminating the need for legacy database polling."*

**You are now fully prepared to deliver a flawless, deeply technical demonstration.** 🚀
