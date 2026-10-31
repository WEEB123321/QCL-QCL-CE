/* ============================================================================
 * QCL-CE 站点：主题切换 / 液态玻璃氛围 / 滚动动效
 * 视觉样式在 premium.css；这里只负责行为，index / wiki / sponsor 共用。
 * ========================================================================== */
(function () {
  'use strict';

  var KEY = 'qcl-theme';
  var root = document.documentElement;
  var reduced = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  // ------------------------------------------------------------- atmosphere --
  function installAtmosphere() {
    if (document.querySelector('.qcl-atmosphere')) return;
    var layer = document.createElement('div');
    layer.className = 'qcl-atmosphere';
    layer.setAttribute('aria-hidden', 'true');
    document.body.insertBefore(layer, document.body.firstChild);
  }

  // ------------------------------------------------------------------ theme --
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

  function updateMeta(mode) {
    var dark = mode === 'dark' ||
      (mode === 'auto' && window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches);
    var meta = document.querySelector('meta[name="theme-color"]');
    if (!meta) {
      meta = document.createElement('meta');
      meta.name = 'theme-color';
      document.head.appendChild(meta);
    }
    meta.content = dark ? '#050506' : '#f2f2f4';
  }

  function apply(mode) {
    if (mode === 'auto') root.removeAttribute('data-theme');
    else root.setAttribute('data-theme', mode);
    updateMeta(mode);
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
      if (next === 'auto') localStorage.removeItem(KEY);
      else localStorage.setItem(KEY, next);
    } catch (e) { /* 隐私模式 */ }
    apply(next);
    if (btn) {
      btn.style.transform = 'scale(.94) rotate(-8deg)';
      setTimeout(function () { btn.style.transform = ''; }, 180);
    }
  }

  function makeButton() {
    btn = document.createElement('button');
    btn.id = 'qcl-theme-btn';
    btn.type = 'button';
    btn.setAttribute('aria-label', '切换浅色 / 深色 / 跟随系统');
    btn.addEventListener('click', cycle);
    document.body.appendChild(btn);
    apply(current());
  }

  // --------------------------------------------------------------- progress --
  function installProgress() {
    var bar = document.createElement('div');
    bar.id = 'qcl-progress';
    bar.setAttribute('aria-hidden', 'true');
    document.body.appendChild(bar);
    var ticking = false;
    function update() {
      var h = document.documentElement.scrollHeight - window.innerHeight;
      var p = h > 0 ? Math.min(1, Math.max(0, window.scrollY / h)) : 0;
      bar.style.transform = 'scaleX(' + p + ')';
      ticking = false;
    }
    window.addEventListener('scroll', function () {
      if (!ticking) {
        ticking = true;
        requestAnimationFrame(update);
      }
    }, { passive: true });
    window.addEventListener('resize', update, { passive: true });
    update();
  }

  // ----------------------------------------------------------------- reveal --
  function installReveal() {
    if (reduced) return;
    var selectors = [
      '.hero-kicker', 'header > h1', 'header > p', 'header > .btns', 'header > .phone',
      'section > .wrap > h2', 'section > .wrap > .lead', 'section > .wrap > .btns',
      '.card', '.box', '.qr-card', '.phone',
      'main > h1', 'main > .lead', '.layout > aside'
    ];
    var nodes = document.querySelectorAll(selectors.join(','));
    if (!nodes.length) return;
    if (!('IntersectionObserver' in window)) return;

    for (var i = 0; i < nodes.length; i++) {
      var el = nodes[i];
      var parent = el.parentNode;
      var idx = parent ? Array.prototype.indexOf.call(parent.children, el) : i;
      el.classList.add('qcl-reveal');
      el.style.transitionDelay = ((idx % 6) * 55) + 'ms';
      var r = el.getBoundingClientRect();
      if (r.top < window.innerHeight * 0.94) el.classList.add('qcl-in');
    }

    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          entry.target.classList.add('qcl-in');
          io.unobserve(entry.target);
        }
      });
    }, { threshold: 0.06, rootMargin: '0px 0px -36px 0px' });

    var rest = document.querySelectorAll('.qcl-reveal:not(.qcl-in)');
    for (var k = 0; k < rest.length; k++) io.observe(rest[k]);
  }

  // -------------------------------------------------------------------- nav --
  function installNav() {
    var nav = document.querySelector('nav');
    if (!nav) return;
    var ticking = false;
    function update() {
      nav.classList.toggle('qcl-scrolled', window.scrollY > 8);
      ticking = false;
    }
    window.addEventListener('scroll', function () {
      if (!ticking) {
        ticking = true;
        requestAnimationFrame(update);
      }
    }, { passive: true });
    update();
  }

  // ---------------------------------------------------------------- mobile nav --
  function installMobileMenu() {
    var nav = document.querySelector('nav');
    if (!nav) return;
    var wrap = nav.querySelector('.wrap');
    if (!wrap) return;
    var links = nav.querySelector('.links');
    if (!links) return;
    var scrim = document.createElement('div');
    scrim.id = 'qcl-menu-scrim';
    scrim.setAttribute('aria-hidden', 'true');
    document.body.appendChild(scrim);
    var menu = document.createElement('button');
    menu.id = 'qcl-menu-btn';
    menu.type = 'button';
    menu.setAttribute('aria-label', '打开导航菜单');
    menu.setAttribute('aria-expanded', 'false');
    menu.innerHTML = '<span></span><span></span><span></span>';
    wrap.appendChild(menu);

    function close() {
      nav.classList.remove('qcl-menu-open');
      menu.setAttribute('aria-expanded', 'false');
      scrim.classList.remove('qcl-show');
      document.body.classList.remove('qcl-menu-lock');
    }
    function toggle() {
      var open = nav.classList.toggle('qcl-menu-open');
      menu.setAttribute('aria-expanded', open ? 'true' : 'false');
      scrim.classList.toggle('qcl-show', open);
      document.body.classList.toggle('qcl-menu-lock', open);
    }
    menu.addEventListener('click', function (e) {
      e.stopPropagation();
      toggle();
    });
    links.addEventListener('click', function (e) {
      if (e.target.closest && e.target.closest('a')) close();
    });
    scrim.addEventListener('click', close);
    document.addEventListener('click', function (e) {
      if (!nav.contains(e.target) && e.target !== scrim) close();
    });
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') close();
    });
    window.addEventListener('resize', function () {
      if (window.innerWidth > 720) close();
    }, { passive: true });
  }

  // -------------------------------------------------------------- scrollspy --
  function installScrollSpy() {
    var aside = document.querySelector('aside');
    var main = document.querySelector('main');
    if (!aside || !main) return;
    var links = aside.querySelectorAll('a[href^="#"]');
    if (!links.length) return;
    var map = {};
    for (var i = 0; i < links.length; i++) {
      var id = links[i].getAttribute('href').slice(1);
      if (id) map[id] = links[i];
    }
    var headings = main.querySelectorAll('h2[id], h3[id]');
    if (!headings.length) return;
    var active = null;
    var ticking = false;
    function update() {
      var atBottom = window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 4;
      var found = null;
      for (var j = 0; j < headings.length; j++) {
        if (headings[j].getBoundingClientRect().top <= 140) found = headings[j];
        else break;
      }
      if (!found && headings.length) found = headings[0];
      if (atBottom && headings.length) found = headings[headings.length - 1];
      var link = found ? map[found.id] : null;
      if (link !== active) {
        if (active) active.classList.remove('qcl-active');
        if (link) link.classList.add('qcl-active');
        active = link;
      }
      ticking = false;
    }
    window.addEventListener('scroll', function () {
      if (!ticking) {
        ticking = true;
        requestAnimationFrame(update);
      }
    }, { passive: true });
    window.addEventListener('resize', update, { passive: true });
    update();
  }

  // ----------------------------------------------------------- cursor light --
  function installCursorLight() {
    if (reduced) return;
    if (!window.matchMedia || !window.matchMedia('(pointer: fine)').matches) return;
    var cards = document.querySelectorAll('.card, .qr-card');
    for (var i = 0; i < cards.length; i++) {
      (function (card) {
        var pending = false;
        var lastX = 0;
        var lastY = 0;
        card.addEventListener('pointerenter', function () {
          card.classList.add('qcl-lit');
        });
        card.addEventListener('pointerleave', function () {
          card.classList.remove('qcl-lit');
        });
        card.addEventListener('pointermove', function (e) {
          lastX = e.clientX;
          lastY = e.clientY;
          if (pending) return;
          pending = true;
          requestAnimationFrame(function () {
            var r = card.getBoundingClientRect();
            card.style.setProperty('--mx', (lastX - r.left) + 'px');
            card.style.setProperty('--my', (lastY - r.top) + 'px');
            pending = false;
          });
        }, { passive: true });
      })(cards[i]);
    }
  }

  // ------------------------------------------------------------- press/flash --
  function installPress() {
    if (reduced) return;
    var els = document.querySelectorAll('.btn');
    for (var i = 0; i < els.length; i++) {
      (function (el) {
        el.addEventListener('pointerdown', function () { el.style.transform = 'translateY(-1px) scale(.985)'; });
        el.addEventListener('pointerup', function () { el.style.transform = ''; });
        el.addEventListener('pointercancel', function () { el.style.transform = ''; });
        el.addEventListener('pointerleave', function () { el.style.transform = ''; });
      })(els[i]);
    }
  }

  function installAnchorFlash() {
    document.addEventListener('click', function (e) {
      var a = e.target.closest ? e.target.closest('a[href^="#"]') : null;
      if (!a) return;
      var id = a.getAttribute('href').slice(1);
      if (!id) return;
      var el = document.getElementById(id);
      if (!el) return;
      el.classList.remove('qcl-flash');
      void el.offsetWidth;
      el.classList.add('qcl-flash');
    });
  }

  // ---------------------------------------------------------------- startup --
  installAtmosphere();
  makeButton();

  if (window.matchMedia) {
    try {
      window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', function () {
        if (current() === 'auto') updateMeta('auto');
      });
    } catch (e) { /* 老浏览器忽略 */ }
  }

  function boot() {
    installProgress();
    installReveal();
    installNav();
    installMobileMenu();
    installScrollSpy();
    installCursorLight();
    installPress();
    installAnchorFlash();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
