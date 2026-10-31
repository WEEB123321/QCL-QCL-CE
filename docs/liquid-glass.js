/*
 * Liquid Glass for QCL-CE website.
 * Based on "Liquid Glass" by Archis Vaze (MIT License).
 * https://github.com/archisvaze/liquid-glass
 * See liquid-glass-LICENSE.txt for the full license notice.
 *
 * Chromium-only SVG backdrop-filter path. Other browsers keep the CSS
 * blur/saturate glass fallback from premium.css.
 */
(function () {
  'use strict';

  var supported = window.CSS && CSS.supports &&
    (CSS.supports('backdrop-filter', 'url(#qcl-lg-probe)') ||
      CSS.supports('-webkit-backdrop-filter', 'url(#qcl-lg-probe)'));
  if (!supported) return;

  var SVG_NS = 'http://www.w3.org/2000/svg';
  var uid = 0;
  var defs = null;
  var mapCache = Object.create(null);
  var records = new WeakMap();
  var SELECTOR = '.phone, .card, .box, .qr-card, .btn-ghost, aside';

  var SURFACE_FNS = {
    convex_squircle: function (x) {
      return Math.pow(1 - Math.pow(1 - x, 4), 0.25);
    },
    convex_circle: function (x) {
      return Math.sqrt(1 - (1 - x) * (1 - x));
    },
    lip: function (x) {
      var convex = Math.pow(1 - Math.pow(1 - Math.min(x * 2, 1), 4), 0.25);
      var concave = 1 - Math.sqrt(1 - (1 - x) * (1 - x)) + 0.1;
      var t = 6 * Math.pow(x, 5) - 15 * Math.pow(x, 4) + 10 * Math.pow(x, 3);
      return convex * (1 - t) + concave * t;
    }
  };

  function ensureDefs() {
    if (defs) return defs;
    var svg = document.createElementNS(SVG_NS, 'svg');
    svg.setAttribute('width', '0');
    svg.setAttribute('height', '0');
    svg.setAttribute('aria-hidden', 'true');
    svg.setAttribute('color-interpolation-filters', 'sRGB');
    svg.style.cssText = 'position:absolute;width:0;height:0;overflow:hidden;pointer-events:none';
    defs = document.createElementNS(SVG_NS, 'defs');
    svg.appendChild(defs);
    document.body.appendChild(svg);
    return defs;
  }

  function calculateProfile(thickness, bezel, heightFn, ior, samples) {
    samples = samples || 128;
    var eta = 1 / ior;
    var profile = new Float64Array(samples);
    for (var i = 0; i < samples; i++) {
      var x = i / samples;
      var y = heightFn(x);
      var dx = x < 1 ? 0.0001 : -0.0001;
      var y2 = heightFn(x + dx);
      var deriv = (y2 - y) / dx;
      var mag = Math.sqrt(deriv * deriv + 1);
      var nx = -deriv / mag;
      var ny = -1 / mag;
      var dot = ny;
      var k = 1 - eta * eta * (1 - dot * dot);
      if (k < 0) {
        profile[i] = 0;
        continue;
      }
      var sq = Math.sqrt(k);
      var rx = -(eta * dot + sq) * nx;
      var ry = eta - (eta * dot + sq) * ny;
      profile[i] = ry === 0 ? 0 : rx * ((y * bezel + thickness) / ry);
    }
    return profile;
  }

  function generateDisplacementMap(w, h, radius, bezel, profile, maxDisp) {
    var c = document.createElement('canvas');
    c.width = w;
    c.height = h;
    var ctx = c.getContext('2d');
    var img = ctx.createImageData(w, h);
    var d = img.data;
    for (var i = 0; i < d.length; i += 4) {
      d[i] = 128;
      d[i + 1] = 128;
      d[i + 2] = 0;
      d[i + 3] = 255;
    }
    var r = radius;
    var rSq = r * r;
    var r1Sq = Math.pow(r + 1, 2);
    var rBSq = Math.pow(Math.max(r - bezel, 0), 2);
    var wB = w - r * 2;
    var hB = h - r * 2;
    var S = profile.length;
    for (var y1 = 0; y1 < h; y1++) {
      for (var x1 = 0; x1 < w; x1++) {
        var x = x1 < r ? x1 - r : x1 >= w - r ? x1 - r - wB : 0;
        var y = y1 < r ? y1 - r : y1 >= h - r ? y1 - r - hB : 0;
        var dSq = x * x + y * y;
        if (dSq > r1Sq || dSq < rBSq) continue;
        var dist = Math.sqrt(dSq);
        var fromSide = r - dist;
        var op = dSq < rSq ? 1 : 1 - (dist - Math.sqrt(rSq)) / (Math.sqrt(r1Sq) - Math.sqrt(rSq));
        if (op <= 0 || dist === 0) continue;
        var cos = x / dist;
        var sin = y / dist;
        var bi = Math.min(Math.floor((fromSide / bezel) * S), S - 1);
        var disp = profile[bi] || 0;
        var dX = (-cos * disp) / maxDisp;
        var dY = (-sin * disp) / maxDisp;
        var idx = (y1 * w + x1) * 4;
        d[idx] = (128 + dX * 127 * op + 0.5) | 0;
        d[idx + 1] = (128 + dY * 127 * op + 0.5) | 0;
      }
    }
    ctx.putImageData(img, 0, 0);
    return c.toDataURL();
  }

  function generateSpecularMap(w, h, radius, bezel, angle) {
    var c = document.createElement('canvas');
    c.width = w;
    c.height = h;
    var ctx = c.getContext('2d');
    var img = ctx.createImageData(w, h);
    var d = img.data;
    d.fill(0);
    var r = radius;
    var rSq = r * r;
    var r1Sq = Math.pow(r + 1, 2);
    var rBSq = Math.pow(Math.max(r - bezel, 0), 2);
    var wB = w - r * 2;
    var hB = h - r * 2;
    var svx = Math.cos(angle);
    var svy = Math.sin(angle);
    for (var y1 = 0; y1 < h; y1++) {
      for (var x1 = 0; x1 < w; x1++) {
        var x = x1 < r ? x1 - r : x1 >= w - r ? x1 - r - wB : 0;
        var y = y1 < r ? y1 - r : y1 >= h - r ? y1 - r - hB : 0;
        var dSq = x * x + y * y;
        if (dSq > r1Sq || dSq < rBSq) continue;
        var dist = Math.sqrt(dSq);
        var fromSide = r - dist;
        var op = dSq < rSq ? 1 : 1 - (dist - Math.sqrt(rSq)) / (Math.sqrt(r1Sq) - Math.sqrt(rSq));
        if (op <= 0 || dist === 0) continue;
        var cos = x / dist;
        var sin = -y / dist;
        var dot = Math.abs(cos * svx + sin * svy);
        var edge = Math.sqrt(Math.max(0, 1 - Math.pow(1 - fromSide, 2)));
        var coeff = dot * edge;
        var col = (255 * coeff) | 0;
        var alpha = (col * coeff * op) | 0;
        var idx = (y1 * w + x1) * 4;
        d[idx] = col;
        d[idx + 1] = col;
        d[idx + 2] = col;
        d[idx + 3] = alpha;
      }
    }
    ctx.putImageData(img, 0, 0);
    return c.toDataURL();
  }

  function radiusOf(el, w, h) {
    var raw = getComputedStyle(el).borderTopLeftRadius || '0';
    var value = parseFloat(raw) || 0;
    if (raw.indexOf('%') >= 0) return Math.min(w, h) * value / 100;
    return Math.min(value, w / 2, h / 2);
  }

  function optionsFor(el) {
    if (el.classList.contains('phone')) {
      return { thickness: 54, ior: 2.0, blur: 0.55, spec: 0.5, scale: 1 };
    }
    if (el.tagName === 'ASIDE') {
      return { thickness: 30, ior: 1.6, blur: 0.45, spec: 0.36, scale: 1 };
    }
    if (el.classList.contains('box')) {
      return { thickness: 36, ior: 1.65, blur: 0.4, spec: 0.38, scale: 1 };
    }
    if (el.classList.contains('qr-card')) {
      return { thickness: 46, ior: 1.8, blur: 0.45, spec: 0.4, scale: 1 };
    }
    if (el.classList.contains('btn-ghost')) {
      return { thickness: 16, ior: 1.45, blur: 0.2, spec: 0.28, scale: 1 };
    }
    return { thickness: 46, ior: 1.8, blur: 0.38, spec: 0.45, scale: 1 };
  }

  function getMaps(w, h, radius, opts) {
    var bezel = Math.max(2, Math.min(radius - 1, Math.min(w, h) / 2 - 1));
    var key = [w, h, Math.round(radius), bezel, opts.thickness, opts.ior].join('|');
    if (mapCache[key]) return mapCache[key];

    var profile = calculateProfile(opts.thickness, bezel, SURFACE_FNS.convex_squircle, opts.ior, 128);
    var maxDisp = 0;
    for (var i = 0; i < profile.length; i++) {
      maxDisp = Math.max(maxDisp, Math.abs(profile[i]));
    }
    maxDisp = maxDisp || 1;
    var dispUrl = generateDisplacementMap(w, h, radius, bezel, profile, maxDisp);
    var specUrl = generateSpecularMap(w, h, radius, bezel * 2.5, Math.PI / 4);
    var maps = {
      dispUrl: dispUrl,
      specUrl: specUrl,
      scale: maxDisp * opts.scale
    };
    mapCache[key] = maps;
    return maps;
  }

  function fe(name, attrs) {
    var el = document.createElementNS(SVG_NS, name);
    for (var k in attrs) {
      if (Object.prototype.hasOwnProperty.call(attrs, k)) {
        el.setAttribute(k, attrs[k]);
      }
    }
    return el;
  }

  function createFilter(id, w, h, maps, opts) {
    var filter = fe('filter', {
      id: id,
      x: '0%',
      y: '0%',
      width: '100%',
      height: '100%',
      'color-interpolation-filters': 'sRGB'
    });
    filter.appendChild(fe('feGaussianBlur', {
      'in': 'SourceGraphic',
      stdDeviation: opts.blur,
      result: 'blurred_source'
    }));
    filter.appendChild(fe('feImage', {
      href: maps.dispUrl,
      x: '0',
      y: '0',
      width: w,
      height: h,
      result: 'disp_map'
    }));
    filter.appendChild(fe('feDisplacementMap', {
      'in': 'blurred_source',
      in2: 'disp_map',
      scale: maps.scale,
      xChannelSelector: 'R',
      yChannelSelector: 'G',
      result: 'displaced'
    }));
    filter.appendChild(fe('feColorMatrix', {
      'in': 'displaced',
      type: 'saturate',
      values: '1',
      result: 'displaced_sat'
    }));
    filter.appendChild(fe('feImage', {
      href: maps.specUrl,
      x: '0',
      y: '0',
      width: w,
      height: h,
      result: 'spec_layer'
    }));
    filter.appendChild(fe('feComposite', {
      'in': 'displaced_sat',
      in2: 'spec_layer',
      operator: 'in',
      result: 'spec_masked'
    }));
    var transfer = fe('feComponentTransfer', {
      'in': 'spec_layer',
      result: 'spec_faded'
    });
    transfer.appendChild(fe('feFuncA', {
      type: 'linear',
      slope: opts.spec
    }));
    filter.appendChild(transfer);
    filter.appendChild(fe('feBlend', {
      'in': 'spec_masked',
      in2: 'displaced',
      mode: 'normal',
      result: 'with_sat'
    }));
    filter.appendChild(fe('feBlend', {
      'in': 'spec_faded',
      in2: 'with_sat',
      mode: 'normal'
    }));
    return filter;
  }

  function build(el) {
    if (!el.isConnected) return;
    var w = el.offsetWidth;
    var h = el.offsetHeight;
    if (w < 60 || h < 36) return;
    var radius = radiusOf(el, w, h);
    if (radius < 6) return;

    var opts = optionsFor(el);
    var rec = records.get(el);
    var key = [w, h, Math.round(radius), opts.thickness, opts.ior].join('|');
    if (rec && rec.key === key) return;

    var maps = getMaps(w, h, Math.round(radius), opts);
    var id = 'qcl-lg-' + (++uid);
    var filter = createFilter(id, w, h, maps, opts);
    ensureDefs();
    defs.appendChild(filter);

    if (rec && rec.id) {
      var old = document.getElementById(rec.id);
      if (old && old.parentNode) old.parentNode.removeChild(old);
    }
    el.style.setProperty('--qcl-liquid-filter', 'url(#' + id + ')');
    el.classList.add('qcl-liquid');
    records.set(el, { id: id, key: key });
  }

  function scheduleBuild(el) {
    if (el.__qclLgScheduled) return;
    el.__qclLgScheduled = true;
    var done = false;
    function run() {
      if (done) return;
      done = true;
      build(el);
    }
    if (window.requestIdleCallback) {
      var handle = window.requestIdleCallback(run, { timeout: 300 });
      setTimeout(function () {
        if (!done) {
          try { window.cancelIdleCallback(handle); } catch (e) {}
          run();
        }
      }, 320);
    } else {
      setTimeout(run, 40);
    }
  }

  function install() {
    ensureDefs();
    var nodes = document.querySelectorAll(SELECTOR);
    if (!nodes.length) return;

    if (!('IntersectionObserver' in window)) {
      for (var i = 0; i < nodes.length; i++) scheduleBuild(nodes[i]);
      return;
    }

    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          scheduleBuild(entry.target);
          io.unobserve(entry.target);
        }
      });
    }, { rootMargin: '280px 0px', threshold: 0.01 });

    for (var j = 0; j < nodes.length; j++) {
      if (records.has(nodes[j])) continue;
      io.observe(nodes[j]);
      var rect = nodes[j].getBoundingClientRect();
      if (rect.top < window.innerHeight + 320 && rect.bottom > -320) {
        scheduleBuild(nodes[j]);
      }
    }

    var timer = 0;
    window.addEventListener('resize', function () {
      clearTimeout(timer);
      timer = setTimeout(function () {
        var all = document.querySelectorAll(SELECTOR);
        for (var k = 0; k < all.length; k++) build(all[k]);
      }, 260);
    }, { passive: true });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', install);
  } else {
    install();
  }
})();
