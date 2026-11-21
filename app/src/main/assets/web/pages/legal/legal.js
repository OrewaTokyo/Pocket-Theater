// Shows the Terms of use or the Privacy policy. Anyone can read these, signed in or not.
(function () {
  const el = PT.util.el;
  const docs = {
    terms: { file: "/legal/terms.md", title: "Terms of use" },
    privacy: { file: "/legal/privacy.md", title: "Privacy policy" },
  };
  const asked = new URLSearchParams(location.search).get("doc");
  const key = docs[asked] ? asked : "terms";
  const body = document.getElementById("legal-body");

  document.title = docs[key].title + " - Pocket Theater";
  document.querySelectorAll("[data-doc]").forEach((link) => {
    if (link.dataset.doc === key) link.setAttribute("aria-current", "page");
  });
  document.getElementById("back").addEventListener("click", (event) => {
    event.preventDefault();
    if (document.referrer && history.length > 1) history.back();
    else location.href = "/";
  });

  fetch(docs[key].file)
    .then((res) => { if (!res.ok) throw new Error("missing"); return res.text(); })
    .then((text) => body.replaceChildren(PT.markdown.render(text)))
    .catch(() => body.replaceChildren(el("p", { class: "error", text: "This page could not load. Check that you are still connected to the phone." })));
})();
