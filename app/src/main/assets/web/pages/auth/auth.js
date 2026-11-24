// The sign in and request access screen. Checks run here for a quick answer and again on the phone for safety.
(function () {
  const $ = (id) => document.getElementById(id);
  const forms = { signin: $("form-signin"), register: $("form-register") };
  const tabs = { signin: $("tab-signin"), register: $("tab-register") };
  const tabBar = $("tabs");
  const titles = { signin: "Welcome back", register: "Join the theater" };

  // Turns a failed request into a sentence a person can act on. A network failure has no message from the phone.
  function explain(err) {
    if (err instanceof TypeError) return "Can't reach the phone. Check that the theater is open on it and that you are on the same Wi-Fi.";
    return err.message || "Something went wrong. Try again.";
  }

  function hidePasswords() {
    document.querySelectorAll(".show-pass").forEach((box) => {
      box.checked = false;
      box.dataset.for.split(" ").forEach((id) => { $(id).type = "password"; });
    });
  }

  function show(which, focus) {
    Object.keys(forms).forEach((key) => {
      forms[key].hidden = key !== which;
      tabs[key].setAttribute("aria-selected", key === which ? "true" : "false");
    });
    tabBar.hidden = false;
    tabBar.dataset.active = which;
    $("sent").hidden = true;
    $("auth-title").textContent = titles[which];
    $("si-msg").textContent = "";
    $("rg-msg").textContent = "";
    hidePasswords();
    try { history.replaceState(null, "", which === "register" ? "#register" : location.pathname + location.search); } catch (e) { /* not important */ }
    if (focus !== false) $(which === "signin" ? "si-user" : "rg-user").focus();
  }
  tabs.signin.addEventListener("click", () => show("signin"));
  tabs.register.addEventListener("click", () => show("register"));
  $("to-signin").addEventListener("click", () => show("signin"));

  document.querySelectorAll(".show-pass").forEach((box) => {
    box.addEventListener("change", () => {
      box.dataset.for.split(" ").forEach((id) => { $(id).type = box.checked ? "text" : "password"; });
    });
  });

  // A quiet warning when Caps Lock is on while typing a password.
  document.querySelectorAll(".pw").forEach((input) => {
    const note = input.closest("form").querySelector(".caps");
    const check = (event) => { if (event.getModifierState) note.hidden = !event.getModifierState("CapsLock"); };
    input.addEventListener("keydown", check);
    input.addEventListener("keyup", check);
    input.addEventListener("blur", () => { note.hidden = true; });
  });

  function busy(form, on) {
    const button = form.querySelector("button.go");
    button.disabled = on;
    button.classList.toggle("busy", on);
    button.dataset.label = button.dataset.label || button.textContent;
    button.textContent = on ? "One moment\u2026" : button.dataset.label;
  }

  function shake(form) {
    form.classList.remove("shake");
    void form.offsetWidth; // lets the animation start again
    form.classList.add("shake");
  }

  // ---- live hints while requesting access ----
  const LABELS = ["Too short", "Weak", "Okay", "Good", "Strong"];

  function strength(password) {
    if (password.length < 8 || PT.util.passwordProblem(password, "")) return password.length < 8 ? 0 : 1;
    let score = 1;
    if (password.length >= 12) score++;
    if (/[a-z]/.test(password) && /[A-Z]/.test(password)) score++;
    if (/\d/.test(password) && /[^A-Za-z0-9]/.test(password)) score++;
    return Math.min(score, 4);
  }

  function setHint(el, text, state) {
    el.textContent = text;
    el.className = "hint" + (state ? " " + state : "");
  }

  function liveUser() {
    const name = $("rg-user").value.trim().toLowerCase();
    if (!name) return setHint($("rg-user-hint"), "3 to 20 letters, numbers or _", "");
    const problem = PT.util.usernameProblem(name);
    if (problem) setHint($("rg-user-hint"), "3 to 20 letters, numbers or _", "bad");
    else setHint($("rg-user-hint"), "Looks good", "ok");
  }

  function livePass() {
    const value = $("rg-pass").value;
    const meter = $("rg-meter");
    if (!value) {
      meter.className = "";
      setHint($("rg-pass-hint"), "At least 8 characters", "");
    } else {
      const level = strength(value);
      meter.className = "l" + Math.max(1, level);
      setHint($("rg-pass-hint"), LABELS[level], level >= 2 ? "ok" : "bad");
    }
    liveMatch();
  }

  function liveMatch() {
    const again = $("rg-pass2").value;
    if (!again) return setHint($("rg-match-hint"), "", "");
    if (again === $("rg-pass").value) setHint($("rg-match-hint"), "Passwords match", "ok");
    else setHint($("rg-match-hint"), "Passwords do not match yet", "bad");
  }

  function resetLive() {
    $("rg-meter").className = "";
    liveUser();
    livePass();
    liveMatch();
  }
  $("rg-user").addEventListener("input", liveUser);
  $("rg-pass").addEventListener("input", livePass);
  $("rg-pass2").addEventListener("input", liveMatch);

  // ---- sign in ----
  forms.signin.addEventListener("submit", async (event) => {
    event.preventDefault();
    const msg = $("si-msg");
    msg.textContent = "";
    const username = $("si-user").value.trim().toLowerCase();
    const password = $("si-pass").value;
    if (!username || !password) { msg.textContent = "Enter your username and password."; shake(forms.signin); return; }
    busy(forms.signin, true);
    try {
      await PT.api.post("/api/login", { username, password });
      location.replace("/");
    } catch (err) {
      msg.textContent = explain(err);
      shake(forms.signin);
      busy(forms.signin, false);
      $("si-pass").select();
    }
  });

  // ---- request access ----
  forms.register.addEventListener("submit", async (event) => {
    event.preventDefault();
    const msg = $("rg-msg");
    msg.textContent = "";
    const username = $("rg-user").value.trim().toLowerCase();
    const password = $("rg-pass").value;
    const problem = PT.util.usernameProblem(username) || PT.util.passwordProblem(password, username) ||
      (password !== $("rg-pass2").value ? "The two passwords are not the same." : null) ||
      (!$("rg-agree").checked ? "Please accept the Terms of Use and the Privacy Policy." : null);
    if (problem) { msg.textContent = problem; shake(forms.register); return; }
    busy(forms.register, true);
    try {
      await PT.api.post("/api/register", { username, password, agree: true });
      forms.register.reset();
      hidePasswords(); // reset() does not turn a shown password back into dots
      resetLive();
      Object.values(forms).forEach((f) => { f.hidden = true; });
      tabBar.hidden = true;
      $("sent").hidden = false;
      $("auth-title").textContent = "Almost there";
    } catch (err) {
      msg.textContent = explain(err);
      shake(forms.register);
    }
    busy(forms.register, false);
  });

  // Over plain HTTP (HTTPS turned off or unavailable) a password crosses the network unprotected.
  if (location.protocol === "http:") {
    const note = document.createElement("p");
    note.className = "auth-warn";
    note.textContent = "This connection is not encrypted. Do not use a password you use anywhere else.";
    document.querySelector(".auth").insertBefore(note, document.querySelector(".auth-foot"));
  }

  if (location.hash === "#register") show("register");
  else if (window.matchMedia && window.matchMedia("(hover: hover)").matches) $("si-user").focus(); // no keyboard pop-up on phones
})();
