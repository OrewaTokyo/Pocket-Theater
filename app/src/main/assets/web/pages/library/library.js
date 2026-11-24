PT.pages = PT.pages || {};

PT.pages.library = async function (main, libId, parts) {
  const el = PT.util.el;
  const enc = PT.util.enc;
  const my = ++PT.renderId;
  main.replaceChildren(el("p", { class: "note", text: "Loading…" }));

  let data;
  try {
    data = await PT.api.list(libId, parts);
  } catch (err) {
    if (my === PT.renderId) main.replaceChildren(el("p", { class: "error", text: err.message }));
    return;
  }
  if (my !== PT.renderId) return;
  PT.nav.render(data.library.category);

  const base = "#/lib/" + enc(libId);
  const crumbs = el("div", { class: "crumbs" }, [el("a", { href: base, text: data.library.name })]);
  let acc = base;
  parts.forEach((part, i) => {
    acc += "/" + enc(part);
    crumbs.append(el("span", { text: "/" }));
    crumbs.append(i === parts.length - 1 ? el("span", { text: part }) : el("a", { href: acc, text: part }));
  });

  const sorted = data.entries.slice().sort((a, b) =>
    a.dir !== b.dir ? (a.dir ? -1 : 1) : a.name.localeCompare(b.name, undefined, { numeric: true, sensitivity: "base" })
  );
  const files = sorted
    .filter((e) => !e.dir)
    .map((e) => ({
      name: e.name,
      kind: PT.util.kind(e),
      url: PT.api.fileUrl(libId, parts.concat(e.name)),
      // Subtitle files found next to the video. The phone turns them into WebVTT for the player.
      subs: (e.subs || []).map((s) => ({ label: s.label, lang: s.lang, url: PT.api.subtitleUrl(libId, s.path.split("/")) })),
    }));

  const tickets = sorted.map((entry) => {
    const kind = PT.util.kind(entry);
    const body = el("span", { class: "ticket-body" }, [
      el("span", { class: "ticket-name", text: entry.name }),
      el("span", { class: "ticket-meta", text: entry.dir ? "Folder" : PT.util.size(entry.size) }),
    ]);
    const stub = el("span", { class: "stub", text: PT.util.icon(kind), "aria-hidden": "true" });
    if (entry.dir) {
      return el("a", { class: "ticket", href: acc + "/" + enc(entry.name), "data-name": entry.name.toLowerCase() }, [stub, body]);
    }
    const index = files.findIndex((f) => f.name === entry.name);
    return el("button", {
      class: "ticket", type: "button", "data-name": entry.name.toLowerCase(),
      onclick: () => PT.viewer.open(files, index),
    }, [stub, body]);
  });

  const filter = el("input", { class: "filter", type: "search", placeholder: "Filter this folder", "aria-label": "Filter this folder" });
  filter.addEventListener("input", () => {
    const q = filter.value.trim().toLowerCase();
    tickets.forEach((t) => { t.hidden = q !== "" && !t.dataset.name.includes(q); });
  });

  main.replaceChildren(
    crumbs,
    el("h1", { text: parts.length ? parts[parts.length - 1] : data.library.name }),
    ...(tickets.length
      ? [filter, el("div", { class: "tickets" }, tickets)]
      : [el("p", { class: "note", text: "This folder is empty." })])
  );
};
