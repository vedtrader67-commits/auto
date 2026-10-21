'use strict';
(() => {
  const $ = (id) => document.getElementById(id);
  const form = $('checkForm');
  const target = $('targetUrl');
  const checkButton = $('checkButton');
  const result = $('result');
  const checks = $('checks');
  const latency = $('latency');
  const clients = $('clients');
  const clientOutput = $('clientOutput');
  const duration = $('duration');
  const planTitle = $('planTitle');
  const planText = $('planText');
  let totalChecks = 0;

  function validTarget() {
    try {
      const url = new URL(target.value.trim());
      if (!['http:', 'https:'].includes(url.protocol) || !url.hostname || url.username || url.password) return null;
      return url;
    } catch (_) { return null; }
  }

  function updatePlan() {
    const count = Math.max(1, Math.min(100, Number(clients.value) || 1));
    const minutes = Math.max(1, Math.min(10, Number(duration.value) || 1));
    clients.value = String(count);
    duration.value = String(minutes);
    clientOutput.value = String(count);
    clientOutput.textContent = String(count);
    planTitle.textContent = count + ' planned client' + (count === 1 ? '' : 's') + ' · ' + minutes + ' minute' + (minutes === 1 ? '' : 's');
    planText.textContent = 'Planning only: ' + count + ' concurrent clients for ' + minutes + ' minute' + (minutes === 1 ? '' : 's') + '. No repeated traffic or browser sessions are launched from this static page.';
  }

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const url = validTarget();
    if (!url) {
      result.textContent = 'Enter a valid http:// or https:// website URL.';
      target.focus();
      return;
    }
    checkButton.disabled = true;
    checkButton.textContent = 'Checking…';
    result.textContent = 'Sending one live browser request to ' + url.hostname + '…';
    const started = performance.now();
    try {
      const response = await fetch(url.href, { method: 'GET', mode: 'cors', cache: 'no-store', redirect: 'follow' });
      const elapsed = Math.round(performance.now() - started);
      totalChecks += 1;
      checks.textContent = String(totalChecks);
      latency.textContent = elapsed + ' ms';
      result.textContent = 'Request completed. HTTP ' + response.status + (response.ok ? ' · response received' : ' · server returned an error status') + ' · ' + elapsed + ' ms. This was one check, not a generated view.';
    } catch (error) {
      const elapsed = Math.round(performance.now() - started);
      totalChecks += 1;
      checks.textContent = String(totalChecks);
      latency.textContent = elapsed + ' ms';
      result.textContent = 'The browser could not read a response. The site may block cross-origin requests (CORS), require a redirect/login, or be unreachable. This result alone cannot prove the site is down. One check was attempted.';
    } finally {
      checkButton.disabled = false;
      checkButton.textContent = 'Check website ↗';
    }
  });

  $('openButton').addEventListener('click', () => {
    const url = validTarget();
    if (!url) { result.textContent = 'Enter a valid http:// or https:// website URL first.'; return; }
    window.open(url.href, '_blank', 'noopener,noreferrer');
  });
  clients.addEventListener('input', updatePlan);
  duration.addEventListener('input', updatePlan);
  updatePlan();
})();