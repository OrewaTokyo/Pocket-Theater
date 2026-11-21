// Markdown reader used by the legal pages. Runs the same file the website loads, with a tiny stand-in for the page.
const assert = require("assert");
const fs = require("fs");
const path = require("path");
global.window = global;
global.document = { createElement: () => ({}), createTextNode: (t) => ({ t }) };
require("../../app/src/main/assets/web/scripts/api.js");
require("../../app/src/main/assets/web/scripts/markdown.js");
const md = PT.md;

const blocks = md.parse("# Title\n\nSome **bold** words\nand more.\n\n- one\n- two\n\n| A | B |\n| --- | --- |\n| x | y |\n");
assert.deepStrictEqual(blocks[0], { type: "h", level: 1, text: "Title" });
assert.deepStrictEqual(blocks[1], { type: "p", text: "Some **bold** words and more." });
assert.deepStrictEqual(blocks[2], { type: "ul", items: ["one", "two"] });
assert.deepStrictEqual(blocks[3], { type: "table", head: ["A", "B"], rows: [["x", "y"]] });

assert.deepStrictEqual(md.inline("a **b** `c` [d](https://e.com)").map((p) => p.t), ["text", "b", "text", "code", "text", "a"]);
assert.strictEqual(md.linkTarget("PRIVACY.md"), "/legal.html#privacy");
assert.strictEqual(md.linkTarget("docs/security/threat-model.md"), null);
assert.strictEqual(md.linkTarget("javascript:alert(1)"), null);
assert.strictEqual(md.linkTarget("https://example.com"), "https://example.com");

// The real documents all parse into headings and text.
["TERMS.md", "PRIVACY.md", "SECURITY.md"].forEach((file) => {
  const parsed = md.parse(fs.readFileSync(path.join(__dirname, "../..", file), "utf8"));
  assert.ok(parsed.length > 5, file + " should have content");
  assert.strictEqual(parsed[0].type, "h");
});

console.log("markdown OK");
