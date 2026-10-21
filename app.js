'use strict';

(function () {
  const STORE_KEY = 'minecraftAiViewLab:v1';
  const MAX_LOGS = 180;
  const MAX_POINTS = 36;
  const $ = (id) => document.getElementById(id);

  const el = {
    target: $('targetUrl'), slider: $('rateSlider'), rate: $('rateValue'),
    rateOutput: $('rateOutput'), addTarget: $('addTargetButton'), targetList: $('targetList'), targetFeedback: $('targetFeedback'), total: $('totalEvents'), current: $('runEvents'),
    uptime: $('uptimeValue'), runText: $('runStateText'), status: $('statusBadge'),
    form: $('generatorForm'), start: $('startButton'), stop: $('stopButton'),
    activity: $('activityList'), activityCount: $('activityCount'), activityStatus: $('activityStatus'),
    clearLog: $('clearLogButton'), export: $('exportButton'), reset: $('resetButton'),
    openTarget: $('openTargetButton'), canvas: $('activityChart'), chartEmpty: $('chartEmpty'),
    chartPeak: $('chartPeak'), help: $('helpButton'), closeHelp: $('closeHelpButton'),
    gotIt: $('gotItButton'), dialog: $('helpDialog'), toast: $('toast')
  };

  const state = {
    total: 0, current: 0, target: 'https://example.com', targets: [], rate: 5,
    running: false, stopped: false, startedAt: null, elapsed: 0,
    timer: null, clock: null, toastTimer: null,
    logs: [], points: Array(MAX_POINTS).fill(0)
  };

  function validUrl(value) {
    try {
      const url = new URL(String(value || '').trim());
      if (url.protocol !== 'https:' && url.protocol !== 'http:') return null;
      if (!url.hostname || url.username || url.password) return null;
      return url;
    } catch (_) {
      return null;
    }
  }

  function number(value) {
    return new Intl.NumberFormat().format(value);
  }

  function duration(seconds) {
    const value = Math.max(0, Math.floor(seconds));
    const hours = Math.floor(value / 3600);
    const minutes = Math.floor((value % 3600) / 60);
    const secs = value % 60;
    if (hours) return String(hours).padStart(2, '0') + ':' + String(minutes).padStart(2, '0') + ':' + String(secs).padStart(2, '0');
    return String(minutes).padStart(2, '0') + ':' + String(secs).padStart(2, '0');
  }

  function clockTime(timestamp) {
    return new Date(timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  }

  function ago(timestamp) {
    const secs = Math.max(0, Math.floor((Date.now() - timestamp) / 1000));
    if (secs < 5) return 'just now';
    if (secs < 60) return secs + 's ago';
    return Math.floor(secs / 60) + 'm ago';
  }

  function save() {
    try {
      localStorage.setItem(STORE_KEY, JSON.stringify({
        total: state.total, target: state.target, rate: state.rate,
        logs: state.logs.slice(0, MAX_LOGS), points: state.points.slice(-MAX_POINTS),
        targets: state.targets.slice(0, 10)
      }));
    } catch (error) {
      console.warn('View Lab could not save local state.', error);
      toast('Browser storage is full. Export your event log.');
    }
  }

  function restore() {
    try {
      const raw = localStorage.getItem(STORE_KEY);
      if (!raw) return;
      const data = JSON.parse(raw);
      if (Number.isSafeInteger(data.total) && data.total >= 0) state.total = data.total;
      if (typeof data.target === 'string' && data.target.length <= 2048) state.target = data.target;
      if (Number.isInteger(data.rate)) state.rate = Math.max(1, Math.min(60, data.rate));
      if (Array.isArray(data.targets)) state.targets = data.targets.map(validUrl).filter(Boolean).map((url) => url.href).slice(0, 10);
      if (Array.isArray(data.logs)) {
        state.logs = data.logs.filter((item) => item && typeof item.message === 'string' && Number.isFinite(item.time))
          .slice(0, MAX_LOGS).map((item) => ({
            message: item.message.slice(0, 240), detail: String(item.detail || '').slice(0, 400),
            time: item.time, simulated: true
          }));
      }
      if (Array.isArray(data.points)) state.points = data.points.filter(Number.isFinite).slice(-MAX_POINTS);
    } catch (error) {
      console.warn('Saved local state could not be parsed.', error);
    }
  }

  function toast(message) {
    el.toast.textContent = message;
    el.toast.classList.add('visible');
    window.clearTimeout(state.toastTimer);
    state.toastTimer = window.setTimeout(() => el.toast.classList.remove('visible'), 2800);
  }

  function renderLogs() {
    el.activity.replaceChildren();
    el.activityCount.textContent = String(state.logs.length);
    if (!state.logs.length) {
      const empty = document.createElement('div');
      empty.className = 'empty-state';
      const glyph = document.createElement('div');
      glyph.className = 'empty-glyph';
      glyph.textContent = '▦';
      const title = document.createElement('strong');
      title.textContent = 'No events yet';
      const description = document.createElement('span');
      description.textContent = 'Start the generator and the simulated sessions will appear here.';
      empty.append(glyph, title, description);
      el.activity.append(empty);
      return;
    }

    const fragment = document.createDocumentFragment();
    state.logs.forEach((item) => {
      const row = document.createElement('div');
      row.className = 'event-row';
      const icon = document.createElement('span');
      icon.className = 'event-icon';
      icon.textContent = '↗';
      const copy = document.createElement('div');
      copy.className = 'event-copy';
      const title = document.createElement('strong');
      title.textContent = item.message;
      const detail = document.createElement('small');
      detail.textContent = item.detail || 'Local simulation event · no network request';
      const timestamp = document.createElement('span');
      timestamp.className = 'event-time';
      timestamp.textContent = ago(item.time);
      timestamp.title = clockTime(item.time);
      copy.append(title, detail);
      row.append(icon, copy, timestamp);
      fragment.append(row);
    });
    el.activity.append(fragment);
  }

  function renderTargets() {
    if (!el.targetList || !el.targetFeedback) return;
    el.targetList.replaceChildren();
    const count = state.targets.length;
    el.targetFeedback.textContent = count ? count + ' target' + (count === 1 ? '' : 's') + ' saved' : 'No targets added yet';
    state.targets.forEach((value, index) => {
      const url = validUrl(value);
      if (!url) return;
      const row = document.createElement('div');
      row.className = 'target-chip';
      const icon = document.createElement('span');
      icon.className = 'target-chip-icon';
      icon.textContent = '↗';
      const name = document.createElement('span');
      name.className = 'target-chip-name';
      name.textContent = url.hostname + (url.pathname !== '/' ? url.pathname : '');
      name.title = url.href;
      const remove = document.createElement('button');
      remove.type = 'button';
      remove.className = 'target-remove';
      remove.textContent = 'REMOVE';
      remove.disabled = state.running;
      remove.setAttribute('aria-label', 'Remove target ' + url.hostname);
      remove.addEventListener('click', () => removeTarget(index));
      row.append(icon, name, remove);
      el.targetList.append(row);
    });
  }

  function addTarget() {
    const typed = String(el.target.value || '').trim();
    const url = validUrl(typed);
    if (!url) {
      el.target.setCustomValidity('Enter a valid HTTP or HTTPS URL, for example https://example.com.');
      el.target.reportValidity();
      el.target.addEventListener('input', () => el.target.setCustomValidity(''), { once: true });
      return;
    }
    if (state.targets.includes(url.href)) {
      toast('That target is already in your list.');
      el.target.value = '';
      return;
    }
    if (state.targets.length >= 10) {
      toast('You can save up to 10 targets in this demo.');
      return;
    }
    state.targets.push(url.href);
    state.target = url.href;
    el.target.value = '';
    el.target.setCustomValidity('');
    renderTargets();
    save();
    toast('Target added. The run will simulate events only.');
  }

  function removeTarget(index) {
    if (state.running || index < 0 || index >= state.targets.length) return;
    const removed = state.targets.splice(index, 1)[0];
    if (state.target === removed) state.target = state.targets[0] || 'https://example.com';
    renderTargets();
    save();
    toast('Target removed from the local list.');
  }

  function log(message, detail) {
    state.logs.unshift({
      time: Date.now(), message: String(message), detail: String(detail || ''),
      simulated: true
    });
    if (state.logs.length > MAX_LOGS) state.logs.length = MAX_LOGS;
    renderLogs();
    save();
  }

  function elapsedSeconds() {
    return state.startedAt ? state.elapsed + (Date.now() - state.startedAt) / 1000 : state.elapsed;
  }

  function setMode(mode) {
    const active = mode === 'running';
    el.status.classList.toggle('running', active);
    el.status.classList.toggle('stopped', mode === 'stopped');
    el.status.innerHTML = '<i></i> ' + (active ? 'RUNNING' : mode === 'stopped' ? 'STOPPED' : 'STANDBY');
    el.start.disabled = active;
    el.stop.disabled = !active;
    el.slider.disabled = active;
    el.target.disabled = active;
    if (el.addTarget) el.addTarget.disabled = active;
    renderTargets();
    el.runText.textContent = active ? 'Generator is running' : mode === 'stopped' ? 'Stopped by user' : 'Ready to launch';
    el.activityStatus.innerHTML = '<i' + (active ? ' class="active"' : '') + '></i> ' +
      (active ? 'Generating local events' : mode === 'stopped' ? 'Run stopped' : 'Waiting for a run');
  }

  function updateRate() {
    state.rate = Math.max(1, Math.min(60, Number(el.slider.value) || 1));
    el.rate.textContent = String(state.rate);
    el.rateOutput.textContent = state.rate + (state.rate === 1 ? ' event / min' : ' events / min');
    const percent = ((state.rate - 1) / 59) * 100;
    el.slider.style.background = 'linear-gradient(90deg,var(--green) 0%,var(--green) ' + percent + '%,#34442f ' + percent + '%,#34442f 100%)';
  }

  function renderStats() {
    el.total.textContent = number(state.total);
    el.current.textContent = number(state.current);
    el.uptime.textContent = duration(elapsedSeconds());
    el.chartPeak.textContent = number(state.current);
    el.chartEmpty.hidden = state.current > 0;
    drawChart();
  }

  function drawChart() {
    const canvas = el.canvas;
    const rect = canvas.getBoundingClientRect();
    if (rect.width < 2 || rect.height < 2) return;
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const width = Math.floor(rect.width * dpr);
    const height = Math.floor(rect.height * dpr);
    if (canvas.width !== width || canvas.height !== height) {
      canvas.width = width;
      canvas.height = height;
    }
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    ctx.clearRect(0, 0, width, height);
    if (state.current === 0 || state.points.every((point) => point === 0)) return;

    const pad = 10 * dpr;
    const top = 14 * dpr;
    const usable = height - top - pad;
    const max = Math.max(5, ...state.points) * 1.12;
    const points = state.points.map((value, index) => ({
      x: index / Math.max(1, state.points.length - 1) * width,
      y: height - pad - value / max * usable
    }));

    ctx.beginPath();
    ctx.moveTo(points[0].x, height - pad);
    points.forEach((point) => ctx.lineTo(point.x, point.y));
    ctx.lineTo(points[points.length - 1].x, height - pad);
    ctx.closePath();
    const fill = ctx.createLinearGradient(0, top, 0, height);
    fill.addColorStop(0, 'rgba(158,246,106,.22)');
    fill.addColorStop(1, 'rgba(158,246,106,.005)');
    ctx.fillStyle = fill;
    ctx.fill();

    ctx.beginPath();
    points.forEach((point, index) => index === 0 ? ctx.moveTo(point.x, point.y) : ctx.lineTo(point.x, point.y));
    ctx.strokeStyle = '#9ef66a';
    ctx.lineWidth = 2 * dpr;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.shadowColor = '#9ef66a44';
    ctx.shadowBlur = 8 * dpr;
    ctx.stroke();
    ctx.shadowBlur = 0;
    const last = points[points.length - 1];
    ctx.beginPath();
    ctx.arc(last.x, last.y, 3 * dpr, 0, Math.PI * 2);
    ctx.fillStyle = '#cfffad';
    ctx.fill();
  }

  function generateEvent() {
    if (!state.running) return;
    state.current += 1;
    state.total += 1;
    state.points.push(state.current);
    if (state.points.length > MAX_POINTS) state.points.shift();
    const targetValue = state.targets.length ? state.targets[(state.current - 1) % state.targets.length] : state.target;
    const host = validUrl(targetValue) ? new URL(targetValue).hostname : 'no-target-selected';
    log('Virtual session #' + number(state.total), host + ' · simulated only · no request sent');
    renderStats();
    save();
  }

  function start(event) {
    if (event) event.preventDefault();
    if (state.running) return;
    const url = validUrl(el.target.value);
    if (!url) {
      el.target.setCustomValidity('Enter a valid HTTP or HTTPS URL, for example https://example.com.');
      el.target.reportValidity();
      el.target.addEventListener('input', () => el.target.setCustomValidity(''), { once: true });
      return;
    }

    if (!state.targets.includes(url.href)) {
      if (state.targets.length >= 10) {
        toast('Target list is full. Remove a target before adding another.');
        return;
      }
      state.targets.push(url.href);
    }
    if (!state.targets.length) {
      el.target.setCustomValidity('Add a target website before starting the simulation.');
      el.target.reportValidity();
      return;
    }
    state.target = state.targets[0];
    el.target.value = '';
    renderTargets();
    state.current = 0;
    state.elapsed = 0;
    state.startedAt = Date.now();
    state.points = Array(MAX_POINTS).fill(0);
    state.running = true;
    state.stopped = false;
    el.activity.scrollTop = 0;
    setMode('running');
    log('Simulation started', url.hostname + ' · ' + state.rate + ' virtual events/min · reference only');
    renderStats();
    save();

    state.timer = window.setInterval(generateEvent, Math.max(1000, Math.floor(60000 / state.rate)));
    state.clock = window.setInterval(renderStats, 1000);
    toast('Generator started. Events are simulated locally.');
  }

  function stop() {
    if (!state.running) return;
    state.elapsed = elapsedSeconds();
    state.startedAt = null;
    state.running = false;
    state.stopped = true;
    window.clearInterval(state.timer);
    window.clearInterval(state.clock);
    state.timer = null;
    state.clock = null;
    setMode('stopped');
    log('Simulation stopped', number(state.current) + ' simulated events in this run');
    renderStats();
    save();
    toast('Generator stopped. Your local log is saved.');
  }

  function reset() {
    if (state.running) stop();
    state.total = 0;
    state.current = 0;
    state.elapsed = 0;
    state.startedAt = null;
    state.stopped = false;
    state.logs = [];
    state.points = Array(MAX_POINTS).fill(0);
    renderLogs();
    setMode('standby');
    renderStats();
    save();
    toast('Counters and activity log have been reset.');
  }

  function openTarget() {
    const url = validUrl(el.target.value) || (state.targets.length ? validUrl(state.targets[0]) : null);
    if (!url) {
      el.target.setCustomValidity('Enter a valid HTTP or HTTPS URL first.');
      el.target.reportValidity();
      el.target.addEventListener('input', () => el.target.setCustomValidity(''), { once: true });
      return;
    }
    window.open(url.href, '_blank', 'noopener,noreferrer');
    toast('Opened the target manually in a new tab.');
  }

  function csvCell(value) {
    return '"' + String(value == null ? '' : value).replace(/"/g, '""') + '"';
  }

  function exportCsv() {
    const rows = [['timestamp_iso', 'timestamp_local', 'event', 'details', 'classification']];
    state.logs.slice().sort((a, b) => a.time - b.time).forEach((item) => {
      rows.push([new Date(item.time).toISOString(), clockTime(item.time), item.message,
        item.detail || '', 'SIMULATED_NOT_REAL_TRAFFIC']);
    });
    const csv = '\uFEFF' + rows.map((row) => row.map(csvCell).join(',')).join('\r\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const objectUrl = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = objectUrl;
    link.download = 'minecraft-ai-view-lab-' + new Date().toISOString().replace(/[:.]/g, '-') + '.csv';
    document.body.append(link);
    link.click();
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
    toast('Exported ' + (rows.length - 1) + ' local log rows as CSV.');
  }

  function clearLog() {
    state.logs = [];
    renderLogs();
    save();
    toast('Activity log cleared. Counters were kept.');
  }

  function openHelp() {
    if (typeof el.dialog.showModal === 'function') el.dialog.showModal();
    else toast('This tool only creates browser-local simulated events.');
  }

  function closeHelp() {
    if (el.dialog.open) el.dialog.close();
  }

  function init() {
    restore();
    el.target.value = state.target;
    el.slider.value = String(state.rate);
    updateRate();
    setMode('standby');
    renderTargets();
    renderLogs();
    renderStats();

    el.form.addEventListener('submit', start);
    el.stop.addEventListener('click', stop);
    el.slider.addEventListener('input', updateRate);
    el.slider.addEventListener('change', save);
    el.target.addEventListener('change', function () {
      const url = validUrl(el.target.value);
      if (url) {
        state.target = url.href;
        el.target.setCustomValidity('');
        save();
      }
    });
    el.addTarget.addEventListener('click', addTarget);
    el.openTarget.addEventListener('click', openTarget);
    el.export.addEventListener('click', exportCsv);
    el.reset.addEventListener('click', reset);
    el.clearLog.addEventListener('click', clearLog);
    el.help.addEventListener('click', openHelp);
    el.closeHelp.addEventListener('click', closeHelp);
    el.gotIt.addEventListener('click', closeHelp);
    el.dialog.addEventListener('click', (event) => {
      if (event.target === el.dialog) closeHelp();
    });
    window.addEventListener('resize', drawChart, { passive: true });
    window.addEventListener('beforeunload', function () {
      window.clearInterval(state.timer);
      window.clearInterval(state.clock);
      save();
    });
    window.setInterval(function () {
      if (state.logs.length) renderLogs();
    }, 15000);
  }

  init();
})();
