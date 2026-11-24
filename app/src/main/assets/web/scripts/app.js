function route() {
  const parts = location.hash.replace(/^#\/?/, "").split("/").filter(Boolean).map((part) => {
    try { return decodeURIComponent(part); } catch (err) { return part; } // a damaged link must not blank the page
  });
  const main = document.getElementById("main");
  PT.viewer.close();
  if (parts[0] !== "admin" && PT.pages.admin) PT.pages.admin.stop();

  if (parts[0] === "admin" && PT.me && PT.me.admin) {
    PT.pages.admin(main);
  } else if (parts[0] === "account") {
    PT.pages.account(main);
  } else if (parts[0] === "lib" && parts[1]) {
    PT.nav.render("");
    PT.pages.library(main, parts[1], parts.slice(2));
  } else if (parts[0] === "cat" && parts[1]) {
    PT.nav.render(parts[1]);
    PT.pages.home(main, parts[1]);
  } else {
    PT.nav.render("Home");
    PT.pages.home(main, null);
  }
}

PT.route = route;
window.addEventListener("hashchange", route);
route();

// Watch the connection. When it returns, a page that failed to load tries again.
PT.connection.onBack(() => {
  if (document.querySelector("#main .error")) route();
});
PT.connection.start();

// Learn which shelves have something in them, so the navigation only shows those.
function loadShelves() {
  PT.api.libraries().then((data) => {
    PT.shelves = new Set(data.libraries.map((l) => l.category));
    PT.nav.refresh();
  }).catch(() => {});
}
loadShelves();
PT.connection.onBack(loadShelves);

// Show who is signed in, top right.
PT.api.me().then((me) => {
  PT.session.update(me);
  const who = document.getElementById("who");
  who.textContent = "\u{1F464} " + me.username;
  who.hidden = false;
}).catch(() => {});
