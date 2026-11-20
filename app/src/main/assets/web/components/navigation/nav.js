PT.nav = {
  // Marks the active section and tints the page with that shelf's colour.
  render(active) {
    const el = PT.util.el;
    const nav = document.getElementById("nav");
    const links = [["Home", "\u{1F3AD} Home", "#/"]].concat(
      PT.cats.map((c) => [c.id, c.icon + " " + c.label, "#/cat/" + PT.util.enc(c.id)])
    );
    nav.replaceChildren(
      ...links.map(([id, label, href]) => {
        const a = el("a", { href, text: label });
        if (id === active) a.setAttribute("aria-current", "page");
        return a;
      })
    );
    const known = PT.cats.some((c) => c.id === active);
    document.body.style.setProperty("--accent", known ? PT.util.cat(active).accent : "var(--marquee)");
  },
};
