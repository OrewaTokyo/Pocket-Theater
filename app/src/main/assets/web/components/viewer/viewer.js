PT.viewer = {
  items: [],
  index: 0,

  open(items, index) {
    this.items = items;
    this.index = index;
    this.render();
    document.getElementById("viewer").hidden = false;
    document.body.classList.add("noscroll");
  },

  // Adds the subtitle choices to a video. The player's own CC button lists them, and the last choice is remembered.
  addSubtitles(media, subs) {
    if (!subs || !subs.length) return;
    subs.forEach((s) => {
      const track = PT.util.el("track", { kind: "subtitles", label: s.label, src: s.url });
      if (s.lang) track.setAttribute("srclang", s.lang);
      media.append(track);
    });
    let saved = null;
    try { saved = JSON.parse(localStorage.getItem("pt.subs") || "null"); } catch (err) { /* nothing saved */ }
    const choose = () => {
      const tracks = Array.from(media.textTracks);
      const want = saved && !saved.off
        ? tracks.find((t) => t.label === saved.label) || tracks.find((t) => saved.lang && t.language === saved.lang)
        : null;
      tracks.forEach((t) => { t.mode = t === want ? "showing" : "disabled"; });
    };
    media.addEventListener("loadedmetadata", choose);
    media.textTracks.addEventListener("change", () => {
      const showing = Array.from(media.textTracks).find((t) => t.mode === "showing");
      saved = showing ? { label: showing.label, lang: showing.language } : { off: true };
      try { localStorage.setItem("pt.subs", JSON.stringify(saved)); } catch (err) { /* storage is off */ }
    });
  },

  // Stops any video or audio, so nothing keeps playing out of sight.
  stopMedia() {
    document.querySelectorAll("#viewer video, #viewer audio").forEach((m) => {
      try { m.pause(); m.removeAttribute("src"); m.load(); } catch (err) { /* already gone */ }
    });
  },

  // Shown instead of a black player when the browser cannot play the file at all.
  unsupported(media) {
    const item = this.items[this.index];
    const el = PT.util.el;
    media.replaceWith(el("div", { class: "unsupported" }, [
      el("p", { text: "Your browser can't play \u201C" + item.name + "\u201D. Download it and open it in a player such as VLC." }),
      el("a", { href: item.url, download: item.name, text: "Download" }),
    ]));
  },

  close() {
    this.stopMedia();
    if (this.stopGuard) this.stopGuard();
    this.stopGuard = null;
    const root = document.getElementById("viewer");
    root.replaceChildren();
    root.hidden = true;
    document.body.classList.remove("noscroll");
  },

  step(delta) {
    const next = this.index + delta;
    if (next < 0 || next >= this.items.length) return;
    this.index = next;
    this.render();
  },

  // If the connection drops while a video or image is open, load it again when it returns and carry on from the same spot.
  guard(media, url, isImage) {
    let resumeAt = 0;
    let wasPlaying = true;
    let attempts = 0;

    const reload = () => {
      if (!document.body.contains(media)) return;
      const at = resumeAt;
      const play = wasPlaying;
      media.src = url;
      if (isImage) return;
      media.load();
      media.addEventListener("loadedmetadata", () => {
        if (at > 0) media.currentTime = at;
        if (play) media.play().catch(() => {});
      }, { once: true });
    };

    if (!isImage) {
      media.addEventListener("timeupdate", () => { if (!media.paused) resumeAt = media.currentTime; });
      media.addEventListener("playing", () => { attempts = 0; });
      media.addEventListener("stalled", () => { wasPlaying = !media.paused; });
    }
    media.addEventListener("error", () => {
      wasPlaying = !isImage && !media.paused;
      const code = media.error ? media.error.code : 0;
      // 3 = could not decode, 4 = format not supported: trying again would only fail again.
      if (!isImage && (code === 3 || code === 4)) {
        this.unsupported(media);
        return;
      }
      if (attempts < 5) {
        attempts += 1;
        setTimeout(reload, 2000 * attempts);
      }
    });

    return PT.connection.onBack(() => {
      const broken = isImage ? !media.complete || media.naturalWidth === 0 : media.error || media.readyState < 3;
      if (broken) {
        attempts = 0;
        reload();
      }
    });
  },

  render() {
    this.stopMedia();
    if (this.stopGuard) this.stopGuard();
    this.stopGuard = null;
    const el = PT.util.el;
    const item = this.items[this.index];
    const stage = el("div", { class: "viewer-stage" });

    if (item.kind === "video" || item.kind === "audio") {
      const media = el(item.kind, { src: item.url, controls: "", autoplay: "" });
      media.addEventListener("ended", () => this.step(1));
      if (item.kind === "video") this.addSubtitles(media, item.subs);
      stage.append(media);
      this.stopGuard = this.guard(media, item.url, false);
    } else if (item.kind === "image") {
      const img = el("img", { src: item.url, alt: item.name });
      stage.append(img);
      this.stopGuard = this.guard(img, item.url, true);
    } else if (item.kind === "pdf") {
      stage.append(el("iframe", { src: item.url, title: item.name }));
    } else {
      stage.append(el("p", { class: "note", text: "This file type can't be shown in the browser. Use Download." }));
    }

    const prev = el("button", { type: "button", text: "Previous", onclick: () => this.step(-1) });
    const next = el("button", { type: "button", text: "Next", onclick: () => this.step(1) });
    prev.disabled = this.index === 0;
    next.disabled = this.index === this.items.length - 1;

    const bar = el("div", { class: "viewer-bar" }, [
      el("div", { class: "viewer-title", text: item.name }),
      el("span", { class: "note", text: this.index + 1 + " of " + this.items.length }),
      prev,
      next,
      el("a", { href: item.url, download: item.name, text: "Download" }),
      el("button", { type: "button", text: "Close", onclick: () => this.close() }),
    ]);

    document.getElementById("viewer").replaceChildren(bar, stage);
  },
};

document.addEventListener("keydown", (e) => {
  if (!document.getElementById("viewer").hidden && e.key === "Escape") PT.viewer.close();
});
