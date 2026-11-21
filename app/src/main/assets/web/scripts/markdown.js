// A small, safe reader for the Terms and Privacy pages: headings, paragraphs, bullets, tables, **bold**, `code` and links.
// Everything is built as text nodes, so nothing in a document can run as code.
window.PT = window.PT || {};

PT.markdown = {
  splitRow(line) {
    return line.trim().replace(/^\|/, "").replace(/\|$/, "").split("|").map((cell) => cell.trim());
  },

  // Text in, a list of blocks out. No page needed, so it can be tested on its own.
  parse(text) {
    const lines = text.replace(/\r\n?/g, "\n").split("\n");
    const startsBlock = (line) => /^(#{1,3}\s|\s*-\s|\|)/.test(line.trim() === "" ? "" : line);
    const blocks = [];
    let i = 0;
    while (i < lines.length) {
      const line = lines[i];
      if (!line.trim()) { i++; continue; }
      const heading = line.match(/^(#{1,3})\s+(.*)$/);
      if (heading) {
        blocks.push({ type: "heading", level: heading[1].length, text: heading[2].trim() });
        i++;
      } else if (/^\s*-\s+/.test(line)) {
        const items = [];
        while (i < lines.length && /^\s*-\s+/.test(lines[i])) { items.push(lines[i].replace(/^\s*-\s+/, "").trim()); i++; }
        blocks.push({ type: "list", items });
      } else if (line.trim().startsWith("|")) {
        const rows = [];
        while (i < lines.length && lines[i].trim().startsWith("|")) { rows.push(this.splitRow(lines[i])); i++; }
        const real = rows.filter((row) => !row.every((cell) => /^:?-{2,}:?$/.test(cell)));
        blocks.push({ type: "table", head: real[0] || [], rows: real.slice(1) });
      } else {
        const parts = [];
        while (i < lines.length && lines[i].trim() && !startsBlock(lines[i])) { parts.push(lines[i].trim()); i++; }
        blocks.push({ type: "para", text: parts.join(" ") });
      }
    }
    return blocks;
  },

  // One line of text in, pieces out: plain text, bold, code and links.
  inline(text) {
    const pieces = [];
    const pattern = /\*\*([^*]+)\*\*|`([^`]+)`|\[([^\]]+)\]\(([^)]+)\)/g;
    let last = 0;
    let m;
    while ((m = pattern.exec(text)) !== null) {
      if (m.index > last) pieces.push({ type: "text", text: text.slice(last, m.index) });
      if (m[1] !== undefined) pieces.push({ type: "bold", text: m[1] });
      else if (m[2] !== undefined) pieces.push({ type: "code", text: m[2] });
      else pieces.push({ type: "link", text: m[3], href: m[4] });
      last = pattern.lastIndex;
    }
    if (last < text.length) pieces.push({ type: "text", text: text.slice(last) });
    return pieces;
  },

  // Only web addresses become links. Anything else (a file name, a script) stays plain text.
  safeHref(href) {
    return /^https?:\/\//i.test(href) ? href : null;
  },

  nodes(text) {
    const el = PT.util.el;
    return this.inline(text).map((piece) => {
      if (piece.type === "bold") return el("strong", { text: piece.text });
      if (piece.type === "code") return el("code", { text: piece.text });
      if (piece.type === "link") {
        const href = this.safeHref(piece.href);
        return href
          ? el("a", { href, target: "_blank", rel: "noopener noreferrer", text: piece.text })
          : document.createTextNode(piece.text);
      }
      return document.createTextNode(piece.text);
    });
  },

  render(text) {
    const el = PT.util.el;
    const fragment = document.createDocumentFragment();
    this.parse(text).forEach((block) => {
      if (block.type === "heading") {
        fragment.append(el("h" + block.level, null, this.nodes(block.text)));
      } else if (block.type === "para") {
        fragment.append(el("p", null, this.nodes(block.text)));
      } else if (block.type === "list") {
        fragment.append(el("ul", null, block.items.map((item) => el("li", null, this.nodes(item)))));
      } else {
        const head = el("tr", null, block.head.map((cell) => el("th", null, this.nodes(cell))));
        const rows = block.rows.map((row) => el("tr", null, row.map((cell) => el("td", null, this.nodes(cell)))));
        fragment.append(el("div", { class: "table-wrap" }, [
          el("table", null, [el("thead", null, [head]), el("tbody", null, rows)]),
        ]));
      }
    });
    return fragment;
  },
};
