window.PT = window.PT || {};
PT.renderId = 0;
PT.categories = ["Movies", "Anime", "Games", "Documents", "Other"];

PT.api = {
  async json(url) {
    const res = await fetch(url);
    if (!res.ok) {
      const body = await res.json().catch(() => ({}));
      throw new Error(body.error || "Request failed (" + res.status + ")");
    }
    return res.json();
  },
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
};
