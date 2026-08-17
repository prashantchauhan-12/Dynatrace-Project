// Send a duplicate file_id directly to S3 to trigger DUPLICATE_FILE error
const http = require('http');

const payload = JSON.stringify({
  fileId: "err-s3-001",
  fileName: "duplicate_test.txt",
  fileType: "FX",
  title: "Duplicate Test",
  logoUrl: "https://example.com/logo.png",
  content: "This will cause a duplicate error in S3",
  footer: "Footer",
  contentHash: "abc123"
});

const options = {
  hostname: 'localhost',
  port: 8083,
  path: '/api/persist',
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'X-Correlation-Id': 'err-s3-direct',
    'X-File-Type': 'FX',
    'Content-Length': Buffer.byteLength(payload)
  }
};

const req = http.request(options, (res) => {
  let data = '';
  res.on('data', (chunk) => data += chunk);
  res.on('end', () => {
    console.log('Status:', res.statusCode);
    console.log('Response:', data);
  });
});

req.on('error', (e) => console.error('Error:', e.message));
req.write(payload);
req.end();
