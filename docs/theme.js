/* ============================================================================
 * QCL-CE 站点：主题切换 + 动效
 *
 * ★ 为什么做成一个独立脚本（而不是写进每个页面）
 *   站点有 index / wiki / sponsor 三个页面 ✗ 主题逻辑写三遍 = 三处不一致 ✗
 *   这个脚本**自己注入**主题切换按钮和动效 ⇒ 页面里只要一行 <script src> ✓
 *
 * ★ 主题优先级
 *   ① localStorage 里用户手动选的  →  ② 系统 prefers-color-scheme
 *   ⇒ 手动选了就永远听手动 ✓ 没选过就跟随系统 ✓
 *
 * ★ 动效原则：只用 transform / opacity（合成层属性）✓
 *   不动 width/height/margin ✗ —— 那些会触发布局，滚动时掉帧 ✗
 * ========================================================================== */
(function () {
  'use strict';

  var KEY = 'qcl-theme';           // 'light' | 'dark' | 不存在（=跟随系统）
  var root = document.documentElement;


  // ---------- 注入样式 ----------
  // ★ 为什么由 JS 注入而不是写进每个页面：
  //   index/wiki 的 CSS 用的是 @media (prefers-color-scheme) ✗
  //   那只能「跟随系统」，**手动切换不生效** ✗
  //   这里注入 html[data-theme="..."] 的变量覆盖 ⇒ 优先级高于 :root ✓ 手动就能压过系统 ✓
  var STYLE_ID = 'QCL_THEME_STYLE';
  function injectStyle() {
    if (document.getElementById(STYLE_ID)) return;
    var css = [
      /* —— 主题变量（手动切换用）—— */
      'html[data-theme="light"]{--bg:#F2F7F7;--surface:#FFFFFF;--text:#142B2A;--sub:#5F7A78;--accent:#0E9E8E;--accent-dark:#0A7A6E;--divider:#D5E5E3;--code:#0C1616;--code-bg:#0C1616;--code-fg:#E6F4F2;--shadow:rgba(0,0,0,.10)}',
      'html[data-theme="dark"]{--bg:#0C1616;--surface:#142222;--text:#E6F4F2;--sub:#8FABA8;--accent:#2AC9B4;--accent-dark:#1FA492;--divider:#223837;--code:#060C0C;--code-bg:#060C0C;--code-fg:#E6F4F2;--shadow:rgba(0,0,0,.45)}',
      'html[data-theme] body{background:var(--bg);color:var(--text);transition:background-color .28s ease,color .28s ease}',
      /* —— 主题切换按钮 —— */
      '#qcl-theme-btn{position:fixed;right:18px;top:70px;z-index:40;font:inherit;font-size:13px;font-weight:600;padding:9px 14px;border-radius:11px;cursor:pointer;background:var(--surface);color:var(--accent);border:1.5px solid var(--accent);box-shadow:0 8px 24px -14px var(--shadow,rgba(0,0,0,.2));transition:transform .16s ease,background-color .2s ease}',
      '#qcl-theme-btn:hover{background:color-mix(in srgb,var(--accent) 12%,transparent)}',
      '@media (max-width:620px){#qcl-theme-btn{top:auto;bottom:18px;right:18px}}',
      /* —— 顶栏滚动阴影 —— */
      'nav.qcl-scrolled{box-shadow:0 6px 22px -14px var(--shadow,rgba(0,0,0,.2))}',
      /* —— 滚动入场（只动 opacity/transform，合成层属性 ✓）—— */
      '.qcl-reveal{opacity:0;transform:translateY(16px);transition:opacity .5s ease,transform .5s cubic-bezier(.2,.8,.2,1)}',
      '.qcl-reveal.qcl-in{opacity:1;transform:none}',
      /* —— 锚点跳转高亮 —— */
      '.qcl-flash{animation:qclFlash .9s ease}',
      '@keyframes qclFlash{0%{background:color-mix(in srgb,var(--accent) 20%,transparent)}100%{background:transparent}}',
      /* —— 无障碍：用户要求减少动效时全关 —— */
      '@media (prefers-reduced-motion: reduce){*{animation:none !important;transition:none !important}.qcl-reveal{opacity:1;transform:none}}'
    ].join('\n');
    var st = document.createElement('style');
    st.id = STYLE_ID;
    st.textContent = css;
    (document.head || document.documentElement).appendChild(st);
  }

  // ---------- 主题 ----------

  function systemDark() {
    return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
  }

  function apply(mode) {
    // mode: 'light' | 'dark' | 'auto'
    if (mode === 'auto') {
      root.removeAttribute('data-theme');
    } else {
      root.setAttribute('data-theme', mode);
    }
    updateButton(mode);
  }

  function current() {
    try {
      var v = localStorage.getItem(KEY);
      return v === 'light' || v === 'dark' ? v : 'auto';
    } catch (e) {
      return 'auto';
    }
  }

  var btn;

  function label(mode) {
    if (mode === 'light') return '☀ 浅色';
    if (mode === 'dark') return '☾ 深色';
    return '◐ 跟随系统';
  }

  function updateButton(mode) {
    if (btn) {
      btn.textContent = label(mode);
      btn.setAttribute('title', '当前：' + label(mode) + '（点击切换）');
    }
  }

  function cycle() {
    var order = ['auto', 'light', 'dark'];
    var now = current();
    var next = order[(order.indexOf(now) + 1) % order.length];
    try {
      if (next === 'auto') {
        localStorage.removeItem(KEY);
      } else {
        localStorage.setItem(KEY, next);
      }
    } catch (e) { /* 隐私模式下写不了 —— 不影响本次生效 */ }
    apply(next);
    // 切换时给个轻微反馈 ✓
    if (btn) {
      btn.style.transform = 'scale(.9)';
      setTimeout(function () { btn.style.transform = ''; }, 140);
    }
  }

  function makeButton() {
    btn = document.createElement('button');
    btn.id = 'qcl-theme-btn';
    btn.type = 'button';
    btn.setAttribute('aria-label', '切换浅色 / 深色');
    btn.addEventListener('click', cycle);
    document.body.appendChild(btn);
    updateButton(current());
  }

  // ---------- 动效 ----------

  /** 给所有卡片 / 区块加「滚动入场」：进入视口时淡入上移 ✓ */
  function installReveal() {
    var targets = document.querySelectorAll('.card, .box, section > .wrap, main > h2, .phone');
    if (!targets.length) return;

    // 不支持 IntersectionObserver 就直接显示（不能因为动效把内容藏了 ✗）
    if (!('IntersectionObserver' in window)) {
      for (var i = 0; i < targets.length; i++) targets[i].classList.add('qcl-in');
      return;
    }

    for (var j = 0; j < targets.length; j++) {
      targets[j].classList.add('qcl-reveal');
      // 首屏的直接显示，别让用户等 ✓
      var r = targets[j].getBoundingClientRect();
      if (r.top < window.innerHeight * 0.92) {
        targets[j].classList.add('qcl-in');
        targets[j].style.transitionDelay = (j % 6) * 55 + 'ms';
      }
    }

    var io = new IntersectionObserver(function (es) {
      es.forEach(function (e) {
        if (e.isIntersecting) {
          e.target.classList.add('qcl-in');
          io.unobserve(e.target);
        }
      });
    }, { threshold: 0.08, rootMargin: '0px 0px -40px 0px' });

    var rest = document.querySelectorAll('.qcl-reveal:not(.qcl-in)');
    for (var k = 0; k < rest.length; k++) io.observe(rest[k]);
  }

  /** 顶栏：滚动超过 8px 加一层阴影 + 压缩高度 ✓ */
  function installNav() {
    var nav = document.querySelector('nav');
    if (!nav) return;
    function onScroll() {
      if (window.scrollY > 8) nav.classList.add('qcl-scrolled');
      else nav.classList.remove('qcl-scrolled');
    }
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
  }

  /** 按钮：按下时轻微回弹 ✓（只动 transform ✓） */
  function installPress() {
    var els = document.querySelectorAll('.btn, .card');
    for (var i = 0; i < els.length; i++) {
      (function (el) {
        el.addEventListener('pointerdown', function () { el.style.transform = 'scale(.985)'; });
        el.addEventListener('pointerup', function () { el.style.transform = ''; });
        el.addEventListener('pointercancel', function () { el.style.transform = ''; });
        el.addEventListener('pointerleave', function () { el.style.transform = ''; });
      })(els[i]);
    }
  }

  /** 锚点跳转平滑 + 高亮目标区块 ✓ */
  function installAnchorFlash() {
    document.addEventListener('click', function (e) {
      var a = e.target.closest ? e.target.closest('a[href^="#"]') : null;
      if (!a) return;
      var id = a.getAttribute('href').slice(1);
      if (!id) return;
      var el = document.getElementById(id);
      if (!el) return;
      el.classList.remove('qcl-flash');
      void el.offsetWidth;          // 强制重排以重启动画 ✓
      el.classList.add('qcl-flash');
    });
  }

  // ---------- 启动 ----------

  injectStyle();             // 先把样式注入，减少切换闪烁 ✓
  apply(current());          // 尽早应用，减少闪烁 ✓

  // 系统主题变了且用户没手动选过 → 跟着变 ✓
  if (window.matchMedia) {
    try {
      window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', function () {
        if (current() === 'auto') apply('auto');
      });
    } catch (e) { /* 老浏览器忽略 */ }
  }

  function boot() {
    makeButton();
    installReveal();
    installNav();
    installPress();
    installAnchorFlash();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
