# Minecraft AI · View Lab

A Minecraft-inspired, mobile-friendly **session simulation dashboard**. It lets you test counters, a live event stream, the session-pulse chart, controls, and CSV export without creating artificial traffic to a real website.

## What it includes

- Configurable local event pace from 1 to 60 simulated events per minute.
- Start and stop controls, current-run count, lifetime simulated counter, and uptime.
- Minecraft-inspired responsive dashboard with a live activity log and canvas chart.
- Browser-local persistence using `localStorage`.
- CSV export with each record clearly classified as `SIMULATED_NOT_REAL_TRAFFIC`.
- A manual **Open** button for inspecting a target yourself.
- Small Express server and a health endpoint at `/api/health`.

## Important behavior

The URL field is a reference label only. The simulation does **not** request that URL, load it in a hidden frame, create real page views, or generate genuine analytics/ad impressions. All generated events are local to the browser and are not website visitors. Use real, authorized monitoring or load-testing tools for legitimate performance tests, and keep test traffic out of advertising metrics.

## Run locally

Requires Node.js 20 or later.

```bash
npm install
npm start
```

Then open `http://localhost:3000`.

## Deploy

Use `npm install` as the build/install command and `npm start` as the start command on a Node.js host. The app uses the `PORT` environment variable supplied by the host. No API key or database is needed.

## Project files

- `index.html` — dashboard structure and accessible controls.
- `style.css` — responsive block-inspired UI.
- `app.js` — local event simulation, counters, chart, local persistence, and CSV export.
- `server.js` — minimal Express host and health endpoint.
