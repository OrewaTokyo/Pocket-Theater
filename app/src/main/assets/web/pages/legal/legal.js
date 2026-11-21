// Shows Terms of Use, Privacy Policy or Security Policy. These pages are open to everyone, no sign-in needed.
(function () {
  const docs = { terms: "Terms of Use", privacy: "Privacy Policy", security: "Security Policy" };
  const box = document.getElementById("doc");

  async function show() {
    const key = docs[location.hash.slice(1)] ? location.hash.slice(1) : "terms";
    document.querySelectorAll(".legal-tabs a").forEach((a) => {
      if (a.getAttribute("href") === "#" + key) a.setAttribute("aria-current", "page");
      else a.removeAttribute("aria-current");
    });
    document.title = docs[key] + " - Pocket Theater";
    box.replaceChildren(PT.util.el("p", { class: "note", text: "Loading\u2026" }));
    try {
      const res = await fetch("/legal/" + key + ".md", { cache: "no-store" });
      if (!res.ok) throw new Error("not found");
      box.replaceChildren(...PT.md.render(PT.md.parse(await res.text())));
    } catch (err) {
      box.replaceChildren(PT.util.el("p", { class: "error", text: "Could not load this page. Check that the phone is still reachable." }));
    }
    window.scrollTo(0, 0);
  }

  window.addEventListener("hashchange", show);
  show();
})();
