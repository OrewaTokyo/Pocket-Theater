const assert = require("assert");
const path = require("path");

global.window = global;
global.PT = {};
require(path.join(__dirname, "../../app/src/main/assets/web/components/windows-app/windows-app.js"));
const w = PT.windowsApp;
const DAY = 24 * 60 * 60 * 1000;

// Only Windows PCs are offered the app.
const chromeWindows = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";
const edgeWindows = chromeWindows + " Edg/130.0.0.0";
const firefoxWindows = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:132.0) Gecko/20100101 Firefox/132.0";
assert.strictEqual(w.isWindowsPC(chromeWindows), true);
assert.strictEqual(w.isWindowsPC(edgeWindows), true);
assert.strictEqual(w.isWindowsPC(firefoxWindows), true);
assert.strictEqual(w.isWindowsPC("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36"), false);
assert.strictEqual(w.isWindowsPC("Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148"), false);
assert.strictEqual(w.isWindowsPC("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Version/17.0 Safari/605.1.15"), false);
assert.strictEqual(w.isWindowsPC("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36"), false);
assert.strictEqual(w.isWindowsPC("Mozilla/5.0 (compatible; MSIE 10.0; Windows Phone 8.0; Trident/6.0; IEMobile/10.0)"), false);
assert.strictEqual(w.isWindowsPC(""), false);
assert.strictEqual(w.isWindowsPC(undefined), false);
// Where the browser says its platform plainly, that wins over the text.
assert.strictEqual(w.isWindowsPC("anything", { platform: "Windows", mobile: false }), true);
assert.strictEqual(w.isWindowsPC(chromeWindows, { platform: "Android", mobile: true }), false);
assert.strictEqual(w.isWindowsPC(chromeWindows, { platform: "Windows", mobile: true }), false);
assert.strictEqual(w.isWindowsPC(chromeWindows, { platform: "" }), true);

// When the card shows.
const now = 1700000000000;
assert.strictEqual(w.shouldOffer(chromeWindows, null, null, now), true);                         // first visit
assert.strictEqual(w.shouldOffer("Linux; Android Mobile", null, null, now), false);               // never on other devices
assert.strictEqual(w.shouldOffer(chromeWindows, null, { never: true }, now), false);              // "Don't show again"
assert.strictEqual(w.shouldOffer(chromeWindows, null, { until: now + DAY }, now), false);         // "Not now", still within 30 days
assert.strictEqual(w.shouldOffer(chromeWindows, null, { until: now - 1 }, now), true);            // and it comes back afterwards
assert.strictEqual(w.shouldOffer(chromeWindows, null, {}, now), true);                            // an unknown saved value is ignored

// What each button remembers.
assert.deepStrictEqual(w.choiceFor("later", now), { until: now + 30 * DAY });
assert.deepStrictEqual(w.choiceFor("never", now), { never: true });
assert.deepStrictEqual(w.choiceFor("downloaded", now), { never: true });

// Saved choices survive a round trip, and broken storage never throws.
const memory = {};
const storage = { getItem: (k) => (k in memory ? memory[k] : null), setItem: (k, v) => { memory[k] = String(v); } };
assert.strictEqual(w.readChoice(storage), null);
assert.strictEqual(w.writeChoice(storage, { never: true }), true);
assert.deepStrictEqual(w.readChoice(storage), { never: true });
memory[w.KEY] = "not json {";
assert.strictEqual(w.readChoice(storage), null);
memory[w.KEY] = "42";
assert.strictEqual(w.readChoice(storage), null);
const broken = { getItem() { throw new Error("blocked"); }, setItem() { throw new Error("full"); } };
assert.strictEqual(w.readChoice(broken), null);
assert.strictEqual(w.writeChoice(broken, { never: true }), false);

// The link goes to the downloads page, in a new tab, without letting that page reach back.
const link = w.link();
assert.strictEqual(link.href, "https://github.com/OrewaTokyo/Pocket-Theater-Windows/releases");
assert.strictEqual(link.target, "_blank");
assert.ok(/noopener/.test(link.rel) && /noreferrer/.test(link.rel));

console.log("windows-app: ok");
