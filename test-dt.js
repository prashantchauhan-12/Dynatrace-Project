const url = "https://jpf24109.live.dynatrace.com";
const token = process.env.DYNATRACE_API_TOKEN; // Wait, I'll pass it in the command or read from .env.local
const fs = require('fs');
const env = fs.readFileSync('c:/Users/prash/Desktop/dynatrace-task/monitoring-dashboard/.env.local', 'utf-8');
const match = env.match(/DYNATRACE_API_TOKEN=(.*)/);
const realToken = match ? match[1].trim() : '';

async function run() {
  console.log("Testing Metrics API...");
  const res1 = await fetch(`${url}/api/v2/metrics/query?metricSelector=builtin:host.cpu.usage:limit(1)`, {
    headers: { 'Authorization': `Bearer ${realToken}` }
  });
  console.log("Metrics:", res1.status, await res1.text());

  console.log("Testing DQL API v3...");
  const bodyStr = JSON.stringify({ query: "fetch bizevents | limit 1" });
  const res2 = await fetch(`${url}/api/v3/dql/query`, {
    method: "POST",
    headers: { 
      'Authorization': `Api-Token ${realToken}`, 
      'Content-Type': 'application/json',
      'Content-Length': Buffer.byteLength(bodyStr).toString(),
      'Accept': 'application/json; charset=utf-8',
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36'
    },
    body: bodyStr
  });
  console.log("DQL:", res2.status, await res2.text());
}
run();
