window.PT = window.PT || {};
PT.renderId = 0;
// One place that defines every shelf. "id" is what the phone app saves, so never rename an id: Games is shown as Gaming.
PT.cats = [
  { id: "Series", label: "Series", icon: "📺", accent: "#5ee0ff", main: true, blurb: "Shows, seasons and binge nights" },
  { id: "Anime", label: "Anime", icon: "🍥", accent: "#ff7bb5", main: true, blurb: "Episodes, OVAs and movies" },
  { id: "Movies", label: "Movies", icon: "🎬", accent: "#ffb547", main: true, blurb: "Hollywood and beyond" },
  { id: "Games", label: "Gaming", icon: "🎮", accent: "#5cff9d", main: true, blurb: "Gameplay, trailers and game files" },
  { id: "Documents", label: "Documents", icon: "📚", accent: "#a39bc4", main: false, blurb: "PDFs, books and notes" },
  { id: "Other", label: "Other", icon: "📦", accent: "#a39bc4", main: false, blurb: "Everything else" },
];
PT.categories = PT.cats.map((c) => c.id);

PT.api = {
  async json(url) {
    const res = await fetch(url);
    if (res.status === 401) {
      location.href = "/login.html"; // signed out, or the admin turned the account off
      throw new Error("Signed out");
    }
    if (!res.ok) {
      const body = await res.json().catch(() => ({}));
      throw new Error(body.error || "Request failed (" + res.status + ")");
    }
    return res.json();
  },
  // Sends JSON. Errors from the phone come back as plain sentences in err.message.
  async post(url, body) {
    const res = await fetch(url, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.error || "Something went wrong (" + res.status + ")");
    return data;
  },
  me() { return this.json("/api/me"); },
  libraries() { return this.json("/api/libraries"); },
  list(lib, parts) {
    return this.json("/api/list?lib=" + encodeURIComponent(lib) + "&path=" + encodeURIComponent(parts.join("/")));
  },
  fileUrl(lib, parts) {
    return "/file?lib=" + encodeURIComponent(lib) + "&path=" + encodeURIComponent(parts.join("/"));
  },
};

PT.util = {
  // File names come from the phone, so they are only ever inserted as text.
  el(tag, props, children) {
    const node = document.createElement(tag);
    for (const key in props || {}) {
      const value = props[key];
      if (key === "text") node.textContent = value;
      else if (key === "class") node.className = value;
      else if (key.startsWith("on")) node.addEventListener(key.slice(2), value);
      else node.setAttribute(key, value);
    }
    (children || []).forEach((child) => node.append(child));
    return node;
  },
  size(bytes) {
    if (bytes < 0) return "";
    const units = ["B", "KB", "MB", "GB", "TB"];
    let i = 0;
    while (bytes >= 1024 && i < units.length - 1) { bytes /= 1024; i++; }
    return bytes.toFixed(i === 0 ? 0 : 1) + " " + units[i];
  },
  kind(entry) {
    if (entry.dir) return "folder";
    const mime = (entry.mime || "").toLowerCase();
    const ext = entry.name.split(".").pop().toLowerCase();
    if (mime.startsWith("video/") || ["mp4", "mkv", "webm", "m4v", "mov", "avi"].includes(ext)) return "video";
    if (mime.startsWith("image/") || ["jpg", "jpeg", "png", "gif", "webp", "bmp"].includes(ext)) return "image";
    if (mime === "application/pdf" || ext === "pdf") return "pdf";
    if (mime.startsWith("audio/") || ["mp3", "m4a", "flac", "ogg", "wav"].includes(ext)) return "audio";
    return "file";
  },
  icon(kind) {
    return { folder: "📁", video: "🎞️", image: "🖼️", pdf: "📄", audio: "🎧", file: "📦" }[kind];
  },
  enc(part) { return encodeURIComponent(part); },
  // Same rules as the phone. A sentence when something is wrong, otherwise null.
  usernameProblem(name) {
    return /^[a-z0-9_]{3,20}$/.test(name) ? null : "Username must be 3 to 20 letters, numbers or _ (lowercase).";
  },
  passwordProblem(password) {
    if (password.length < 8) return "Password needs at least 8 characters.";
    if (password.length > 64) return "Password can be at most 64 characters.";
    return null;
  },
  // Look up a shelf by id. An id the site does not know still gets a sensible look.
  cat(id) {
    return PT.cats.find((c) => c.id === id) || { id, label: id, icon: "📦", accent: "#a39bc4", main: false, blurb: "" };
  },
};
