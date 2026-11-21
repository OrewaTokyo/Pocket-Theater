const assert = require("assert");
const fs = require("fs");
const path = require("path");

global.window = global;
const web = path.join(__dirname, "../../app/src/main/assets/web");
require(path.join(web, "scripts/api.js"));
require(path.join(web, "scripts/markdown.js"));
const md = PT.markdown;

// Blocks: heading, paragraph, bullets, table (the dashed row is not data).
const blocks = md.parse("# Title\n\nHello **world** and `code`.\n\n- one\n- two\n\n| A | B |\n| --- | --- |\n| 1 | 2 |\n");
assert.deepStrictEqual(blocks.map((b) => b.type), ["heading", "para", "list", "table"]);
assert.strictEqual(blocks[0].level, 1);
assert.deepStrictEqual(blocks[2].items, ["one", "two"]);
assert.deepStrictEqual(blocks[3].head, ["A", "B"]);
assert.deepStrictEqual(blocks[3].rows, [["1", "2"]]);

// Lines of one paragraph join up, and a heading ends a paragraph.
const joined = md.parse("one\ntwo\n## Next");
assert.deepStrictEqual(joined.map((b) => b.type), ["para", "heading"]);
assert.strictEqual(joined[0].text, "one two");

// Inline pieces.
assert.deepStrictEqual(md.inline("a **b** `c` [d](https://e.f)"), [
  { type: "text", text: "a " }, { type: "bold", text: "b" }, { type: "text", text: " " },
  { type: "code", text: "c" }, { type: "text", text: " " }, { type: "link", text: "d", href: "https://e.f" },
]);

// Only web addresses become links.
assert.strictEqual(md.safeHref("https://x.y"), "https://x.y");
assert.strictEqual(md.safeHref("javascript:alert(1)"), null);
assert.strictEqual(md.safeHref("SECURITY.md"), null);

// The real documents read properly.
for (const name of ["terms", "privacy"]) {
  const text = fs.readFileSync(path.join(web, "legal", name + ".md"), "utf8");
  const parsed = md.parse(text);
  assert.strictEqual(parsed[0].type, "heading", name + " starts with a heading");
  assert.ok(parsed.filter((b) => b.type === "heading").length >= 5, name + " has sections");
  assert.ok(/Last updated: \d/.test(text), name + " shows its date");
}
const privacy = md.parse(fs.readFileSync(path.join(web, "legal/privacy.md"), "utf8"));
assert.ok(privacy.some((b) => b.type === "table" && b.rows.length >= 5), "privacy has its tables");

console.log("markdown OK");
