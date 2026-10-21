'use strict';

const express = require('express');
const path = require('node:path');

const app = express();
const PORT = Number(process.env.PORT || 3000);
const ROOT = __dirname;

app.disable('x-powered-by');
app.disable('etag');

app.use((req, res, next) => {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('Referrer-Policy', 'strict-origin-when-cross-origin');
  res.setHeader('X-Frame-Options', 'SAMEORIGIN');
  res.setHeader('Permissions-Policy', 'camera=(), microphone=(), geolocation=()');
  next();
});

app.get('/api/health', (_req, res) => {
  res.set('Cache-Control', 'no-store');
  res.json({
    ok: true,
    name: 'Minecraft AI View Lab',
    mode: 'browser-local-simulation',
    targetRequestsEnabled: false
  });
});

app.get('/', (_req, res) => {
  res.set('Cache-Control', 'no-cache');
  res.sendFile(path.join(ROOT, 'index.html'));
});

app.get('/style.css', (_req, res) => {
  res.set('Cache-Control', 'no-cache');
  res.type('text/css').sendFile(path.join(ROOT, 'style.css'));
});

app.get('/app.js', (_req, res) => {
  res.set('Cache-Control', 'no-cache');
  res.type('application/javascript').sendFile(path.join(ROOT, 'app.js'));
});

app.use((_req, res) => {
  res.status(404).json({ ok: false, error: 'Route not found' });
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`Minecraft AI View Lab listening on port ${PORT}`);
  console.log('Simulation-only mode: the target URL is never requested by the server.');
});
