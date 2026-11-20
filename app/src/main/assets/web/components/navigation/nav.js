PT.nav = {
  render(active) {
    const el = PT.util.el;
    const nav = document.getElementById("nav");
    const links = [["Home", "#/"]].concat(PT.categories.map((c) => [c, "#/cat/" + PT.util.enc(c)]));
    nav.replaceChildren(
      ...links.map(([label, href]) => {
        const a = el("a", { href, text: label });
        if (label === active) a.setAttribute("aria-current", "page");
        return a;
      })
    );
  },
};
