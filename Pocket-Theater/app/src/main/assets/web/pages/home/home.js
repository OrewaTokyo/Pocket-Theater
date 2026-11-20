PT.pages = PT.pages || {};

// Shows shared folders grouped by category. Pass a category to show only that shelf.
PT.pages.home = async function (main, onlyCategory) {
  const el = PT.util.el;
  const my = ++PT.renderId;
  main.replaceChildren(el("p", { class: "note", text: "Loading…" }));

  let data;
  try {
    data = await PT.api.libraries();
  } catch (err) {
    if (my === PT.renderId) main.replaceChildren(el("p", { class: "error", text: err.message }));
    return;
  }
  if (my !== PT.renderId) return;

  const cats = onlyCategory ? [onlyCategory] : PT.categories;
  const out = [el("h1", { text: onlyCategory || "Now showing" })];
  let shown = 0;

  cats.forEach((cat) => {
    const libs = data.libraries.filter((l) => l.category === cat);
    if (!libs.length) return;
    shown += libs.length;
    if (!onlyCategory) out.push(el("h2", { text: cat }));
    out.push(
      el("div", { class: "shelf" }, libs.map((l) =>
        el("a", { class: "screen", href: "#/lib/" + PT.util.enc(l.id), text: l.name })
      ))
    );
  });

  if (!shown) {
    out.push(el("p", {
      class: "note",
      text: onlyCategory
        ? "Nothing shared under " + onlyCategory + " yet. Add a folder in the phone app and pick this category."
        : "No shared folders yet. Open Pocket Theater on the phone, tap Add folder, and choose what is inside.",
    }));
  }
  main.replaceChildren(...out);
};
