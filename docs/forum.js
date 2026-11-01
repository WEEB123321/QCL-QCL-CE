(function () {
  "use strict";

  var REPO = "WEEB123321/QCL-QCL-CE";
  var REPO_ID = "R_kgDOU9CPgQ";
  var CATEGORY = "General";
  var CATEGORY_ID = "DIC_kwDOU9CPgc4DKIWg";
  var NAME_KEY = "qcl_forum_name";

  var gate = document.getElementById("forum-gate");
  var main = document.getElementById("forum-main");
  var form = document.getElementById("forum-name-form");
  var input = document.getElementById("forum-name-input");
  var currentName = document.getElementById("forum-current-name");
  var changeName = document.getElementById("forum-change-name");
  var tabs = document.querySelectorAll(".forum-tabs button");
  var container = document.getElementById("giscus-container");
  if (!gate || !main || !form || !input || !container) return;

  var activeTerm = "QCL-CE 论坛 · 综合讨论";

  function savedName() {
    try {
      return (localStorage.getItem(NAME_KEY) || "").trim();
    } catch (e) {
      return "";
    }
  }

  function saveName(value) {
    try {
      localStorage.setItem(NAME_KEY, value);
    } catch (e) {
      // 隐私模式下 localStorage 可能不可用；仍允许本次进入。
    }
  }

  function themeName() {
    return document.documentElement.getAttribute("data-theme") === "light"
      ? "light" : "dark";
  }

  function setGateVisible(visible) {
    gate.hidden = !visible;
    main.hidden = visible;
    if (visible) {
      input.value = savedName();
      input.focus();
    }
  }

  function enter(name) {
    currentName.textContent = name;
    setGateVisible(false);
    loadBoard(activeTerm);
  }

  function loadBoard(term) {
    activeTerm = term;
    for (var i = 0; i < tabs.length; i++) {
      tabs[i].classList.toggle("active", tabs[i].getAttribute("data-term") === term);
    }
    container.innerHTML = "";
    var script = document.createElement("script");
    script.src = "https://giscus.app/client.js";
    script.async = true;
    script.crossOrigin = "anonymous";
    script.setAttribute("data-repo", REPO);
    script.setAttribute("data-repo-id", REPO_ID);
    script.setAttribute("data-category", CATEGORY);
    script.setAttribute("data-category-id", CATEGORY_ID);
    script.setAttribute("data-mapping", "specific");
    script.setAttribute("data-term", term);
    script.setAttribute("data-strict", "0");
    script.setAttribute("data-reactions-enabled", "1");
    script.setAttribute("data-emit-metadata", "0");
    script.setAttribute("data-input-position", "top");
    script.setAttribute("data-theme", themeName());
    script.setAttribute("data-lang", "zh-CN");
    script.setAttribute("data-loading", "lazy");
    container.appendChild(script);
  }

  form.addEventListener("submit", function (event) {
    event.preventDefault();
    var name = (input.value || "").trim().slice(0, 20);
    if (!name) {
      input.focus();
      return;
    }
    saveName(name);
    enter(name);
  });

  changeName.addEventListener("click", function () {
    setGateVisible(true);
  });

  for (var i = 0; i < tabs.length; i++) {
    tabs[i].addEventListener("click", function () {
      if (main.hidden) return;
      loadBoard(this.getAttribute("data-term") || activeTerm);
    });
  }

  // Giscus 的明暗主题跟随站点切换。
  function pushTheme() {
    var frame = document.querySelector("iframe.giscus-frame");
    if (!frame || !frame.contentWindow) return;
    frame.contentWindow.postMessage({
      giscus: { setConfig: { theme: themeName() } }
    }, "https://giscus.app");
  }

  try {
    new MutationObserver(pushTheme).observe(document.documentElement, {
      attributes: true,
      attributeFilter: ["data-theme"]
    });
  } catch (e) {
  }

  var existing = savedName();
  if (existing) {
    enter(existing);
  } else {
    setGateVisible(true);
  }
})();
