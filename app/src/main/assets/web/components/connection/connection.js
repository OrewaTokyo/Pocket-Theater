// Watches the link to the phone. When it drops, a banner says so, and when it returns the page picks up where it was.
PT.connection = (function () {
  let online = true;
  let failures = 0;
  let timer = null;
  let banner = null;
  let hideTimer = null;
  const listeners = [];

  function show(text, kind) {
    if (!banner) {
      banner = document.createElement("div");
      banner.className = "conn";
      banner.setAttribute("role", "status");
      document.body.append(banner);
    }
    clearTimeout(hideTimer);
    banner.className = "conn " + kind;
    banner.textContent = text;
    banner.hidden = false;
  }

  function hide() {
    if (banner) banner.hidden = true;
  }

  // Any answer from the phone counts as "still there", even a "sign in first".
  async function ping() {
    const stop = new AbortController();
    const giveUp = setTimeout(() => stop.abort(), 4000);
    try {
      const res = await fetch("/api/me", { cache: "no-store", signal: stop.signal });
      clearTimeout(giveUp);
      if (res.status === 401) location.href = "/login.html";
      return true;
    } catch (err) {
      clearTimeout(giveUp);
      return false;
    }
  }

  function schedule() {
    clearTimeout(timer);
    timer = setTimeout(check, PT.util.pollDelay(online, failures));
  }

  async function check() {
    const ok = await ping();
    if (ok) {
      failures = 0;
      if (!online) {
        online = true;
        show("Back online", "ok");
        hideTimer = setTimeout(hide, 2500);
        listeners.slice().forEach((fn) => fn());
      }
    } else {
      failures += 1;
      // Two misses in a row, so a single hiccup does not flash a warning.
      if (online && failures >= 2) {
        online = false;
        show("Lost the connection to the phone. Reconnecting\u2026", "bad");
      }
    }
    schedule();
  }

  window.addEventListener("online", check);
  window.addEventListener("offline", check);
  document.addEventListener("visibilitychange", () => { if (!document.hidden) check(); });

  return {
    start: schedule,
    isOnline: () => online,
    // Runs `fn` each time the connection comes back. Returns a function that stops listening.
    onBack(fn) {
      listeners.push(fn);
      return () => {
        const at = listeners.indexOf(fn);
        if (at >= 0) listeners.splice(at, 1);
      };
    },
  };
})();
