// Offers the Windows app to people who open the website on a Windows PC.
// A small card appears a few seconds after the site opens. "Not now" hides it for 30 days and "Don't show
// again" for good. The choice is kept in this browser only (never on the phone), and phones, tablets and
// other computers never see the card.
PT.windowsApp = (function () {
  const RELEASES = "https://github.com/OrewaTokyo/Pocket-Theater-Windows/releases";
  const KEY = "pt.windowsApp";
  const LATER_DAYS = 30;
  const DAY_MS = 24 * 60 * 60 * 1000;
  let started = false;

  // True for a Windows PC. A phone or tablet, even one that mentions Windows, does not count.
  // `hints` is navigator.userAgentData where the browser has it.
  function isWindowsPC(ua, hints) {
    const text = String(ua || "");
    if (hints && typeof hints.platform === "string" && hints.platform !== "") return hints.platform === "Windows" && !hints.mobile;
    if (/Windows Phone|IEMobile|Mobile/i.test(text)) return false;
    return /Windows NT/.test(text);
  }

  // What the person chose last time: null, { until: time } or { never: true }.
  function readChoice(storage) {
    try {
      const value = JSON.parse(storage.getItem(KEY) || "null");
      return value && typeof value === "object" ? value : null;
    } catch (err) {
      return null; // storage is off or holds something unreadable
    }
  }

  function writeChoice(storage, choice) {
    try {
      storage.setItem(KEY, JSON.stringify(choice));
      return true;
    } catch (err) {
      return false; // the card then simply shows again next visit
    }
  }

  // The answer to remember for each button.
  function choiceFor(button, now) {
    if (button === "later") return { until: now + LATER_DAYS * DAY_MS };
    return { never: true }; // "never" and "downloaded"
  }

  function shouldOffer(ua, hints, choice, now) {
    if (!isWindowsPC(ua, hints)) return false;
    if (!choice) return true;
    if (choice.never) return false;
    return typeof choice.until === "number" ? now >= choice.until : true;
  }

  // The link to the downloads. It opens in a new tab and gives that page no way back to this one.
  function link() {
    return { href: RELEASES, target: "_blank", rel: "noopener noreferrer" };
  }

  function anchor(text, className) {
    const a = document.createElement("a");
    const attrs = link();
    a.href = attrs.href;
    a.target = attrs.target;
    a.rel = attrs.rel;
    a.className = className || "";
    a.textContent = text;
    return a;
  }

  function button(text, className, onClick) {
    const b = document.createElement("button");
    b.type = "button";
    b.className = className;
    b.textContent = text;
    b.addEventListener("click", onClick);
    return b;
  }

  function show() {
    if (document.querySelector(".win-offer")) return;
    const box = document.createElement("section");
    box.className = "win-offer";
    box.setAttribute("role", "dialog");
    box.setAttribute("aria-labelledby", "win-offer-title");

    const title = document.createElement("h2");
    title.id = "win-offer-title";
    title.textContent = "Get the Windows app";
    const text = document.createElement("p");
    text.textContent = "Watch from your PC with no browser warnings. It stays signed in, plays more kinds of video in VLC or another player, and saves files easily.";

    const close = (which) => {
      writeChoice(localStorage, choiceFor(which, Date.now()));
      box.remove();
    };
    const download = anchor("Download for Windows", "win-offer-go");
    download.addEventListener("click", () => setTimeout(() => close("downloaded"), 0)); // let the new tab open first
    const row = document.createElement("div");
    row.className = "win-offer-row";
    row.append(download, button("Not now", "win-offer-ghost", () => close("later")), button("Don\u2019t show again", "win-offer-link", () => close("never")));

    box.append(title, text, row);
    box.addEventListener("keydown", (event) => { if (event.key === "Escape") close("later"); });
    document.body.append(box); // last in the page, so Tab reaches it after everything else and focus is never taken away
  }

  // Also leaves a quiet link in the footer, for anyone who closed the card.
  function addFooterLink() {
    const foot = document.querySelector(".site-foot");
    if (!foot || foot.querySelector(".win-foot")) return;
    foot.append(document.createTextNode(" \u00B7 "), anchor("Windows app", "win-foot"));
  }

  function start() {
    if (started || typeof document === "undefined") return;
    started = true;
    if (!isWindowsPC(navigator.userAgent, navigator.userAgentData)) return;
    addFooterLink();
    let choice = null;
    try { choice = readChoice(localStorage); } catch (err) { /* storage is blocked */ }
    if (!shouldOffer(navigator.userAgent, navigator.userAgentData, choice, Date.now())) return;
    // Wait a moment, and never cover a video that is playing.
    setTimeout(function wait() {
      const viewer = document.getElementById("viewer");
      if (viewer && !viewer.hidden) { setTimeout(wait, 4000); return; }
      show();
    }, 3000);
  }

  const api = { RELEASES, KEY, LATER_DAYS, isWindowsPC, readChoice, writeChoice, choiceFor, shouldOffer, link, start };
  if (typeof document !== "undefined") start();
  return api;
})();
