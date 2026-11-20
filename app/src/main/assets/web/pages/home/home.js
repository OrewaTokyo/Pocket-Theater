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
      .filter((c) => c.main || countOf(c.id) > 0)
      .map((c) => {
        const n = countOf(c.id);
        return el("a", { class: "tile", href: "#/cat/" + PT.util.enc(c.id), style: "--tile:" + c.accent }, [
          el("span", { class: "tile-icon", text: c.icon, "aria-hidden": "true" }),
          el("span", { class: "tile-name", text: c.label }),
          el("span", { class: "tile-count", text: n ? n + (n === 1 ? " folder" : " folders") : "Empty" }),
        ]);
      });
    out.push(el("div", { class: "tiles" }, tiles));
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
        el("a", { class: "screen", href: "#/lib/" + PT.util.enc(l.id), text: l.name })
      ))
    );
  });

  if (!shown) {
    out.push(el("p", {
      class: "note",
      text: onlyCategory
        ? "Nothing shared under " + PT.util.cat(onlyCategory).label + " yet. Add a folder in the phone app and pick this category."
        : "No shared folders yet. Open Pocket Theater on the phone, tap Add folder, and choose what is inside.",
    }));
  }
  main.replaceChildren(...out);
};
