function route() {
  const parts = location.hash.replace(/^#\/?/, "").split("/").filter(Boolean).map(decodeURIComponent);
  const main = document.getElementById("main");
  PT.viewer.close();

  if (parts[0] === "account") {
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

window.addEventListener("hashchange", route);
route();

// Show who is signed in, top right.
PT.api.me().then((me) => {
  const who = document.getElementById("who");
  who.textContent = "\u{1F464} " + me.username;
  who.hidden = false;
}).catch(() => {});
