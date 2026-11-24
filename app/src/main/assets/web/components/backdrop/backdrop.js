// The sign-in background: an anime-style sky that slowly turns from a golden-pink twilight into a blue night over a
// glowing city. A comet with a split tail crosses it, meteors fall, clouds drift and the city windows twinkle.
// Everything is drawn here on one canvas (no images, no network) and it never touches any account data.
(function () {
  "use strict";
  const canvas = document.getElementById("backdrop");
  if (!canvas || !canvas.getContext) return;
  const ctx = canvas.getContext("2d");
  const still = !!(window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches);

  const TAU = Math.PI * 2;
  const PAD = 40; // the city is drawn a little wider than the screen so the mouse can shift it
  const DAY_NIGHT_SECONDS = 110;
  const COMET_SECONDS = 70;

  // Sky colours from the top of the sky down to the horizon, once for twilight and once for night.
  const SKY_DAY = [[0, [27, 27, 94]], [0.28, [59, 47, 143]], [0.5, [122, 79, 176]], [0.68, [217, 112, 159]], [0.85, [255, 178, 122]], [1, [255, 226, 168]]];
  const SKY_NIGHT = [[0, [5, 10, 34]], [0.28, [11, 26, 74]], [0.5, [21, 48, 104]], [0.68, [42, 74, 140]], [0.85, [75, 95, 168]], [1, [138, 111, 180]]];

  const clamp = (v, a, b) => Math.min(b, Math.max(a, v));
  const lerp = (a, b, t) => a + (b - a) * t;
  const mix = (a, b, t) => [lerp(a[0], b[0], t), lerp(a[1], b[1], t), lerp(a[2], b[2], t)];
  const rgb = (c, a) => "rgba(" + Math.round(c[0]) + "," + Math.round(c[1]) + "," + Math.round(c[2]) + "," + (a === undefined ? 1 : a) + ")";
  const rand = (a, b) => a + Math.random() * (b - a);

  // A small seeded random generator, so the stars, clouds and buildings are the same after the window is resized.
  function rng(seed) {
    let s = seed >>> 0;
    return function () {
      s = (s + 0x6D2B79F5) >>> 0;
      let t = s;
      t = Math.imul(t ^ (t >>> 15), t | 1);
      t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  let w = 0;
  let h = 0;
  let dpr = 1;
  let horizonY = 0;
  let cityH = 0;
  let clock = 0;
  let last = 0;
  let stars = [];
  let cloudsFar = [];
  let cloudsNear = [];
  let layers = null;
  let flicker = [];
  let tower = null;
  let meteors = [];
  let meteorIn = 1;
  let burst = 0;
  const pointer = { x: 0, y: 0, tx: 0, ty: 0 };

  function offscreen(width, height) {
    const c = document.createElement("canvas");
    c.width = Math.max(1, Math.round(width * dpr));
    c.height = Math.max(1, Math.round(height * dpr));
    const g = c.getContext("2d");
    g.scale(dpr, dpr);
    return { c: c, g: g };
  }

  // ---- sprites made once ----

  function makeDot(color) {
    const o = offscreen(64, 64);
    const g = o.g.createRadialGradient(32, 32, 0, 32, 32, 32);
    g.addColorStop(0, rgb(color, 1));
    g.addColorStop(0.35, rgb(color, 0.5));
    g.addColorStop(1, rgb(color, 0));
    o.g.fillStyle = g;
    o.g.fillRect(0, 0, 64, 64);
    return o.c;
  }

  // A fluffy cloud: lit on top, shaded underneath, with a flat soft bottom.
  function makeCloud(top, bottom, seed) {
    const W = 560;
    const H = 240;
    const o = offscreen(W, H);
    const g = o.g;
    const r = rng(seed);
    for (let i = 0; i < 36; i++) {
      const cx = W * (0.12 + 0.76 * r());
      const edge = 1 - Math.abs(cx / W - 0.5) * 2;
      const cy = H * (0.62 - 0.4 * edge * r());
      const rad = H * (0.18 + 0.28 * r()) * (0.55 + 0.45 * edge);
      const lit = clamp(1 - (cy - H * 0.2) / (H * 0.55), 0, 1);
      const col = mix(bottom, top, lit);
      const grad = g.createRadialGradient(cx, cy, 0, cx, cy, rad);
      grad.addColorStop(0, rgb(col, 0.9));
      grad.addColorStop(0.6, rgb(col, 0.45));
      grad.addColorStop(1, rgb(col, 0));
      g.fillStyle = grad;
      g.beginPath();
      g.arc(cx, cy, rad, 0, TAU);
      g.fill();
    }
    g.globalCompositeOperation = "destination-out";
    const fade = g.createLinearGradient(0, H * 0.62, 0, H);
    fade.addColorStop(0, "rgba(0,0,0,0)");
    fade.addColorStop(1, "rgba(0,0,0,1)");
    g.fillStyle = fade;
    g.fillRect(0, 0, W, H);
    return o.c;
  }

  const dots = {};
  const cloudWarm = [];
  const cloudCool = [];

  function makeSprites() {
    dots.warm = makeDot([255, 200, 170]);
    dots.white = makeDot([225, 238, 255]);
    dots.lilac = makeDot([170, 150, 255]);
    dots.violet = makeDot([130, 90, 220]);
    for (let i = 0; i < 3; i++) {
      cloudWarm.push(makeCloud([255, 226, 190], [150, 90, 170], 21 + i * 7));
      cloudCool.push(makeCloud([120, 150, 215], [24, 32, 84], 21 + i * 7));
    }
  }

  // ---- things that depend on the window size ----

  function buildStars() {
    const r = rng(3);
    const count = clamp(Math.round((w * h) / 9000), 80, 260);
    stars = [];
    for (let i = 0; i < count; i++) {
      stars.push({ x: r() * w, y: r() * horizonY * 0.95, size: r() < 0.12 ? 2 : 1, phase: r() * TAU, rate: 0.6 + r() * 2.2, warm: r() < 0.3 });
    }
  }

  function buildClouds() {
    const r = rng(5);
    cloudsFar = [];
    cloudsNear = [];
    for (let j = 0; j < 8; j++) {
      const far = j < 4;
      const dw = Math.max(360, w * 0.36) * (far ? 0.6 + r() * 0.35 : 1.1 + r() * 0.6);
      const cloud = {
        i: j % 3, dw: dw, dh: dw * 240 / 560, x: r() * (w + dw) - dw * 0.5,
        y: far ? h * (0.28 + r() * 0.22) : h * (0.5 + r() * 0.14),
        speed: far ? 5 + r() * 5 : 11 + r() * 8, a: far ? 0.55 + r() * 0.2 : 0.8 + r() * 0.15, par: far ? 6 : 14,
      };
      (far ? cloudsFar : cloudsNear).push(cloud);
    }
  }

  function skyline(g, lg, r, width, o, flickList) {
    g.fillStyle = o.body;
    let x = -10;
    while (x < width) {
      const bw = o.minW + r() * (o.maxW - o.minW);
      const bh = cityH * (o.minH + r() * (o.maxH - o.minH));
      const top = cityH - bh;
      g.fillRect(x, top, bw + 1, bh + 2);
      const extra = r();
      if (extra < 0.25) g.fillRect(x + bw * 0.4, top - 6 - r() * 10, 2, 16); // an antenna
      else if (extra < 0.45) g.fillRect(x + bw * 0.15, top - 5, bw * 0.35, 5); // a rooftop box
      for (let wy = top + 8; wy < cityH - 6; wy += 10) {
        for (let wx = x + 5; wx < x + bw - 6; wx += 8) {
          const q = r();
          if (q < o.p) {
            lg.globalAlpha = o.alpha * (0.5 + 0.5 * r());
            lg.fillStyle = r() < 0.15 ? "#bfe6ff" : "#ffd98a";
            lg.fillRect(wx, wy, 3, 5);
          } else if (flickList && q < o.p + 0.05) {
            flickList.push({ x: wx, y: wy, phase: r() * TAU, rate: 0.4 + r() * 1.2 });
          }
        }
      }
      x += bw + (r() < 0.3 ? 4 + r() * 8 : 0);
    }
    lg.globalAlpha = 1;
  }

  function buildCity() {
    const width = w + PAD * 2;
    cityH = Math.round(h * 0.32);
    const back = offscreen(width, cityH);
    const front = offscreen(width, cityH);
    const lightsBack = offscreen(width, cityH);
    const lightsFront = offscreen(width, cityH);
    const r = rng(11);
    flicker = [];
    skyline(back.g, lightsBack.g, r, width, { minW: 34, maxW: 90, minH: 0.22, maxH: 0.6, body: "rgb(24,22,60)", p: 0.22, alpha: 0.55 }, null);
    skyline(front.g, lightsFront.g, r, width, { minW: 26, maxW: 70, minH: 0.12, maxH: 0.5, body: "rgb(8,8,26)", p: 0.38, alpha: 1 }, flicker);
    // one tall tower with a blinking red light on top
    const tx = width * 0.8;
    const g = front.g;
    g.fillStyle = "rgb(8,8,26)";
    g.beginPath();
    g.moveTo(tx - 16, cityH);
    g.lineTo(tx - 10, cityH * 0.45);
    g.lineTo(tx - 6, cityH * 0.3);
    g.lineTo(tx - 3, cityH * 0.14);
    g.lineTo(tx - 1, cityH * 0.02);
    g.lineTo(tx + 1, cityH * 0.02);
    g.lineTo(tx + 3, cityH * 0.14);
    g.lineTo(tx + 6, cityH * 0.3);
    g.lineTo(tx + 10, cityH * 0.45);
    g.lineTo(tx + 16, cityH);
    g.closePath();
    g.fill();
    g.fillRect(tx - 8, cityH * 0.36, 16, 5);
    tower = { x: tx, y: cityH * 0.02 };
    layers = { back: back.c, front: front.c, lightsBack: lightsBack.c, lightsFront: lightsFront.c, width: width };
  }

  function resize() {
    dpr = Math.min(window.devicePixelRatio || 1, 1.25);
    w = window.innerWidth;
    h = window.innerHeight;
    horizonY = h * 0.78;
    canvas.width = Math.round(w * dpr);
    canvas.height = Math.round(h * dpr);
    buildStars();
    buildClouds();
    buildCity();
    if (still) frame(0);
  }

  // ---- moving and drawing ----

  // 0 is golden twilight, 1 is deep night. It eases in and out so each look lingers for a while.
  function mood() {
    if (still) return 0.12;
    const c = 0.5 - 0.5 * Math.cos((TAU * clock) / DAY_NIGHT_SECONDS);
    return c * c * (3 - 2 * c);
  }

  function update(dt) {
    pointer.x += (pointer.tx - pointer.x) * Math.min(1, dt * 3);
    pointer.y += (pointer.ty - pointer.y) * Math.min(1, dt * 3);
    cloudsFar.concat(cloudsNear).forEach((c) => {
      c.x -= c.speed * dt;
      if (c.x < -c.dw) c.x = w + rand(0, 120);
    });
    if (still) return;
    const m = mood();
    meteorIn -= dt;
    if (meteorIn <= 0) {
      if (burst > 0) {
        burst--;
        meteorIn = rand(0.08, 0.25);
      } else {
        meteorIn = rand(0.5, 1.6) / (0.5 + m);
        if (Math.random() < 0.12) burst = 6 + Math.floor(Math.random() * 5); // now and then a whole shower
      }
      spawnMeteor();
    }
    meteors = meteors.filter((s) => {
      s.t += dt;
      s.x += s.dx * s.v * dt;
      s.y += s.dy * s.v * dt;
      return s.t < s.life && s.y < horizonY;
    });
  }

  function spawnMeteor() {
    const angle = 2.05 + rand(-0.08, 0.08); // falling down and to the left
    meteors.push({
      x: rand(w * 0.1, w * 1.15), y: rand(-30, h * 0.3), dx: Math.cos(angle), dy: Math.sin(angle),
      v: rand(650, 1050), len: rand(90, 230), t: 0, life: rand(0.6, 1.4), width: rand(1, 2.2), pink: Math.random() < 0.12,
    });
  }

  function drawSky(m) {
    const g = ctx.createLinearGradient(0, 0, 0, horizonY);
    for (let i = 0; i < SKY_DAY.length; i++) g.addColorStop(SKY_DAY[i][0], rgb(mix(SKY_DAY[i][1], SKY_NIGHT[i][1], m)));
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, w, horizonY + 2);
    ctx.fillStyle = "#0a0a22";
    ctx.fillRect(0, horizonY, w, h - horizonY);
  }

  function drawStars(m) {
    const px = pointer.x * 4;
    for (let i = 0; i < stars.length; i++) {
      const s = stars[i];
      const vis = Math.pow(1 - s.y / horizonY, 0.9);
      const a = vis * (0.18 + 0.82 * m) * (0.55 + 0.45 * Math.sin(clock * s.rate + s.phase));
      if (a < 0.03) continue;
      ctx.globalAlpha = a;
      ctx.fillStyle = s.warm ? "#ffe9c8" : "#ffffff";
      ctx.fillRect(s.x + px, s.y, s.size, s.size);
      if (s.size > 1) ctx.drawImage(dots.white, s.x + px - 5, s.y - 5, 12, 12);
    }
    ctx.globalAlpha = 1;
  }

  // The comet: a bright head with a long curved tail made of soft dots, and a smaller piece travelling just behind it.
  function comet(u, scale, alpha, offX, offY, length) {
    if (alpha <= 0.01) return;
    const dirx = 0.96;
    const diry = 0.28;
    const hx = w * (0.06 + 0.88 * u) + offX + pointer.x * 8;
    const hy = h * (0.07 + 0.2 * u) + offY;
    const R = Math.max(10, w * 0.012) * scale;
    const N = 44;
    for (let k = N - 1; k >= 0; k--) {
      const s = k / N;
      const d = s * length * scale;
      const bend = s * s * length * scale * 0.22;
      const x = hx - dirx * d + diry * bend;
      const y = hy - diry * d - dirx * bend;
      const r = R * (1.5 * Math.pow(1 - s, 0.9) + 0.25);
      ctx.globalAlpha = alpha * 0.5 * Math.pow(1 - s, 1.5);
      ctx.drawImage(s < 0.25 ? dots.white : s < 0.6 ? dots.lilac : dots.violet, x - r, y - r, r * 2, r * 2);
    }
    ctx.globalAlpha = alpha;
    ctx.drawImage(dots.white, hx - R * 2.2, hy - R * 2.2, R * 4.4, R * 4.4);
    ctx.fillStyle = "#ffffff";
    ctx.beginPath();
    ctx.arc(hx, hy, Math.max(1.5, R * 0.28), 0, TAU);
    ctx.fill();
    ctx.globalAlpha = 1;
  }

  function drawComets() {
    const u = ((clock + 14) % COMET_SECONDS) / COMET_SECONDS;
    const a = clamp(Math.sin(Math.PI * u) * 2.2, 0, 1);
    comet(u, 1, a, 0, 0, w * 0.32);
    comet(clamp(u - 0.025, 0, 1), 0.45, a * 0.8, -w * 0.03, h * 0.06, w * 0.2);
  }

  function drawMeteors() {
    for (let i = 0; i < meteors.length; i++) {
      const s = meteors[i];
      const a = Math.min(1, s.t * 8) * Math.min(1, (s.life - s.t) * 4);
      const tx = s.x - s.dx * s.len;
      const ty = s.y - s.dy * s.len;
      const g = ctx.createLinearGradient(s.x, s.y, tx, ty);
      g.addColorStop(0, "rgba(255,255,255," + a.toFixed(3) + ")");
      g.addColorStop(0.25, (s.pink ? "rgba(255,150,190," : "rgba(150,210,255,") + (a * 0.7).toFixed(3) + ")");
      g.addColorStop(1, s.pink ? "rgba(255,90,160,0)" : "rgba(90,140,255,0)");
      ctx.strokeStyle = g;
      ctx.lineWidth = s.width;
      ctx.lineCap = "round";
      ctx.beginPath();
      ctx.moveTo(s.x, s.y);
      ctx.lineTo(tx, ty);
      ctx.stroke();
    }
  }

  function drawGlow(m) {
    const c = mix([255, 190, 130], [255, 110, 180], m);
    const r = w * 0.6;
    ctx.save();
    ctx.translate(w * 0.5, horizonY);
    ctx.scale(1, 0.4);
    const g = ctx.createRadialGradient(0, 0, 0, 0, 0, r);
    g.addColorStop(0, rgb(c, lerp(0.55, 0.32, m)));
    g.addColorStop(1, rgb(c, 0));
    ctx.fillStyle = g;
    ctx.fillRect(-r, -r, r * 2, r * 2);
    ctx.restore();
  }

  // The thin lines of light and the little star where sky meets city.
  function drawFlare(m) {
    const x = w * (w > 900 ? 0.76 : 0.5) + pointer.x * 10;
    const y = horizonY - h * 0.02;
    const a = (0.55 - 0.25 * m) * (0.75 + 0.25 * Math.sin(clock * 1.3));
    const L = w * 0.5;
    let g = ctx.createLinearGradient(x - L, y, x + L, y);
    g.addColorStop(0, "rgba(255,110,120,0)");
    g.addColorStop(0.5, "rgba(255,170,170," + a.toFixed(3) + ")");
    g.addColorStop(1, "rgba(255,110,120,0)");
    ctx.fillStyle = g;
    ctx.fillRect(x - L, y - 1, L * 2, 2);
    g = ctx.createLinearGradient(x, y - h * 0.5, x, y + h * 0.2);
    g.addColorStop(0, "rgba(255,120,150,0)");
    g.addColorStop(0.7, "rgba(255,140,160," + (a * 0.7).toFixed(3) + ")");
    g.addColorStop(1, "rgba(255,120,150,0)");
    ctx.fillStyle = g;
    ctx.fillRect(x - 0.75, y - h * 0.5, 1.5, h * 0.7);
    ctx.globalAlpha = a;
    ctx.drawImage(dots.warm, x - 90, y - 90, 180, 180);
    ctx.strokeStyle = "rgba(255,235,220,0.9)";
    ctx.lineWidth = 1;
    for (let i = 0; i < 4; i++) {
      const ang = (i * Math.PI) / 4;
      const len = i % 2 ? 40 : 70;
      ctx.beginPath();
      ctx.moveTo(x - Math.cos(ang) * len, y - Math.sin(ang) * len);
      ctx.lineTo(x + Math.cos(ang) * len, y + Math.sin(ang) * len);
      ctx.stroke();
    }
    ctx.globalAlpha = 1;
  }

  function drawClouds(list, m) {
    for (let i = 0; i < list.length; i++) {
      const c = list[i];
      const x = c.x + pointer.x * c.par;
      if (1 - m > 0.01) {
        ctx.globalAlpha = c.a * (1 - m);
        ctx.drawImage(cloudWarm[c.i], x, c.y, c.dw, c.dh);
      }
      if (m > 0.01) {
        ctx.globalAlpha = c.a * m;
        ctx.drawImage(cloudCool[c.i], x, c.y, c.dw, c.dh);
      }
    }
    ctx.globalAlpha = 1;
  }

  function drawCity(m, front) {
    const x = -PAD + pointer.x * 6;
    const y = h - cityH;
    ctx.globalAlpha = front ? 1 : 0.85;
    ctx.drawImage(front ? layers.front : layers.back, x, y, layers.width, cityH);
    // the windows glow more as the sky darkens
    ctx.globalAlpha = (front ? 0.35 : 0.3) + 0.65 * m;
    ctx.drawImage(front ? layers.lightsFront : layers.lightsBack, x, y, layers.width, cityH);
    if (front) {
      ctx.fillStyle = "#ffd98a";
      for (let i = 0; i < flicker.length; i++) {
        const f = flicker[i];
        const on = Math.sin(clock * f.rate + f.phase);
        if (on < 0) continue;
        ctx.globalAlpha = (0.3 + 0.7 * m) * on;
        ctx.fillRect(x + f.x, y + f.y, 3, 5);
      }
      ctx.globalAlpha = 0.4 + 0.6 * (Math.sin(clock * 3) > 0 ? 1 : 0);
      ctx.fillStyle = "#ff4d5e";
      ctx.fillRect(x + tower.x - 1.5, y + tower.y - 2, 3, 3);
    }
    ctx.globalAlpha = 1;
  }

  function draw() {
    const m = mood();
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    drawSky(m);
    drawStars(m);
    drawComets();
    drawMeteors();
    drawGlow(m);
    drawFlare(m);
    drawClouds(cloudsFar, m);
    drawCity(m, false);
    drawClouds(cloudsNear, m);
    drawCity(m, true);
  }

  function frame(dt) {
    clock += dt;
    update(dt);
    draw();
  }

  function loop(now) {
    const dt = Math.min(0.05, (now - last) / 1000 || 0);
    last = now;
    frame(dt);
    requestAnimationFrame(loop);
  }

  makeSprites();
  if (still) clock = 25; // a calm picture with the comet in view
  window.addEventListener("resize", resize);
  if (!still) {
    window.addEventListener("pointermove", (e) => {
      pointer.tx = (e.clientX / (w || 1) - 0.5) * 2;
      pointer.ty = (e.clientY / (h || 1) - 0.5) * 2;
    }, { passive: true });
  }
  resize();
  if (!still) requestAnimationFrame(loop);
})();
