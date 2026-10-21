(() => {
  "use strict";

  // These counters track visits to the AUTO homepage, not the counter dashboard.
  const API = "https://page-views-api.ratneshc.com/api/v1";
  const SITE = "vedtrader67-commits.github.io";
  const HOME_PATH = "/auto/";
  const BUCKET_MS = 5 * 60 * 1000;
  const REFRESH_MS = 3 * 1000;

  const $ = (id) => document.getElementById(id);
  const els = {
    badge: $("connectionBadge"),
    total: $("totalViews"),
    hour: $("hourViews"),
    current: $("currentViews"),
    updated: $("updatedAt"),
    refreshState: $("refreshState"),
    status: $("statusText"),
    chart: $("trafficChart"),
    empty: $("chartEmpty"),
    chartTotal: $("chartTotal"),
    bucketList: $("bucketList"),
    refresh: $("refreshButton")
  };

  let busy = false;
  let lastData = null;

  const fmt = (value) => Number(value || 0).toLocaleString();
  const bucketFor = (timestamp) => Math.floor(timestamp / BUCKET_MS);
  const pathForBucket = (bucket) => `/auto/live-hour/${bucket}`;

  async function readCount(path) {
    const query = new URLSearchParams({ site: SITE, path });
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 9000);
    try {
      const response = await fetch(`${API}/views?${query.toString()}`, {
        method: "GET",
        cache: "no-store",
        credentials: "omit",
        signal: controller.signal
      });
      if (!response.ok) throw new Error(`Counter API returned ${response.status}`);
      const data = await response.json();
      const value = Number(data.views);
      if (!Number.isFinite(value) || value < 0) throw new Error("Counter API returned an unexpected response");
      return value;
    } finally {
      clearTimeout(timeout);
    }
  }

  function setStatus(ok, message) {
    els.badge.classList.toggle("error", !ok);
    els.badge.lastChild.textContent = ok ? " LIVE" : " OFFLINE";
    els.refreshState.textContent = message;
    els.status.textContent = message;
  }

  function drawChart(values) {
    const canvas = els.chart;
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    if (!rect.width || !rect.height) return;
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    canvas.width = Math.round(rect.width * dpr);
    canvas.height = Math.round(rect.height * dpr);
    const ctx = canvas.getContext("2d");
    ctx.scale(dpr, dpr);

    const width = rect.width;
    const height = rect.height;
    const pad = { top: 20, right: 6, bottom: 22, left: 6 };
    const chartH = height - pad.top - pad.bottom;
    const chartW = width - pad.left - pad.right;
    const max = Math.max(1, ...values);
    const gap = Math.max(4, Math.min(10, chartW * .012));
    const barW = Math.max(4, (chartW - gap * (values.length - 1)) / values.length);

    ctx.clearRect(0, 0, width, height);
    ctx.font = "9px 'DM Mono', monospace";
    ctx.textBaseline = "middle";

    for (let line = 0; line < 4; line++) {
      const y = pad.top + chartH * line / 3;
      ctx.beginPath();
      ctx.strokeStyle = "rgba(119,151,122,.15)";
      ctx.lineWidth = 1;
      ctx.moveTo(pad.left, Math.round(y) + .5);
      ctx.lineTo(width - pad.right, Math.round(y) + .5);
      ctx.stroke();
    }

    values.forEach((value, index) => {
      const x = pad.left + index * (barW + gap);
      const barH = value > 0 ? Math.max(3, (value / max) * (chartH - 8)) : 2;
      const y = pad.top + chartH - barH;
      ctx.fillStyle = index === values.length - 1 ? "#8cff91" : "rgba(72,219,120,.58)";
      ctx.fillRect(x, y, barW, barH);
      if (value > 0 && barW > 11) {
        ctx.fillStyle = "#cde9cf";
        ctx.textAlign = "center";
        ctx.fillText(String(value), x + barW / 2, Math.max(9, y - 9));
      }
    });

    const step = Math.max(1, Math.floor(values.length / 6));
    values.forEach((_, index) => {
      if (index % step !== 0 && index !== values.length - 1) return;
      const x = pad.left + index * (barW + gap) + barW / 2;
      const minsAgo = (values.length - 1 - index) * 5;
      ctx.fillStyle = "#718574";
      ctx.textAlign = index === 0 ? "left" : index === values.length - 1 ? "right" : "center";
      ctx.fillText(minsAgo === 0 ? "NOW" : `-${minsAgo}m`, x, height - 7);
    });
  }

  function renderBuckets(buckets, counts) {
    els.bucketList.replaceChildren();
    buckets.forEach((bucket, i) => {
      const item = document.createElement("div");
      item.className = "bucket-item";
      const time = document.createElement("small");
      const minsAgo = (buckets.length - 1 - i) * 5;
      time.textContent = minsAgo === 0 ? "NOW" : `-${minsAgo} MIN`;
      const value = document.createElement("b");
      value.textContent = fmt(counts[i]);
      item.append(time, value);
      els.bucketList.append(item);
    });
  }

  async function refresh() {
    if (busy) return;
    busy = true;
    els.refresh.disabled = true;
    els.refresh.innerHTML = "<span aria-hidden='true'>⟳</span> CHECKING";

    const now = Date.now();
    const currentBucket = bucketFor(now);
    const buckets = Array.from({ length: 12 }, (_, index) => currentBucket - 11 + index);

    try {
      const results = await Promise.all([
        readCount(HOME_PATH),
        ...buckets.map((bucket) => readCount(pathForBucket(bucket)))
      ]);
      const total = results[0];
      const recent = results.slice(1);
      const hourTotal = recent.reduce((sum, count) => sum + count, 0);

      els.total.textContent = fmt(total);
      els.hour.textContent = fmt(hourTotal);
      els.current.textContent = fmt(recent[recent.length - 1] || 0);
      els.chartTotal.textContent = fmt(hourTotal);
      els.updated.textContent = new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });
      els.empty.hidden = true;
      renderBuckets(buckets, recent);
      drawChart(recent);
      lastData = { total, hourTotal, recent, buckets };
      setStatus(true, "Live counter connected · refreshes every 3 seconds");
    } catch (error) {
      console.error("Live counter refresh failed:", error);
      if (!lastData) {
        els.total.textContent = "—";
        els.hour.textContent = "—";
        els.current.textContent = "—";
        els.chartTotal.textContent = "—";
        els.empty.hidden = false;
        els.empty.textContent = "Counter unavailable. Check your connection, then refresh.";
      }
      setStatus(false, "Counter service unreachable · retrying automatically");
    } finally {
      busy = false;
      els.refresh.disabled = false;
      els.refresh.innerHTML = "<span aria-hidden='true'>⟳</span> REFRESH";
    }
  }

  els.refresh.addEventListener("click", refresh);
  window.addEventListener("resize", () => {
    if (lastData) drawChart(lastData.recent);
  });

  refresh();
  window.setInterval(refresh, REFRESH_MS);
})();
