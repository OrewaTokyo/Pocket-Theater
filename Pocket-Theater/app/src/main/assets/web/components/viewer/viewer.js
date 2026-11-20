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

  close() {
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

  render() {
    const el = PT.util.el;
    const item = this.items[this.index];
    const stage = el("div", { class: "viewer-stage" });

    if (item.kind === "video" || item.kind === "audio") {
      const media = el(item.kind, { src: item.url, controls: "", autoplay: "" });
      media.addEventListener("ended", () => this.step(1));
      stage.append(media);
    } else if (item.kind === "image") {
      stage.append(el("img", { src: item.url, alt: item.name }));
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
