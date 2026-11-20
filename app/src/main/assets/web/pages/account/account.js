PT.pages = PT.pages || {};

// Who is signed in, change password, sign out.
PT.pages.account = function (main) {
  const el = PT.util.el;
  PT.renderId++;
  PT.nav.render("");

  const msg = el("p", { class: "form-msg", role: "alert" });
  const field = (label, id, autocomplete) => [
    el("label", { for: id, text: label }),
    el("input", { id, type: "password", autocomplete }),
  ];
  const form = el("form", { class: "account-form", novalidate: "" }, [
    ...field("Current password", "ac-current", "current-password"),
    ...field("New password", "ac-next", "new-password"),
    ...field("Repeat new password", "ac-next2", "new-password"),
    msg,
    el("button", { class: "go", type: "submit", text: "Change password" }),
  ]);

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    msg.className = "form-msg";
    msg.textContent = "";
    const current = document.getElementById("ac-current").value;
    const next = document.getElementById("ac-next").value;
    const problem = PT.util.passwordProblem(next) ||
      (next !== document.getElementById("ac-next2").value ? "The two new passwords are not the same." : null);
    if (problem) { msg.textContent = problem; return; }
    try {
      await PT.api.post("/api/password", { current, next });
      form.reset();
      msg.className = "form-msg ok";
      msg.textContent = "Password changed.";
    } catch (err) {
      msg.textContent = err.message;
    }
  });

  const out = el("button", { class: "ghost", type: "button", text: "Sign out" });
  out.addEventListener("click", async () => {
    try { await PT.api.post("/api/logout", {}); } catch (err) { /* leaving anyway */ }
    location.href = "/login.html";
  });

  main.replaceChildren(
    el("h1", { text: "Account" }),
    el("p", { class: "note", id: "ac-who", text: "Loading\u2026" }),
    el("h2", { text: "Change password" }),
    form,
    el("h2", { text: "Leaving?" }),
    out
  );
  PT.api.me().then((me) => { document.getElementById("ac-who").textContent = "Signed in as " + me.username + "."; }).catch(() => {});
};
