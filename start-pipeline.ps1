# =====================================================
#  START ALL PIPELINE SERVICES
#  Run this from: c:\Users\prash\Desktop\dynatrace-task
# =====================================================

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Starting Dynatrace File Pipeline...  " -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$root = "c:\Users\prash\Desktop\dynatrace-task"

# Start S1 - Ingestion (Port 8081)
Write-Host "[1/3] Starting S1 - File Ingestion Service (port 8081)..." -ForegroundColor Green
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$root\file-ingestion-service'; Write-Host 'S1: File Ingestion Service' -ForegroundColor Green; mvn spring-boot:run"

Start-Sleep -Seconds 3

# Start S2 - Transformation (Port 8082)
Write-Host "[2/3] Starting S2 - File Transformation Service (port 8082)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$root\file-transformation-service'; Write-Host 'S2: File Transformation Service' -ForegroundColor Yellow; mvn spring-boot:run"

Start-Sleep -Seconds 3

# Start S3 - Persistence (Port 8083)
Write-Host "[3/3] Starting S3 - File Persistence Service (port 8083)..." -ForegroundColor Blue
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$root\file-persistence-service'; Write-Host 'S3: File Persistence Service' -ForegroundColor Blue; mvn spring-boot:run"

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  All 3 services launched!             " -ForegroundColor Cyan
Write-Host "  S1: http://localhost:8081             " -ForegroundColor Green
Write-Host "  S2: http://localhost:8082             " -ForegroundColor Yellow
Write-Host "  S3: http://localhost:8083             " -ForegroundColor Blue
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Tip: Wait ~30 seconds for all services to fully start." -ForegroundColor Gray
Write-Host "Then test: curl http://localhost:8081/api/files/health" -ForegroundColor Gray
