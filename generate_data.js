const fs = require('fs');

async function uploadFile(fileId, fileType, fileName, content) {
    fs.writeFileSync(fileName, content);
    
    const formData = new FormData();
    const fileBlob = new Blob([fs.readFileSync(fileName)]);
    formData.append('file', fileBlob, fileName);
    formData.append('file_type', fileType);

    try {
        const response = await fetch('http://localhost:8081/api/files/upload', {
            method: 'POST',
            headers: {
                'X-Correlation-Id': fileId
            },
            body: formData
        });
        const text = await response.text();
        console.log(`[${fileId}] Uploaded ${fileName}. Status: ${response.status}, Res: ${text.substring(0, 80)}`);
    } catch (err) {
        console.error(`[${fileId}] Failed: ${err.message}`);
    }
    
    fs.unlinkSync(fileName);
    
    // Sleep a bit so timestamps are definitely different
    await new Promise(r => setTimeout(r, 1500));
}

const contentV1 = "[TITLE]\nFinancial Report\n[LOGO]\nhttp://example.com/logo.png\n[CONTENT]\nVersion 1 Data\n[FOOTER]\nEnd\n";
const contentV2 = "[TITLE]\nFinancial Report\n[LOGO]\nhttp://example.com/logo.png\n[CONTENT]\nVersion 2 Data\n[FOOTER]\nEnd\n";

async function run() {
    console.log("1. Uploading fx-01 (Version 1)...");
    await uploadFile("fx-01", "FX", "fx01_v1.txt", contentV1);
    
    console.log("\n2. Uploading fx-01 (Version 2 - DIFFERENT CONTENT)... -> Should auto-version!");
    await uploadFile("fx-01", "FX", "fx01_v2.txt", contentV2);
    
    console.log("\n3. Uploading fx-01 (Version 2 - EXACT SAME CONTENT)... -> Should be skipped (Idempotent)!");
    await uploadFile("fx-01", "FX", "fx01_v2_duplicate.txt", contentV2);

    console.log("\nDone!");
}

run();
