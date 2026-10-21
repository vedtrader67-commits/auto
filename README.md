# AUTO Browser Lab

A mobile-friendly browser-testing dashboard for launching isolated Chromium sessions against websites you own or have explicit permission to test.

The dashboard is static and hosted on GitHub Pages. Real browser sessions run on a separate Node.js + Playwright backend. The backend is deliberately locked to exact hostnames configured in the ALLOWED_TARGET_HOSTS environment variable.

## What it does

- Launches multiple isolated headless Chromium browser contexts from one dashboard.
- Shows per-session load status, HTTP status, elapsed load time, errors, and a screenshot preview.
- Supports a run duration of 1–5 minutes, with a hard backend duration cap.
- Provides a Stop All control to close all sessions in a run.
- Sends X-Test-Traffic: true and X-Authorized-Test-Run request headers so your server-side analytics can filter test traffic.
- Requires a dashboard token, an authorized-target confirmation, HTTPS, and an exact host allowlist.
- Blocks top-level navigation redirects to hostnames that are not allowlisted.
- Limits runs to one active run at a time and keeps a short in-memory run history.

This is a controlled testing tool. It does not make genuine audience or advertising impressions, and it must not be used to inflate metrics on sites you do not control.

## Deploy the backend on Render

1. Open the Render dashboard and choose **New → Blueprint**. Connect the GitHub repository vedtrader67-commits/auto, select branch main, and deploy the render.yaml Blueprint. The repository also has a **Deploy Runner** link at the bottom of the homepage.
2. Open the new service's Environment settings. Set ALLOWED_TARGET_HOSTS to the exact hostname(s) you own or are authorized to test, separated by commas. Example: my-site.example.com. Do not include https:// or a path.
3. Keep the generated DASHBOARD_TOKEN secret. Copy its value from the host settings when prompted; never commit it into a source file or share it publicly.
4. Wait until the backend service is healthy. Copy its https://...onrender.com service URL.
5. Open the GitHub Pages homepage at https://vedtrader67-commits.github.io/auto/. Paste the backend URL and dashboard token, then press **Connect**.
6. Enter a URL whose hostname exactly matches the allowlist. Select a session count, keep-open duration, and tick the authorization checkbox before launching.

The Blueprint starts with a 3-session cap because small/free containers have limited memory. The backend supports a configurable cap up to 100, but setting a high number does not create more memory or CPU. Increase MAX_SESSIONS only on a host sized for that workload. A browser-heavy workload may exceed free hosting resources, and free services may sleep when idle.

## Backend environment settings

| Variable | Purpose |
| --- | --- |
| DASHBOARD_TOKEN | Secret token required for launching, reading, and stopping runs. Render generates a value in the Blueprint. |
| ALLOWED_TARGET_HOSTS | Required comma-separated exact hostnames authorized for tests. Empty means no tests can start. |
| ALLOWED_ORIGINS | Browser origins allowed to call the backend. The Blueprint allows https://vedtrader67-commits.github.io. |
| MAX_SESSIONS | Hard cap for simultaneous isolated browser contexts, from 1 to 100. Blueprint default: 3. |
| MAX_DURATION_SECONDS | Hard cap on each run's duration, from 60 to 600 seconds. Blueprint default: 300. |
| ALLOW_HTTP_TARGETS | Set to true only for a controlled private development environment. HTTPS is required otherwise. |

Changing the backend environment may trigger a restart/redeploy. If a new target domain is needed, add it to the host allowlist first.

## Local development

Requires Node.js 20 or later. Install the dependencies and Playwright's browser before starting:

    npm install
    npx playwright install --with-deps chromium
    npm start

Then open http://localhost:3000. Configure the environment variables before using any run endpoints. The backend fails closed when no allowlisted hostname or dashboard token is configured.

## Test traffic and analytics

Each browser context sends these headers on page requests:

- X-Test-Traffic: true
- X-Authorized-Test-Run: run-id

Use them to exclude test requests from production analytics, counters, advertising, and reporting. Some third-party analytics services cannot filter on custom headers directly; filter at your own server or use a separate staging property.

## Project files

- index.html — dashboard UI and browser-session cards.
- app.js — backend connection, run controls, status polling, and previews.
- server.js — allowlisted browser runner, API, token checks, and stop handling.
- Dockerfile — Playwright-enabled container image.
- render.yaml — Render Blueprint.
- live-counter/ — separate live-counter dashboard.
- .github/workflows/deploy.yml — GitHub Pages deployment.
