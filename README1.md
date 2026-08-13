# Pipeline Matrix: Custom Next.js Dashboard

This is a custom, high-fidelity Next.js application built to monitor the Dynatrace File Pipeline. It provides an advanced **Expand/Collapse Tree View** to group file events by their `file_type` (e.g., FX, EDM, ACCOUNTS), bypassing the visual limitations of the native Dynatrace dashboarding system.

## 🚀 Features
- **Real-time Monitoring Matrix**: A sleek, glassmorphic UI built with Tailwind CSS.
- **Hierarchical Grouping**: Collapses hundreds of files into manageable pipeline categories.
- **Dynatrace API Integration**: Uses a Next.js server-side API route to query Dynatrace's powerful DQL (Dynatrace Query Language) backend.
- **Graceful Degradation**: If Dynatrace WAF blocks the request, the UI seamlessly falls back to a realistic mock state for demonstration purposes.

---

## 🛠️ Getting Started

### Prerequisites
- Node.js 18+
- npm or yarn

### Installation
1. Navigate into the dashboard directory:
   ```bash
   cd monitoring-dashboard
   ```
2. Install dependencies:
   ```bash
   npm install
   ```
3. Set up your environment variables:
   Create a `.env.local` file in the root of this folder:
   ```env
   DYNATRACE_URL=https://<your-tenant>.live.dynatrace.com
   DYNATRACE_API_TOKEN=your_oauth_token_here
   ```
4. Start the development server:
   ```bash
   npm run dev
   ```
5. Open [http://localhost:3000](http://localhost:3000) in your browser.

---

## 🔒 Security & Dynatrace OAuth2 Requirements

This application queries the **Dynatrace Grail API** (`/platform/storage/query/v1/query:execute`).

### The Security Model
Dynatrace enforces a highly strict security model for its Grail architecture. While pushing data *into* Dynatrace can be done using a standard Personal API Token (`dt0c01...`), pulling data *out* via the Grail query API requires a fully authenticated **OAuth2 Client Application**.

### Why you might see a `403 Forbidden` Error
If you attempt to query the API using a standard Personal API Token (or from an unauthorized local IP address), the Dynatrace Web Application Firewall (WAF) will actively block the request with a `Request forbidden by administrative rules` error.

### Production Deployment Steps
To deploy this dashboard in a live Enterprise environment and replace the mock data with live DQL results:
1. A Dynatrace Administrator must register an **OAuth2 Client Application** in the Dynatrace account settings.
2. The Client must be granted the `storage:events:read` and `storage:bizevents:read` scopes.
3. The generated **Client ID** and **Client Secret** must be provided to this application to perform a secure Token Exchange flow.
4. Once the OAuth2 token is passed in the header, the firewall will allow the DQL query to execute seamlessly.

---

## 🎨 UI Architecture
- **Framework**: [Next.js 15 (App Router)](https://nextjs.org/)
- **Styling**: [Tailwind CSS](https://tailwindcss.com/)
- **Icons**: [Lucide React](https://lucide.dev/)
- **API Engine**: Next.js Serverless Route (`app/api/pipeline/route.ts`)
