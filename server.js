'use strict';

const crypto = require('node:crypto');
const dns = require('node:dns').promises;
const net = require('node:net');
const path = require('node:path');
const express = require('express');
const { chromium } = require('playwright');

const app = express();
const PORT = Number(process.env.PORT || 3000);
const ROOT = __dirname;
const DASHBOARD_TOKEN = String(process.env.DASHBOARD_TOKEN || '');
const ALLOWED_ORIGINS = new Set(
  String(process.env.ALLOWED_ORIGINS || 'https://vedtrader67-commits.github.io,http://localhost:3000,http://127.0.0.1:3000')
    .split(',').map((value) => value.trim()).filter(Boolean)
);
const ALLOWED_TARGET_HOSTS = new Set(
  String(process.env.ALLOWED_TARGET_HOSTS || '')
    .split(',').map((value) => value.trim().toLowerCase()).filter(Boolean)
);
const MAX_SESSIONS = boundedInt(process.env.MAX_SESSIONS, 1, 100, 5);
const MAX_DURATION_SECONDS = boundedInt(process.env.MAX_DURATION_SECONDS, 60, 600, 300);
const MIN_SECONDS_BETWEEN_RUNS = 10;
const runs = new Map();
let activeRunId = null;
let lastRunStartedAt = 0;

function boundedInt(value, min, max, fallback) {
  const number = Number(value);
  if (!Number.isInteger(number)) return fallback;
  return Math.min(max, Math.max(min, number));
}

function isPrivateAddress(address) {
  const type = net.isIP(address);
  if (type === 4) {
    const parts = address.split('.').map(Number);
    const a = parts[0], b = parts[1], c = parts[2];
    return a === 0 || a === 10 || a === 127 || a >= 224 ||
      (a === 100 && b >= 64 && b <= 127) ||
      (a === 169 && b === 254) ||
      (a === 172 && b >= 16 && b <= 31) ||
      (a === 192 && b === 168) ||
      (a === 192 && b === 0 && c === 0) ||
      (a === 198 && (b === 18 || b === 19));
  }
  if (type === 6) {
    const value = address.toLowerCase();
    if (value.startsWith('::ffff:')) {
      const mapped = value.slice(7);
      if (net.isIP(mapped) === 4) return isPrivateAddress(mapped);
    }
    return value === '::' || value === '::1' ||
      value.startsWith('fc') || value.startsWith('fd') ||
      /^fe[89ab]/.test(value) || value.startsWith('ff');
  }
  return true;
}

async function validateTarget(rawTarget) {
  if (typeof rawTarget !== 'string' || rawTarget.length > 2048) {
    throw new Error('Enter a valid website URL.');
  }

  let url;
  try {
    url = new URL(rawTarget.trim());
  } catch (_) {
    throw new Error('Enter a complete https:// website URL.');
  }

  if (!['https:', 'http:'].includes(url.protocol) || !url.hostname ||
      url.username || url.password || url.port && !/^\d{1,5}$/.test(url.port)) {
    throw new Error('Only normal HTTP or HTTPS website URLs are accepted.');
  }
  if (url.protocol !== 'https:' && process.env.ALLOW_HTTP_TARGETS !== 'true') {
    throw new Error('HTTPS is required for test targets.');
  }
  const host = url.hostname.toLowerCase().replace(/\.$/, '');
  if (net.isIP(host)) {
    throw new Error('Use an authorized domain name, not an IP address.');
  }
  if (!ALLOWED_TARGET_HOSTS.has(host)) {
    throw new Error('This domain is not on the backend allowlist. Add its exact hostname to ALLOWED_TARGET_HOSTS first.');
  }

  let addresses;
  try {
    addresses = await dns.lookup(host, { all: true, verbatim: true });
  } catch (_) {
    throw new Error('The target domain could not be resolved.');
  }
  if (!addresses.length || addresses.some((entry) => isPrivateAddress(entry.address))) {
    throw new Error('The target resolves to a private or reserved network address and was blocked.');
  }

  return { url, host };
}

function safeTokenEquals(supplied) {
  if (!DASHBOARD_TOKEN || typeof supplied !== 'string') return false;
  const expected = Buffer.from(DASHBOARD_TOKEN);
  const received = Buffer.from(supplied);
  return expected.length === received.length && crypto.timingSafeEqual(expected, received);
}

function requireDashboardToken(req, res, next) {
  if (!DASHBOARD_TOKEN) {
    return res.status(503).json({
      ok: false,
      error: 'Backend setup is incomplete: configure DASHBOARD_TOKEN in the hosting environment.'
    });
  }
  const authorization = String(req.get('Authorization') || '');
  const bearer = authorization.startsWith('Bearer ') ? authorization.slice(7) : '';
  const token = bearer || String(req.get('X-Dashboard-Token') || '');
  if (!safeTokenEquals(token)) {
    return res.status(401).json({ ok: false, error: 'Dashboard token is missing or incorrect.' });
  }
  return next();
}

function safeErrorMessage(error) {
  const message = String(error && error.message || '');
  if (/timeout/i.test(message)) return 'Navigation timed out.';
  if (/blockedbyclient|blocked by|unapproved host/i.test(message)) return 'Navigation to an unapproved host was blocked.';
  if (/net::ERR_|failed to launch|browser.*closed/i.test(message)) return 'Browser navigation or launch failed.';
  return 'Navigation failed. Check the target and backend logs.';
}

function publicRun(run, includePreviews) {
  const elapsedSeconds = Math.max(0, Math.floor(((run.endedAt || Date.now()) - run.startedAt) / 1000));
  return {
    id: run.id,
    status: run.status,
    target: run.targetDisplay,
    sessionCount: run.sessions.length,
    durationSeconds: run.durationSeconds,
    elapsedSeconds,
    remainingSeconds: run.status === 'completed' || run.status === 'stopped' || run.status === 'failed'
      ? 0 : Math.max(0, Math.ceil((run.deadline - Date.now()) / 1000)),
    startedAt: new Date(run.startedAt).toISOString(),
    endedAt: run.endedAt ? new Date(run.endedAt).toISOString() : null,
    message: run.message || '',
    sessions: run.sessions.map((session) => {
      const result = {
        id: session.id,
        status: session.status,
        httpStatus: session.httpStatus,
        elapsedMs: session.elapsedMs,
        error: session.error || ''
      };
      if (includePreviews && session.preview) {
        result.preview = 'data:image/jpeg;base64,' + session.preview.toString('base64');
      }
      return result;
    })
  };
}

function pruneHistory() {
  const finished = Array.from(runs.values())
    .filter((run) => run.endedAt)
    .sort((a, b) => b.endedAt - a.endedAt);
  for (const run of finished.slice(20)) runs.delete(run.id);
}

async function executeRun(run) {
  try {
    run.status = 'starting';
    run.browser = await chromium.launch({
      headless: true,
      args: ['--no-sandbox', '--disable-dev-shm-usage', '--disable-background-networking', '--disable-default-apps']
    });
    if (run.stopRequested) return;
    run.status = 'running';

    await Promise.all(run.sessions.map(async (session) => {
      if (run.stopRequested) {
        session.status = 'stopped';
        return;
      }
      session.status = 'launching';
      const began = Date.now();
      try {
        const context = await run.browser.newContext({
          viewport: { width: 480, height: 320 },
          deviceScaleFactor: 1,
          ignoreHTTPSErrors: false,
          extraHTTPHeaders: {
            'X-Test-Traffic': 'true',
            'X-Authorized-Test-Run': run.id
          }
        });
        run.contexts.push(context);
        const page = await context.newPage();
        session.page = page;

        await page.route('**/*', async (route) => {
          const request = route.request();
          if (!request.isNavigationRequest()) return route.continue();
          try {
            const nextUrl = new URL(request.url());
            if (!['http:', 'https:'].includes(nextUrl.protocol)) return route.abort();
            if (!ALLOWED_TARGET_HOSTS.has(nextUrl.hostname.toLowerCase().replace(/\.$/, ''))) {
              return route.abort('blockedbyclient');
            }
            return route.continue();
          } catch (_) {
            return route.abort();
          }
        });

        const response = await page.goto(run.targetUrl, {
          waitUntil: 'domcontentloaded',
          timeout: 20000
        });
        session.httpStatus = response ? response.status() : null;
        session.elapsedMs = Date.now() - began;
        session.status = 'loaded';
        try {
          session.preview = await page.screenshot({
            type: 'jpeg',
            quality: 45,
            scale: 'css',
            animations: 'disabled',
            timeout: 5000
          });
        } catch (_) {
          session.preview = null;
        }
      } catch (error) {
        session.elapsedMs = Date.now() - began;
        session.status = run.stopRequested ? 'stopped' : 'failed';
        session.error = run.stopRequested ? '' : safeErrorMessage(error);
      }
    }));

    if (run.stopRequested) return;
    const remaining = Math.max(0, run.deadline - Date.now());
    if (remaining > 0) {
      await new Promise((resolve) => {
        run.finishResolve = resolve;
        run.finishTimer = setTimeout(resolve, remaining);
      });
    }
    if (!run.stopRequested) run.status = 'completed';
  } catch (_) {
    run.status = run.stopRequested ? 'stopped' : 'failed';
    run.message = run.stopRequested ? 'Run stopped by the dashboard.' : 'Browser engine failed to start. Check backend logs and hosting memory limits.';
  } finally {
    if (run.finishTimer) clearTimeout(run.finishTimer);
    if (run.browser) {
      try { await run.browser.close(); } catch (_) {}
    }
    for (const session of run.sessions) {
      if (session.status === 'queued' || session.status === 'launching') {
        session.status = run.stopRequested ? 'stopped' : 'failed';
        if (!session.error && session.status === 'failed') session.error = 'Browser session did not finish loading.';
      }
    }
    if (run.stopRequested) run.status = 'stopped';
    if (run.status === 'starting' || run.status === 'running' || run.status === 'stopping') {
      run.status = 'completed';
    }
    run.endedAt = Date.now();
    if (activeRunId === run.id) activeRunId = null;
    pruneHistory();
  }
}

app.disable('x-powered-by');
app.disable('etag');
app.use((req, res, next) => {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('Referrer-Policy', 'strict-origin-when-cross-origin');
  res.setHeader('X-Frame-Options', 'SAMEORIGIN');
  res.setHeader('Permissions-Policy', 'camera=(), microphone=(), geolocation=()');
  res.setHeader('Cache-Control', 'no-store');
  res.setHeader('Vary', 'Origin');

  const origin = req.get('Origin');
  if (origin) {
    if (!ALLOWED_ORIGINS.has(origin)) return res.status(403).json({ ok: false, error: 'This browser origin is not allowed.' });
    res.setHeader('Access-Control-Allow-Origin', origin);
    res.setHeader('Access-Control-Allow-Methods', 'GET,POST,OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type,Authorization,X-Dashboard-Token');
    res.setHeader('Access-Control-Max-Age', '600');
  }
  if (req.method === 'OPTIONS') return res.sendStatus(204);
  return next();
});
app.use(express.json({ limit: '16kb' }));

app.get('/api/health', (_req, res) => {
  res.json({
    ok: true,
    name: 'Authorized Browser Test Lab',
    mode: 'allowlisted-real-chromium-sessions',
    ready: Boolean(DASHBOARD_TOKEN && ALLOWED_TARGET_HOSTS.size),
    activeRun: Boolean(activeRunId),
    allowedHostCount: ALLOWED_TARGET_HOSTS.size,
    maxSessions: MAX_SESSIONS,
    maxDurationSeconds: MAX_DURATION_SECONDS
  });
});

app.get('/api/config', (_req, res) => {
  res.json({
    ok: true,
    maxSessions: MAX_SESSIONS,
    maxDurationSeconds: MAX_DURATION_SECONDS,
    allowedHosts: Array.from(ALLOWED_TARGET_HOSTS),
    authConfigured: Boolean(DASHBOARD_TOKEN),
    requireAuthorizedConfirmation: true
  });
});

app.post('/api/runs/start', requireDashboardToken, async (req, res) => {
  if (activeRunId) {
    return res.status(409).json({ ok: false, error: 'A browser run is already active. Stop it or wait for it to finish.' });
  }

  const sessions = Number(req.body && req.body.sessions);
  const durationSeconds = Number(req.body && req.body.durationSeconds);
  if (req.body && req.body.confirmAuthorized !== true) {
    return res.status(400).json({ ok: false, error: 'Confirm that you own or have explicit permission to test this domain.' });
  }
  if (!Number.isInteger(sessions) || sessions < 1 || sessions > MAX_SESSIONS) {
    return res.status(400).json({ ok: false, error: 'Session count must be between 1 and ' + MAX_SESSIONS + ' for this backend.' });
  }
  if (!Number.isInteger(durationSeconds) || durationSeconds < 60 || durationSeconds > MAX_DURATION_SECONDS) {
    return res.status(400).json({ ok: false, error: 'Duration must be between 60 and ' + MAX_DURATION_SECONDS + ' seconds.' });
  }
  if (Date.now() - lastRunStartedAt < MIN_SECONDS_BETWEEN_RUNS * 1000) {
    return res.status(429).json({ ok: false, error: 'Please wait a few seconds before starting another run.' });
  }
  if (!ALLOWED_TARGET_HOSTS.size) {
    return res.status(503).json({ ok: false, error: 'No authorized target domains are configured on the backend yet.' });
  }

  let target;
  try {
    target = await validateTarget(req.body && req.body.targetUrl);
  } catch (error) {
    return res.status(400).json({ ok: false, error: error.message });
  }

  const id = crypto.randomUUID();
  const startedAt = Date.now();
  const run = {
    id,
    status: 'queued',
    targetUrl: target.url.href,
    targetDisplay: target.url.origin + target.url.pathname,
    host: target.host,
    durationSeconds,
    startedAt,
    deadline: startedAt + durationSeconds * 1000,
    endedAt: null,
    message: '',
    stopRequested: false,
    browser: null,
    contexts: [],
    finishTimer: null,
    finishResolve: null,
    sessions: Array.from({ length: sessions }, (_, index) => ({
      id: index + 1,
      status: 'queued',
      httpStatus: null,
      elapsedMs: null,
      error: '',
      preview: null,
      page: null
    }))
  };

  runs.set(id, run);
  activeRunId = id;
  lastRunStartedAt = Date.now();
  executeRun(run).catch(() => {});
  return res.status(202).json({ ok: true, run: publicRun(run, false) });
});

app.get('/api/runs/active', requireDashboardToken, (_req, res) => {
  if (!activeRunId || !runs.has(activeRunId)) return res.json({ ok: true, run: null });
  return res.json({ ok: true, run: publicRun(runs.get(activeRunId), false) });
});

app.get('/api/runs/:id', requireDashboardToken, (req, res) => {
  const run = runs.get(req.params.id);
  if (!run) return res.status(404).json({ ok: false, error: 'Run not found. The backend may have restarted.' });
  const includePreviews = req.query.previews === '1';
  return res.json({ ok: true, run: publicRun(run, includePreviews) });
});

app.post('/api/runs/:id/stop', requireDashboardToken, async (req, res) => {
  const run = runs.get(req.params.id);
  if (!run) return res.status(404).json({ ok: false, error: 'Run not found.' });
  if (run.endedAt) return res.json({ ok: true, run: publicRun(run, false) });
  run.stopRequested = true;
  run.status = 'stopping';
  if (run.finishTimer) clearTimeout(run.finishTimer);
  if (run.finishResolve) run.finishResolve();
  if (run.browser) {
    try { await run.browser.close(); } catch (_) {}
  }
  return res.json({ ok: true, run: publicRun(run, false) });
});

app.get('/', (_req, res) => res.sendFile(path.join(ROOT, 'index.html')));
app.get('/app.js', (_req, res) => res.type('application/javascript').sendFile(path.join(ROOT, 'app.js')));
app.get('/style.css', (_req, res) => res.type('text/css').sendFile(path.join(ROOT, 'style.css')));
app.use((_req, res) => res.status(404).json({ ok: false, error: 'Route not found.' }));

const server = app.listen(PORT, '0.0.0.0', () => {
  console.log('Authorized Browser Test Lab listening on port ' + PORT);
  console.log('Allowed target hostnames: ' + (ALLOWED_TARGET_HOSTS.size ? Array.from(ALLOWED_TARGET_HOSTS).join(', ') : '(none configured; runner stays locked)'));
  console.log('Session cap: ' + MAX_SESSIONS + '; duration cap: ' + MAX_DURATION_SECONDS + ' seconds.');
});

function shutdown() {
  if (activeRunId && runs.has(activeRunId)) {
    const run = runs.get(activeRunId);
    run.stopRequested = true;
    if (run.finishResolve) run.finishResolve();
    if (run.browser) run.browser.close().catch(() => {});
  }
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(1), 10000).unref();
}
process.on('SIGTERM', shutdown);
process.on('SIGINT', shutdown);
