// Android's regex engine (ICU) is stricter than desktop Java: a bare "{" or "}" that is not part of a quantifier such as
// {2} or {1,3} makes Regex(...) throw when the class loads, and the app closes. The JVM unit tests cannot see that, so
// this scans every regex written in the Kotlin code for it. Fix: escape the brace (\\{ and \\}).
const fs = require("fs");
const path = require("path");

const root = path.resolve(__dirname, "../..");
const problems = [];

function walk(dir) {
  const out = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) out.push(...walk(full));
    else if (entry.name.endsWith(".kt")) out.push(full);
  }
  return out;
}

// Turns the text between the quotes of a Kotlin string into the pattern the regex engine really sees.
function unescapeKotlin(text) {
  return text.replace(/\\(.)/g, (all, c) => ({ n: "\n", t: "\t", r: "\r", b: "\b", "\\": "\\", '"': '"', "'": "'", $: "$" }[c] ?? c));
}

// Returns a message when the pattern has a brace the Android engine would reject, otherwise null.
function braceProblem(pattern) {
  let inClass = false;
  for (let i = 0; i < pattern.length; i++) {
    const c = pattern[i];
    if (c === "\\") { i++; continue; }
    if (inClass) { if (c === "]") inClass = false; else if (c === "}" || c === "{") return 'bare "' + c + '" inside [ ] at index ' + i; continue; }
    if (c === "[") { inClass = true; continue; }
    if (c === "{") {
      const quantifier = /^\{\d+(,\d*)?\}/.exec(pattern.slice(i));
      if (!quantifier) return 'bare "{" at index ' + i;
      i += quantifier[0].length - 1;
    } else if (c === "}") {
      return 'bare "}" at index ' + i;
    }
  }
  return null;
}

// Self-test, so the scanner itself cannot quietly stop working.
const bad = unescapeKotlin('\\\\{[^}]*}');
if (!braceProblem(bad)) problems.push("scanner failed to flag the pattern that crashed the app: " + bad);
if (braceProblem(unescapeKotlin('(\\\\d{2}):(\\\\d{1,3})[,.]\\\\{[^\\\\}]*\\\\}'))) problems.push("scanner flagged a valid pattern");

const literal = /(?:Regex|Pattern\.compile)\(\s*"((?:[^"\\]|\\.)*)"/g;
let checked = 0;
for (const file of walk(path.join(root, "app/src/main"))) {
  const source = fs.readFileSync(file, "utf8");
  for (const match of source.matchAll(literal)) {
    checked++;
    const issue = braceProblem(unescapeKotlin(match[1]));
    if (issue) {
      const line = source.slice(0, match.index).split("\n").length;
      problems.push(path.relative(root, file) + ":" + line + " " + issue + "  ->  " + match[1]);
    }
  }
}

if (problems.length) {
  console.error("Regex problems that would crash the app on Android:\n - " + problems.join("\n - "));
  process.exit(1);
}
console.log("android-regex OK (" + checked + " regex patterns checked)");
