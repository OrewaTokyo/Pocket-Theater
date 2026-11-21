// Fails the build if the project drifts from the class / category / sub-class layout.
const fs = require("fs");
const path = require("path");

const root = path.resolve(__dirname, "..");
const problems = [];
const bad = (message) => problems.push(message);
const exists = (rel) => fs.existsSync(path.join(root, rel));

function walk(rel) {
  const out = [];
  const abs = path.join(root, rel);
  if (!fs.existsSync(abs)) return out;
  for (const entry of fs.readdirSync(abs, { withFileTypes: true })) {
    const child = rel + "/" + entry.name;
    if (entry.isDirectory()) out.push(...walk(child));
    else out.push(child);
  }
  return out;
}

// 1. Top level: only known folders and files.
const ROOT_DIRS = new Set([".git", ".github", "app", "docs", "tests", "tools"]);
const ROOT_FILES = new Set([
  ".gitignore", "README.md", "PRIVACY.md", "SECURITY.md", "TERMS.md", "LICENSE", "build.gradle.kts", "settings.gradle.kts", "gradle.properties", "site.zip",
]);
for (const entry of fs.readdirSync(root, { withFileTypes: true })) {
  if (entry.isDirectory() && !ROOT_DIRS.has(entry.name)) bad('Folder "' + entry.name + '" is not part of the layout. Put it under an existing class folder.');
  if (entry.isFile() && !ROOT_FILES.has(entry.name)) bad('Loose file "' + entry.name + '" at the top level. Move it into a folder.');
}
["app", "docs/setup", "docs/security", "tests/unit", "tools/ci", ".github/workflows/build-apk.yml"].forEach((rel) => {
  if (!exists(rel)) bad("Missing " + rel);
});

// 2. Kotlin: every file lives in a known sub-class folder and its package matches the folder.
const KT_BASE = "app/src/main/java/com/pockettheater";
const KT_FOLDERS = ["core/server", "core/security", "core/storage", "service", "ui/screens", "ui/components", "ui/theme", "util"];
const ktFiles = walk(KT_BASE).filter((f) => f.endsWith(".kt"));
KT_FOLDERS.forEach((dir) => {
  if (!ktFiles.some((f) => path.posix.dirname(f) === KT_BASE + "/" + dir)) bad("Folder " + dir + " has no Kotlin files.");
});
ktFiles.forEach((file) => {
  const dir = path.posix.dirname(file).slice(KT_BASE.length + 1);
  if (!KT_FOLDERS.includes(dir)) {
    bad(file + " is in an unknown folder. Allowed: " + KT_FOLDERS.join(", "));
    return;
  }
  const expected = "com.pockettheater." + dir.replace(/\//g, ".");
  const match = fs.readFileSync(path.join(root, file), "utf8").match(/^package\s+([\w.]+)/m);
  if (!match || match[1] !== expected) bad(file + ': package should be "' + expected + '"');
});

// 3. Manifest: the activity and service files it names must exist.
const manifest = fs.readFileSync(path.join(root, "app/src/main/AndroidManifest.xml"), "utf8");
for (const m of manifest.matchAll(/android:name="\.([\w.]+)"/g)) {
  const parts = m[1].split(".");
  const rel = KT_BASE + "/" + parts.slice(0, -1).join("/") + "/" + parts[parts.length - 1] + ".kt";
  if (!exists(rel)) bad("AndroidManifest.xml names ." + m[1] + " but " + rel + " does not exist.");
}

// 3b. The legal texts exist twice (root, for GitHub, and inside the app, for the website and the phone). They must match.
[["TERMS.md", "terms.md"], ["PRIVACY.md", "privacy.md"], ["SECURITY.md", "security.md"]].forEach(([top, copy]) => {
  const inApp = "app/src/main/assets/web/legal/" + copy;
  if (!exists(top) || !exists(inApp)) { bad("Missing " + top + " or " + inApp); return; }
  if (fs.readFileSync(path.join(root, top), "utf8") !== fs.readFileSync(path.join(root, inApp), "utf8")) {
    bad(top + " and " + inApp + " differ. Copy " + top + " over " + inApp + ".");
  }
});

// 4. Web site: every file is used by index.html, and everything index.html uses exists.
const WEB = "app/src/main/assets/web";
const WEB_FOLDERS = ["pages", "components", "styles", "scripts", "legal"];
// The signed-in site (index.html) and the sign in screen (login.html) each load their own files.
const PAGES = ["index.html", "login.html", "legal.html"];
const used = new Set();
PAGES.forEach((page) => {
  if (!exists(WEB + "/" + page)) { bad("Missing " + WEB + "/" + page); return; }
  const html = fs.readFileSync(path.join(root, WEB, page), "utf8");
  [...html.matchAll(/(?:src|href)="\/([^"]+)"/g)].forEach((m) => used.add(m[1].split(/[?#]/)[0]));
});
used.forEach((rel) => { if (!exists(WEB + "/" + rel)) bad("A page uses /" + rel + " but the file does not exist."); });
const webFiles = walk(WEB).map((f) => f.slice(WEB.length + 1));
webFiles.forEach((rel) => {
  if (PAGES.includes(rel)) return;
  if (!WEB_FOLDERS.includes(rel.split("/")[0])) bad("web/" + rel + " is not inside pages, components, styles or scripts.");
  else if (/\.(js|css)$/.test(rel) && !used.has(rel)) bad("web/" + rel + " is not used by index.html or login.html.");
});

if (problems.length) {
  console.error("Layout check failed:\n- " + problems.join("\n- "));
  process.exit(1);
}
console.log("Layout OK (" + ktFiles.length + " Kotlin files, " + webFiles.length + " web files)");
