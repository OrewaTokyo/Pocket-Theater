// Knows who is signed in and whether the phone has opened the admin website on this browser.
// While the phone is pairing this browser, it shows a QR code for the admin to scan with the phone.
PT.session = (function () {
  let wasAdmin = false;
  let overlay = null;
  let image = null;
  let codeText = null;
  let timeText = null;
  let countdown = null;
  let shown = null;
  let dismissed = null;
  let expiresAt = 0;

  function build() {
    const el = PT.util.el;
    image = el("img", { alt: "QR code to open the admin website", width: "260", height: "260" });
    codeText = el("p", { class: "pair-code" });
    timeText = el("p", { class: "note" });
    const close = el("button", { class: "ghost", type: "button", text: "Hide" });
    close.addEventListener("click", () => { dismissed = shown; hide(); });
    overlay = el("div", { class: "pair", role: "dialog", "aria-label": "Open the admin website" }, [
      el("div", { class: "pair-card" }, [
        el("h2", { text: "Open the admin website" }),
        el("p", { text: "On the phone, open Pocket Theater and scan this QR code. Then enter the admin password on the phone." }),
        image,
        codeText,
        el("p", { class: "note", text: "No camera? On the phone choose Type code and enter the code above." }),
        timeText,
        close,
      ]),
    ]);
    overlay.hidden = true;
    document.body.append(overlay);
  }

  function tick() {
    const left = Math.max(0, Math.round((expiresAt - Date.now()) / 1000));
    timeText.textContent = "Runs out in " + Math.floor(left / 60) + ":" + String(left % 60).padStart(2, "0");
    if (left === 0) hide();
  }

  function show(pair) {
    if (!overlay) build();
    if (dismissed === pair.code) return;
    if (shown !== pair.code) {
      shown = pair.code;
      image.src = "/api/pair/qr?c=" + encodeURIComponent(pair.code);
      codeText.textContent = pair.code.slice(0, 4) + " " + pair.code.slice(4);
    }
    expiresAt = Date.now() + pair.expires_in * 1000;
    overlay.hidden = false;
    tick();
    if (!countdown) countdown = setInterval(tick, 1000);
  }

  function hide() {
    if (overlay) overlay.hidden = true;
    shown = null;
    clearInterval(countdown);
    countdown = null;
  }

  return {
    // Called with the answer of /api/me, every few seconds.
    update(me) {
      PT.me = me;
      if (me.pair) show(me.pair);
      else { hide(); dismissed = null; }

      if (me.admin && !wasAdmin) {
        wasAdmin = true;
        hide();
        if (location.hash === "#/admin") PT.route(); else location.hash = "#/admin";
      } else if (!me.admin && wasAdmin) {
        wasAdmin = false;
        if (location.hash.indexOf("#/admin") === 0) location.hash = "#/"; else PT.route();
      }
    },
  };
})();
