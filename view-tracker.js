(() => {
  "use strict";

  // Counts tracked visits to the AUTO homepage. The service deduplicates a
  // visitor for 30 minutes on the same path. Five-minute bucket paths power
  // the counter dashboard's rolling 60-minute estimate.
  const API = "https://page-views-api.ratneshc.com/api/v1/track";
  const SITE = "vedtrader67-commits.github.io";
  const HOME_PATH = "/auto/";
  const BUCKET_MS = 5 * 60 * 1000;

  const currentPath = window.location.pathname.replace(/\/+$/, "") || "/";
  const normalizedHome = HOME_PATH.replace(/\/+$/, "") || "/";
  if (currentPath !== normalizedHome) return;

  function record(path) {
    const query = new URLSearchParams({ site: SITE, path });
    fetch(`${API}?${query.toString()}`, {
      method: "GET",
      cache: "no-store",
      credentials: "omit",
      keepalive: true
    }).catch(() => {
      // Tracking failure must never interfere with loading the main website.
    });
  }

  const bucket = Math.floor(Date.now() / BUCKET_MS);
  record(HOME_PATH);
  record(`/auto/live-hour/${bucket}`);
})();
