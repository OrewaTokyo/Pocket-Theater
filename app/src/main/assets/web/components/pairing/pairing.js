// Knows who is signed in and whether the phone has opened the admin website on this browser.
// "Admin sign-in" in the top bar shows a QR code here. The admin scans it with the scanner built into the phone app and
// types the admin password on the phone. This page notices that and opens the admin website by itself.
PT.session = (function () {
  const FAST_POLL_MS = 1500; // while a code is on the screen, ask the phone often so the admin page opens right away
  let wasAdmin = false;
  let wasReady = null;
  let overlay = null;
  let image = null;
  let codeText = null;
  let statusText = null;
  let timeText = null;
  let countdown = null;
  let poller = null;
  let shown = null;
  let expiresAt = 0;
  let startedAt = 0;

  function build() {
    const el = PT.util.el;
    image = el("img", { alt: "QR code to open the admin website", width: "260", height: "260" });
    codeText = el("p", { class: "pair-code" });
    statusText = el("p", { class: "pair-status", role: "status", "aria-live": "polite" });
    timeText = el("p", { class: "note" });
    const close = el("button", { class: "ghost", type: "button", text: "Hide" });
    close.addEventListener("click", cancel);
    overlay = el("div", { class: "pair", role: "dialog", "aria-label": "Open the admin website" }, [
      el("div", { class: "pair-card" }, [
        el("h2", { text: "Open the admin website" }),
        el("ol", { class: "pair-steps" }, [
          el("li", { text: "Open Pocket Theater on the phone." }),
          el("li", { text: "On Profile, tap Scan admin QR and point the camera at this code." }),
          el("li", { text: "Type the admin password on the phone. This page opens by itself." }),
        ]),
        image,
        codeText,
        el("p", { class: "note", text: "Camera not working? On the phone choose Type code and enter the code above." }),
        statusText,
        timeText,
        close,
      ]),
    ]);
    overlay.hidden = true;
    document.body.append(overlay);
  }

  function setStatus(text, kind) {
    statusText.textContent = text;
    statusText.className = "pair-status" + (kind ? " " + kind : "");
  }

  function tick() {
    const left = Math.max(0, Math.round((expiresAt - Date.now()) / 1000));
    timeText.textContent = "Runs out in " + Math.floor(left / 60) + ":" + String(left % 60).padStart(2, "0");
    if (left === 0) hide();
  }

  // Asks the phone how things stand, faster than the usual check.
  async function poll() {
    try {
      const res = await fetch("/api/me", { cache: "no-store" });
      if (res.status === 401) { location.href = "/login.html"; return; }
      if (res.ok) update(await res.json());
    } catch (err) { /* the connection banner deals with a lost link */ }
  }

  function show(pair) {
    if (!overlay) build();
    if (shown !== pair.code) {
      shown = pair.code;
      image.hidden = false;
      image.src = "/api/pair/qr?c=" + encodeURIComponent(pair.code);
      codeText.textContent = pair.code.slice(0, 4) + " " + pair.code.slice(4);
    }
    if (pair.scanned) setStatus("Scanned. Now type the admin password on the phone.", "ok");
    else setStatus("Waiting for the phone to scan this code\u2026", "");
    expiresAt = Date.now() + pair.expires_in * 1000;
    overlay.hidden = false;
    tick();
    if (!countdown) countdown = setInterval(tick, 1000);
    if (!poller) poller = setInterval(poll, FAST_POLL_MS);
  }

  // Something stopped the sign-in before a code could be shown, for example the admin website is turned off.
  function showProblem(message) {
    if (!overlay) build();
    shown = null;
    image.hidden = true;
    codeText.textContent = "";
    timeText.textContent = "";
    setStatus(message, "bad");
    overlay.hidden = false;
  }

  function hide() {
    if (overlay) overlay.hidden = true;
    shown = null;
    clearInterval(countdown);
    clearInterval(poller);
    countdown = null;
    poller = null;
  }

  // The Hide button: the code stops working at once, so a QR code nobody needs is never left lying around.
  function cancel() {
    hide();
    PT.api.post("/api/pair/cancel", {}).catch(() => {});
  }

  // The "Admin sign-in" button asks the phone for a QR code.
  async function start() {
    startedAt = Date.now();
    try {
      show(await PT.api.post("/api/pair/start", {}));
    } catch (err) {
      showProblem(err.message);
    }
  }

  // Called with the answer of /api/me, every few seconds (and every second or so while a code is shown).
  function update(me) {
    PT.me = me;
    if (me.pair) show(me.pair);
    // The code ran out, or the phone ended it. (Answers that were already on their way when the button was tapped are ignored.)
    else if (overlay && !overlay.hidden && image && !image.hidden && Date.now() - startedAt > 4000) hide();

    // Show or take away the "Admin sign-in" button when the phone starts or stops allowing it.
    if (wasReady !== null && wasReady !== !!me.admin_ready && PT.nav && PT.nav.refresh) PT.nav.refresh();
    wasReady = !!me.admin_ready;

    if (me.admin && !wasAdmin) {
      wasAdmin = true;
      hide();
      if (location.hash === "#/admin") PT.route(); else location.hash = "#/admin";
    } else if (!me.admin && wasAdmin) {
      wasAdmin = false;
      if (location.hash.indexOf("#/admin") === 0) location.hash = "#/"; else PT.route();
    }
  }

  return { update, start };
})();
