(function () {
  "use strict";

  var REPO = "WEEB123321/QCL-QCL-CE";
  var REPO_ID = "R_kgDOU9CPgQ";
  var CATEGORY = "General";
  var CATEGORY_ID = "DIC_kwDOU9CPgc4DKIWg";
  var NAME_KEY = "qcl_forum_name";
  var NUMBERS_URL = "forum-discussions.json";

  var gate = document.getElementById("forum-gate");
  var main = document.getElementById("forum-main");
  var form = document.getElementById("forum-name-form");
  var input = document.getElementById("forum-name-input");
  var currentName = document.getElementById("forum-current-name");
  var changeName = document.getElementById("forum-change-name");
  var tabs = document.querySelectorAll(".forum-tabs button");
  var container = document.getElementById("giscus-container");
  var boardLink = document.getElementById("forum-board-link");
  var status = document.getElementById("forum-status");
  if (!gate || !main || !form || !input || !container) return;

  var activeTerm = "QCL-CE 论坛 · 综合讨论";
  // 已知的 Discussion 编号。按编号绑定不依赖 GitHub 搜索索引，
  // 新建的讨论也不会因为索引延迟而在网页上显示成空。
  var knownNumbers = { "QCL-CE 论坛 · 综合讨论": 1 };
  var numbersPromise = null;
  var loadSeq = 0;

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

  function discussionUrl(term, number) {
    var base = "https://github.com/" + REPO + "/discussions";
    if (number) return base + "/" + number;
    return base + "/new?category=general&title=" + encodeURIComponent(term);
  }

  function updateBoardLink(term, number) {
    if (!boardLink) return;
    boardLink.href = discussionUrl(term, number);
    boardLink.textContent = number ? "在 GitHub 打开本板块" : "在 GitHub 创建本板块";
  }

  function escapeHtml(value) {
    return String(value).replace(/[&<>"']/g, function (ch) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[ch];
    });
  }

  function showStatus(kind, html) {
    if (!status) return;
    status.hidden = false;
    status.setAttribute("data-kind", kind);
    status.innerHTML = html;
  }

  function hideStatus() {
    if (!status) return;
    status.hidden = true;
    status.textContent = "";
  }

  function githubFallback(term) {
    var number = knownNumbers[term] || 0;
    return "<a href=\"" + discussionUrl(term, number) + "\" target=\"_blank\" rel=\"noopener\">" +
      (number ? "在 GitHub 打开本板块" : "在 GitHub 创建本板块") + "</a>";
  }

  function loadNumbers() {
    if (numbersPromise) return numbersPromise;
    var request;
    try {
      request = fetch(NUMBERS_URL + "?t=" + Date.now(), { cache: "no-store" });
    } catch (e) {
      numbersPromise = Promise.resolve(knownNumbers);
      return numbersPromise;
    }
    numbersPromise = request
      .then(function (response) { return response.ok ? response.json() : null; })
      .then(function (data) {
        if (data && typeof data === "object") {
          for (var term in data) {
            if (Object.prototype.hasOwnProperty.call(data, term) &&
                typeof data[term] === "number" && data[term] > 0) {
              knownNumbers[term] = data[term];
            }
          }
        }
        return knownNumbers;
      })
      .catch(function () { return knownNumbers; });
    return numbersPromise;
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

  function renderGiscus(term, number) {
    var script = document.createElement("script");
    script.src = "https://giscus.app/client.js";
    script.async = true;
    script.crossOrigin = "anonymous";
    script.setAttribute("data-repo", REPO);
    script.setAttribute("data-repo-id", REPO_ID);
    script.setAttribute("data-category", CATEGORY);
    script.setAttribute("data-category-id", CATEGORY_ID);
    if (number) {
      script.setAttribute("data-mapping", "number");
      script.setAttribute("data-term", String(number));
    } else {
      script.setAttribute("data-mapping", "specific");
      script.setAttribute("data-term", term);
      script.setAttribute("data-strict", "1");
    }
    script.setAttribute("data-reactions-enabled", "1");
    script.setAttribute("data-emit-metadata", "0");
    script.setAttribute("data-input-position", "top");
    script.setAttribute("data-theme", themeName());
    script.setAttribute("data-lang", "zh-CN");
    script.setAttribute("data-loading", "lazy");
    container.appendChild(script);
  }

  function loadBoard(term) {
    activeTerm = term;
    var seq = ++loadSeq;
    for (var i = 0; i < tabs.length; i++) {
      tabs[i].classList.toggle("active", tabs[i].getAttribute("data-term") === term);
    }
    container.innerHTML = "";
    hideStatus();
    updateBoardLink(term, knownNumbers[term] || 0);
    loadNumbers().then(function (numbers) {
      if (seq !== loadSeq) return;
      var number = numbers[term] || 0;
      updateBoardLink(term, number);
      renderGiscus(term, number);
      if (location.protocol === "file:") {
        showStatus("info", "当前是本地文件模式，GitHub 登录无法回调到 file://，评论会发送失败。请使用" +
          "<a href=\"https://weeb123321.github.io/QCL-QCL-CE/forum.html\">线上论坛</a>发表评论。");
      }
    });
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

  // Giscus 的错误只会发到父页面控制台；这里把它显示出来，并给出 GitHub 兜底入口。
  window.addEventListener("message", function (event) {
    if (event.origin !== "https://giscus.app") return;
    var data = event.data;
    if (!data || typeof data !== "object" || !data.giscus) return;
    var error = data.giscus.error;
    if (typeof error !== "string" || !error) return;
    if (error.indexOf("Discussion not found") >= 0) {
      showStatus("info", "这个板块还没有讨论线程；发出第一条评论后会自动创建。也可以在 " +
        githubFallback(activeTerm) + "。");
    } else if (error.indexOf("giscus is not installed") >= 0) {
      showStatus("error", "Giscus App 还没有授权给这个仓库，评论区暂时不可用。请管理员安装后刷新页面。");
    } else if (error.indexOf("Bad credentials") >= 0 ||
               error.indexOf("Invalid state value") >= 0 ||
               error.indexOf("State has expired") >= 0) {
      showStatus("error", "GitHub 登录已过期，请刷新页面重新登录；也可以" + githubFallback(activeTerm) + "。");
    } else if (error.indexOf("API rate limit exceeded") >= 0) {
      showStatus("error", "GitHub API 达到限流，请稍后再试；也可以" + githubFallback(activeTerm) + "。");
    } else {
      showStatus("error", "评论区加载失败：" + escapeHtml(error) + "。也可以" + githubFallback(activeTerm) + "。");
    }
  });

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
