PT.pages = PT.pages || {};

// Shows shared folders grouped by shelf. Pass a category id to show only that shelf.
PT.pages.home = async function (main, onlyCategory) {
  const el = PT.util.el;
  const my = ++PT.renderId;
  main.replaceChildren(el("p", { class: "note", text: "Loading\u2026" }));

  let data;
  try {
    data = await PT.api.libraries();
  } catch (err) {
    if (my === PT.renderId) main.replaceChildren(el("p", { class: "error", text: err.message }));
    return;
  }
  if (my !== PT.renderId) return;

  // The phone only lists folders that hold something, so these are exactly the shelves worth showing.
  PT.shelves = new Set(data.libraries.map((l) => l.category));
  PT.nav.render(onlyCategory || "Home");

  const countOf = (id) => data.libraries.filter((l) => l.category === id).length;
  const cats = onlyCategory ? [onlyCategory] : PT.categories;
  const out = [];

  if (onlyCategory) {
    const c = PT.util.cat(onlyCategory);
    out.push(el("h1", { text: c.icon + " " + c.label }));
    if (c.blurb) out.push(el("p", { class: "note", text: c.blurb }));
  } else {
    out.push(el("h1", { text: "Now showing" }));
    const tiles = PT.cats
      .filter((c) => countOf(c.id) > 0)
      .map((c) => {
        const n = countOf(c.id);
        return el("a", { class: "tile", href: "#/cat/" + PT.util.enc(c.id), style: "--tile:" + c.accent }, [
          el("span", { class: "tile-icon", text: c.icon, "aria-hidden": "true" }),
          el("span", { class: "tile-name", text: c.label }),
          el("span", { class: "tile-count", text: n + (n === 1 ? " folder" : " folders") }),
        ]);
      });
    if (tiles.length) out.push(el("div", { class: "tiles" }, tiles));
  }

  let shown = 0;
  cats.forEach((id) => {
    const libs = data.libraries.filter((l) => l.category === id);
    if (!libs.length) return;
    shown += libs.length;
    const c = PT.util.cat(id);
    if (!onlyCategory) out.push(el("h2", { text: c.icon + " " + c.label, style: "--tile:" + c.accent }));
    out.push(
      el("div", { class: "shelf", style: "--tile:" + c.accent }, libs.map((l) =>
        el("a", { class: "screen", href: "#/lib/" + PT.util.enc(l.id) }, [
          el("span", { class: "screen-name", text: l.name }),
          el("small", { class: "screen-count", text: l.items + (l.items === 1 ? " item" : " items") }),
        ])
      ))
    );
  });

  if (!shown) {
    out.push(el("p", {
      class: "note",
      text: onlyCategory
        ? "Nothing in " + PT.util.cat(onlyCategory).label + " right now."
        : "Nothing is shared yet. Check back later.",
    }));
    if (onlyCategory) out.push(el("a", { href: "#/", text: "Back to home" }));
  }
  main.replaceChildren(...out);
};
