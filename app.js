'use strict';

(() => {
  const $ = (id) => document.getElementById(id);
  const els = {
    backendUrl: $('backendUrl'),
    token: $('dashboardToken'),
    connect: $('connectButton'),
    connectResult: $('connectResult'),
    connectionPill: $('connectionPill'),
    connectionText: $('connectionText'),
    connectionDot: $('connectionDot'),
    connectionState: $('connectionState'),
    target: $('targetUrl'),
    allowlist: $('allowlistResult'),
    sessions: $('sessions'),
    sessionOutput: $('sessionOutput'),
    sessionCap: $('sessionCap'),
    duration: $('duration'),
    durationOutput: $('durationOutput'),
    authorization: $('authorizationCheck'),
    start: $('startButton'),
    stop: $('stopButton'),
    runResult: $('runResult'),
    runBadge: $('runBadge'),
    fleetDescription: $('fleetDescription'),
    metricSessions: $('metricSessions'),
    metricLoaded: $('metricLoaded'),
    metricFailed: $('metricFailed'),
    metricTime: $('metricTime'),
    grid: $('sessionGrid'),
    empty: $('emptyFleet')
  };

  let baseUrl = '';
  let token = '';
  let config = null;
  let health = null;
  let currentRunId = null;
  let isPolling = false;
  let pollTimer = null;
  let previewCache = new Map();
  let lastRun = null;

  function setResult(element, message, tone) {
    element.textContent = message;
    element.classList.remove('good', 'bad', 'warn');
    if (tone) element.classList.add(tone);
  }

  function normalizeBackendUrl(value) {
    let url;
    try { url = new URL(String(value || '').trim()); } catch (_) { return null; }
    if (!['https:', 'http:'].includes(url.protocol) || !url.hostname ||
        url.username || url.password || (url.pathname !== '/' && url.pathname !== '')) return null;
    if (url.protocol === 'http:' && !['localhost', '127.0.0.1'].includes(url.hostname)) return null;
    url.hash = '';
    url.search = '';
    return url.origin;
  }

  function apiUrl(path) {
    return baseUrl.replace(/\/+$/, '') + path;
  }

  async function api(path, options) {
    const settings = options || {};
    const headers = Object.assign({}, settings.headers || {});
    if (token) headers.Authorization = 'Bearer ' + token;
    if (settings.body !== undefined) headers['Content-Type'] = 'application/json';
    let response;
    try {
      response = await fetch(apiUrl(path), Object.assign({}, settings, {
        headers,
        cache: 'no-store',
        mode: 'cors',
        credentials: 'omit'
      }));
    } catch (_) {
      throw new Error('Cannot reach the backend. Check the service URL, deployment status, and allowed browser origins.');
    }
    let data;
    try { data = await response.json(); } catch (_) { data = {}; }
    if (!response.ok || data.ok === false) {
      throw new Error(data.error || ('Backend returned HTTP ' + response.status + '.'));
    }
    return data;
  }

  function setConnection(online, text) {
    els.connectionText.textContent = online ? 'BACKEND CONNECTED' : 'BACKEND NOT CONNECTED';
    els.connectionPill.classList.toggle('offline', !online);
    els.connectionDot.classList.toggle('online', online);
    els.connectionState.textContent = online ? 'ONLINE' : 'OFFLINE';
    if (text) setResult(els.connectResult, text, online ? 'good' : 'warn');
  }

  function applyConfig() {
    const allowedHosts = config && Array.isArray(config.allowedHosts) ? config.allowedHosts : [];
    const maxSessions = Math.max(1, Math.min(100, Number(config && config.maxSessions) || 1));
    const maxMinutes = Math.max(1, Math.min(5, Math.floor((Number(config && config.maxDurationSeconds) || 60) / 60)));
    els.sessions.max = String(maxSessions);
    if (Number(els.sessions.value) > maxSessions) els.sessions.value = String(maxSessions);
    els.sessionCap.textContent = 'BACKEND CAP: ' + maxSessions;
    const finalRangeText = els.sessionCap.parentElement.lastElementChild;
    if (finalRangeText) finalRangeText.textContent = maxSessions + (maxSessions === 1 ? ' MAX' : ' MAX');
    els.duration.max = String(maxMinutes);
    if (Number(els.duration.value) > maxMinutes) els.duration.value = String(maxMinutes);
    const allowlistText = allowedHosts.length
      ? 'Allowed hostnames: ' + allowedHosts.join(', ') + '. The backend will reject every other hostname.'
      : 'No authorized hostnames are configured yet. Add your exact domain to ALLOWED_TARGET_HOSTS in the backend hosting settings.';
    setResult(els.allowlist, allowlistText, allowedHosts.length ? 'good' : 'warn');
    if (!allowedHosts.length) {
      setResult(els.runResult, 'Backend connected, but the runner is locked until ALLOWED_TARGET_HOSTS contains a domain you control or are authorized to test.', 'warn');
    }
    updateControlText();
  }

  function targetIsAllowed() {
    if (!config || !Array.isArray(config.allowedHosts)) return false;
    try {
      const url = new URL(els.target.value.trim());
      if (url.protocol !== 'https:' || url.username || url.password) return false;
      return config.allowedHosts.includes(url.hostname.toLowerCase().replace(/\.$/, ''));
    } catch (_) {
      return false;
    }
  }

  function updateControlText() {
    const count = Math.max(1, Math.min(Number(els.sessions.max) || 1, Number(els.sessions.value) || 1));
    const minutes = Math.max(1, Math.min(Number(els.duration.max) || 1, Number(els.duration.value) || 1));
    els.sessions.value = String(count);
    els.duration.value = String(minutes);
    els.sessionOutput.textContent = String(count);
    els.durationOutput.textContent = minutes + (minutes === 1 ? ' MIN' : ' MINS');
    const ready = Boolean(baseUrl && token && config && health && health.ready &&
      targetIsAllowed() && els.authorization.checked && !isActiveRun(lastRun));
    els.start.disabled = !ready;
    els.stop.disabled = !currentRunId || !isActiveRun(lastRun);
  }

  function isActiveRun(run) {
    return Boolean(run && ['queued', 'starting', 'running', 'stopping'].includes(run.status));
  }

  function formatTime(seconds) {
    const total = Math.max(0, Number(seconds) || 0);
    const minutes = Math.floor(total / 60);
    const secondsPart = total % 60;
    return minutes ? minutes + ':' + String(secondsPart).padStart(2, '0') : secondsPart + 's';
  }

  function statusLabel(status) {
    return String(status || 'queued').replace(/-/g, ' ').toUpperCase();
  }

  function paintSessions(run) {
    els.grid.replaceChildren();
    if (!run || !Array.isArray(run.sessions) || !run.sessions.length) {
      els.empty.hidden = false;
      return;
    }
    els.empty.hidden = true;
    run.sessions.forEach((session) => {
      const card = document.createElement('article');
      card.className = 'browser-card';

      const bar = document.createElement('div');
      bar.className = 'browser-bar';
      const name = document.createElement('span');
      name.className = 'browser-name';
      name.textContent = 'CHROMIUM ' + String(session.id).padStart(2, '0');
      const badge = document.createElement('span');
      badge.className = 'status ' + String(session.status || '').replace(/[^a-z-]/gi, '');
      badge.textContent = statusLabel(session.status);
      bar.append(name, badge);

      const preview = document.createElement('div');
      preview.className = 'preview';
      const dataUrl = session.preview || previewCache.get(session.id);
      if (dataUrl) {
        previewCache.set(session.id, dataUrl);
        const image = document.createElement('img');
        image.alt = 'Screenshot preview from Chromium session ' + session.id;
        image.loading = 'lazy';
        image.src = dataUrl;
        preview.append(image);
      } else {
        const placeholder = document.createElement('div');
        placeholder.className = 'preview-placeholder';
        placeholder.textContent = session.status === 'failed' ? 'BROWSER LOAD FAILED' :
          session.status === 'loaded' ? 'CAPTURING PREVIEW…' : 'BROWSER SESSION ' + statusLabel(session.status);
        preview.append(placeholder);
      }

      const details = document.createElement('div');
      details.className = 'card-details';
      const statusLine = document.createElement('div');
      statusLine.textContent = 'RESULT: ' + (session.httpStatus === null || session.httpStatus === undefined ? '—' : 'HTTP ' + session.httpStatus);
      const timingLine = document.createElement('div');
      timingLine.textContent = 'LOAD: ' + (session.elapsedMs === null || session.elapsedMs === undefined ? '—' : session.elapsedMs + ' ms');
      details.append(statusLine, timingLine);
      if (session.error) {
        const errorLine = document.createElement('div');
        errorLine.textContent = 'NOTE: ' + session.error;
        errorLine.style.color = 'var(--red)';
        details.append(errorLine);
      }
      card.append(bar, preview, details);
      els.grid.append(card);
    });
  }

  function renderRun(run) {
    if (!run) return;
    lastRun = run;
    const sessions = Array.isArray(run.sessions) ? run.sessions : [];
    const loaded = sessions.filter((session) => session.status === 'loaded').length;
    const failed = sessions.filter((session) => session.status === 'failed').length;
    const settled = sessions.filter((session) => ['loaded', 'failed', 'stopped'].includes(session.status)).length;
    const active = isActiveRun(run);
    els.metricSessions.textContent = settled + ' / ' + sessions.length;
    els.metricLoaded.textContent = String(loaded);
    els.metricFailed.textContent = String(failed);
    els.metricTime.textContent = active ? formatTime(run.remainingSeconds) : (run.status === 'completed' ? '0s' : '--');
    els.runBadge.textContent = statusLabel(run.status);
    els.fleetDescription.textContent = run.target + ' · ' + sessions.length + ' isolated sessions · ' + (active ? 'running remotely' : 'run finished');
    const message = run.message || (active
      ? 'Run ' + run.id.slice(0, 8) + ' is ' + run.status + '. Loaded ' + loaded + ' of ' + sessions.length + ' sessions; ' + failed + ' failures so far.'
      : 'Run ' + run.id.slice(0, 8) + ' ' + run.status + '. Loaded ' + loaded + ' sessions; ' + failed + ' failed.');
    setResult(els.runResult, message, run.status === 'failed' || failed ? 'warn' : active ? 'good' : '');
    paintSessions(run);
    updateControlText();
  }

  async function connect() {
    const normalized = normalizeBackendUrl(els.backendUrl.value);
    const suppliedToken = els.token.value.trim();
    if (!normalized) {
      setConnection(false, 'Enter a valid backend base URL, such as https://your-service.onrender.com.');
      return;
    }
    if (!suppliedToken) {
      setConnection(false, 'Paste the DASHBOARD_TOKEN configured on the backend host.');
      return;
    }
    baseUrl = normalized;
    token = suppliedToken;
    localStorage.setItem('autoBrowserLabBackend', baseUrl);
    sessionStorage.setItem('autoBrowserLabToken', token);
    els.connect.disabled = true;
    els.connect.textContent = 'CONNECTING…';
    try {
      const results = await Promise.all([
        api('/api/health'),
        api('/api/config'),
        api('/api/runs/active')
      ]);
      health = results[0];
      config = results[1];
      setConnection(true, health.ready
        ? 'Backend connected. Chromium runner is ready; configured maximum is ' + config.maxSessions + ' session(s).'
        : 'Backend is reachable but locked. Configure at least one allowed hostname and a dashboard token on the host.');
      applyConfig();
      const active = results[2].run;
      if (active) {
        currentRunId = active.id;
        previewCache = new Map();
        renderRun(active);
        beginPolling();
      } else {
        currentRunId = null;
      }
      updateControlText();
    } catch (error) {
      health = null;
      config = null;
      setConnection(false, error.message);
      updateControlText();
    } finally {
      els.connect.disabled = false;
      els.connect.textContent = 'CONNECT ↗';
    }
  }

  async function startRun() {
    if (!health || !config || !health.ready) {
      setResult(els.runResult, 'Connect a ready backend first.', 'warn');
      return;
    }
    if (!targetIsAllowed()) {
      setResult(els.runResult, 'The target hostname must exactly match one of the backend allowlisted domains.', 'warn');
      return;
    }
    if (!els.authorization.checked) {
      setResult(els.runResult, 'Confirm that you own or have explicit permission to test this site.', 'warn');
      return;
    }
    els.start.disabled = true;
    els.start.textContent = 'LAUNCHING…';
    try {
      const result = await api('/api/runs/start', {
        method: 'POST',
        body: JSON.stringify({
          targetUrl: els.target.value.trim(),
          sessions: Number(els.sessions.value),
          durationSeconds: Number(els.duration.value) * 60,
          confirmAuthorized: true
        })
      });
      currentRunId = result.run.id;
      previewCache = new Map();
      renderRun(result.run);
      setResult(els.runResult, 'Browser run queued. Session cards will update as Chromium loads the target.', 'good');
      beginPolling();
    } catch (error) {
      setResult(els.runResult, error.message, 'bad');
    } finally {
      els.start.textContent = '▶ LAUNCH BROWSER SESSIONS';
      updateControlText();
    }
  }

  function beginPolling() {
    if (pollTimer) clearTimeout(pollTimer);
    pollTimer = null;
    pollRun();
  }

  async function pollRun() {
    if (!currentRunId || isPolling) return;
    isPolling = true;
    try {
      const requestPreviews = !lastRun || !Array.isArray(lastRun.sessions) ||
        lastRun.sessions.some((session) => session.status === 'loaded' && !previewCache.has(session.id));
      const suffix = requestPreviews ? '?previews=1' : '';
      const result = await api('/api/runs/' + encodeURIComponent(currentRunId) + suffix);
      renderRun(result.run);
      if (isActiveRun(result.run)) {
        pollTimer = setTimeout(() => {
          pollTimer = null;
          pollRun();
        }, 1800);
      } else {
        pollTimer = null;
        updateControlText();
      }
    } catch (error) {
      setResult(els.runResult, error.message + ' The run may still be active on the backend; reconnect to recover status.', 'warn');
      pollTimer = setTimeout(() => {
        pollTimer = null;
        pollRun();
      }, 4000);
    } finally {
      isPolling = false;
    }
  }

  async function stopRun() {
    if (!currentRunId) return;
    els.stop.disabled = true;
    els.stop.textContent = 'STOPPING…';
    try {
      const result = await api('/api/runs/' + encodeURIComponent(currentRunId) + '/stop', { method: 'POST' });
      renderRun(result.run);
      setResult(els.runResult, 'Stop signal sent. The backend is closing every browser session in this run.', 'warn');
      beginPolling();
    } catch (error) {
      setResult(els.runResult, error.message, 'bad');
    } finally {
      els.stop.textContent = '■ STOP ALL';
      updateControlText();
    }
  }

  els.connect.addEventListener('click', connect);
  els.start.addEventListener('click', startRun);
  els.stop.addEventListener('click', stopRun);
  els.sessions.addEventListener('input', updateControlText);
  els.duration.addEventListener('input', updateControlText);
  els.target.addEventListener('input', updateControlText);
  els.authorization.addEventListener('change', updateControlText);
  els.backendUrl.value = localStorage.getItem('autoBrowserLabBackend') || '';
  els.token.value = sessionStorage.getItem('autoBrowserLabToken') || '';
  updateControlText();

  if (els.backendUrl.value && els.token.value) {
    // Automatic reconnection uses only this tab's session token.
    connect();
  }
})();
