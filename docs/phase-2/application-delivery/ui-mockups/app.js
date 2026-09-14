const allowedScreens = ["discover", "library", "values", "preview", "releases", "ai-settings"];

function requestedScreen() {
  const value = new URLSearchParams(window.location.search).get("screen");
  return allowedScreens.includes(value) ? value : "discover";
}

function showScreen(screen, push = true) {
  document.querySelectorAll("[data-screen-panel]").forEach((panel) => {
    panel.classList.toggle("active", panel.dataset.screenPanel === screen);
  });
  document.querySelectorAll(".nav-item[data-screen]").forEach((item) => {
    item.classList.toggle("active", item.dataset.screen === screen);
  });
  if (push) {
    const url = new URL(window.location.href);
    url.searchParams.set("screen", screen);
    window.history.pushState({ screen }, "", url);
  }
  window.scrollTo({ top: 0, behavior: "instant" });
}

document.querySelectorAll("[data-screen]").forEach((button) => {
  button.addEventListener("click", () => showScreen(button.dataset.screen));
});

let toastTimer;
document.querySelectorAll("[data-toast]").forEach((button) => {
  button.addEventListener("click", () => {
    const toast = document.querySelector(".toast");
    toast.textContent = button.dataset.toast;
    toast.classList.add("show");
    window.clearTimeout(toastTimer);
    toastTimer = window.setTimeout(() => toast.classList.remove("show"), 2400);
  });
});

window.addEventListener("popstate", () => showScreen(requestedScreen(), false));
showScreen(requestedScreen(), false);
