PT.nav = {
  // Marks the active section and tints the page with that shelf's colour.
  render(active) {
    const el = PT.util.el;
    this.active = active;
    const nav = document.getElementById("nav");
    // A shelf shows up here only once something in it is shared. Until the list is known, every shelf shows.
    const shelves = PT.cats.filter((c) => !PT.shelves || PT.shelves.has(c.id));
    const links = [["Home", "\u{1F3AD} Home", "#/"]].concat(
      shelves.map((c) => [c.id, c.icon + " " + c.label, "#/cat/" + PT.util.enc(c.id)])
    );
    if (PT.me && PT.me.admin) links.push(["Admin", "\u{1F6E0} Admin", "#/admin"]);
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

  // Draws the bar again, for example after the list of shelves became known.
  refresh() {
    this.render(this.active || "Home");
  },
};
