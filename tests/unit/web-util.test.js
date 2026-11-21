const assert = require("assert");
const path = require("path");

global.window = global;
require(path.join(__dirname, "../../app/src/main/assets/web/scripts/api.js"));
const u = PT.util;

assert.strictEqual(u.size(-1), "");
assert.strictEqual(u.size(0), "0 B");
assert.strictEqual(u.size(1536), "1.5 KB");
assert.strictEqual(u.size(5 * 1024 * 1024), "5.0 MB");

assert.strictEqual(u.kind({ name: "a", dir: true }), "folder");
assert.strictEqual(u.kind({ name: "ep01.mkv", mime: "" }), "video");
assert.strictEqual(u.kind({ name: "x.bin", mime: "video/mp4" }), "video");
assert.strictEqual(u.kind({ name: "cover.PNG", mime: "" }), "image");
assert.strictEqual(u.kind({ name: "book.pdf", mime: "" }), "pdf");
assert.strictEqual(u.kind({ name: "song.flac", mime: "" }), "audio");
assert.strictEqual(u.kind({ name: "notes.txt", mime: "text/plain" }), "file");

assert.strictEqual(u.enc("a b/c"), "a%20b%2Fc");
assert.strictEqual(PT.api.fileUrl("ab12", ["Movies", "a b.mp4"]), "/file?lib=ab12&path=Movies%2Fa%20b.mp4");

// Shelves: Series sits beside Anime, Movies and Gaming. The saved id "Games" is shown as "Gaming".
assert.deepStrictEqual(PT.categories.slice(0, 4), ["Series", "Anime", "Movies", "Games"]);
assert.strictEqual(u.cat("Games").label, "Gaming");
assert.strictEqual(u.cat("Series").icon, "\u{1F4FA}");
assert.strictEqual(u.cat("Mystery").label, "Mystery");
assert.strictEqual(new Set(PT.categories).size, PT.categories.length);

// Sign-up rules match the phone's.
assert.strictEqual(u.usernameProblem("anime_fan"), null);
assert.ok(u.usernameProblem("ab"));
assert.ok(u.usernameProblem("Has Space"));
assert.ok(u.usernameProblem("UPPER"));
assert.ok(u.usernameProblem("a".repeat(21)));
assert.strictEqual(u.passwordProblem("12345678"), null);
assert.ok(u.passwordProblem("short"));
assert.ok(u.passwordProblem("x".repeat(65)));

// Polling: calm while all is well, quicker after a miss, never slower than 6 seconds while offline.
assert.strictEqual(u.pollDelay(true, 0), 5000);
assert.strictEqual(u.pollDelay(true, 1), 1500);
assert.strictEqual(u.pollDelay(false, 2), 2500);
assert.strictEqual(u.pollDelay(false, 40), 6000);

console.log("web-util OK");
