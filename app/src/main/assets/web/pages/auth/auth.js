// The sign in and request access screen. Checks run here for a quick answer and again on the phone for safety.
(function () {
  const $ = (id) => document.getElementById(id);
  const forms = { signin: $("form-signin"), register: $("form-register") };
  const tabs = { signin: $("tab-signin"), register: $("tab-register") };

  function show(which) {
    Object.keys(forms).forEach((key) => {
      forms[key].hidden = key !== which;
      tabs[key].setAttribute("aria-selected", key === which ? "true" : "false");
    });
    $("sent").hidden = true;
    $("auth-title").textContent = which === "signin" ? "Welcome back" : "Join the theater";
    $(which === "signin" ? "si-user" : "rg-user").focus();
  }
  tabs.signin.addEventListener("click", () => show("signin"));
  tabs.register.addEventListener("click", () => show("register"));
  $("to-signin").addEventListener("click", () => show("signin"));

  document.querySelectorAll(".show-pass").forEach((box) => {
    box.addEventListener("change", () => {
      box.dataset.for.split(" ").forEach((id) => { $(id).type = box.checked ? "text" : "password"; });
    });
  });

  function busy(form, on) {
    const button = form.querySelector("button.go");
    button.disabled = on;
    button.dataset.label = button.dataset.label || button.textContent;
    button.textContent = on ? "One moment\u2026" : button.dataset.label;
  }

  forms.signin.addEventListener("submit", async (event) => {
    event.preventDefault();
    const msg = $("si-msg");
    msg.textContent = "";
    const username = $("si-user").value.trim().toLowerCase();
    const password = $("si-pass").value;
    if (!username || !password) { msg.textContent = "Enter your username and password."; return; }
    busy(forms.signin, true);
    try {
      await PT.api.post("/api/login", { username, password });
      location.href = "/";
    } catch (err) {
      msg.textContent = err.message;
      busy(forms.signin, false);
    }
  });

  forms.register.addEventListener("submit", async (event) => {
    event.preventDefault();
    const msg = $("rg-msg");
    msg.textContent = "";
    const username = $("rg-user").value.trim().toLowerCase();
    const password = $("rg-pass").value;
    const problem = PT.util.usernameProblem(username) || PT.util.passwordProblem(password) ||
      (password !== $("rg-pass2").value ? "The two passwords are not the same." : null);
    if (problem) { msg.textContent = problem; return; }
    busy(forms.register, true);
    try {
      await PT.api.post("/api/register", { username, password });
      forms.register.reset();
      Object.values(forms).forEach((f) => { f.hidden = true; });
      $("sent").hidden = false;
      $("auth-title").textContent = "Almost there";
    } catch (err) {
      msg.textContent = err.message;
    }
    busy(forms.register, false);
  });

  if (location.hash === "#register") show("register");
})();
