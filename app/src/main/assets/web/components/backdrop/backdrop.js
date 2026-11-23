// The moving things in the sign-in background, drawn on one canvas: cherry-blossom petals (anime), twinkling stars
// (movies), blinking pixels (games) and now and then a shooting star. It never touches the network or any account data.
(function () {
  "use strict";
  const canvas = document.getElementById("backdrop");
  if (!canvas || !canvas.getContext) return;
  const ctx = canvas.getContext("2d");
  const still = !!(window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches);

  const PETAL = ["#ffb3d1", "#ff8fbd", "#ffd0e4"];
  const STAR = ["#ffffff", "#ffd27a", "#5ee0ff", "#ff7bb5"];
  const PIXEL = ["#5cff9d", "#5ee0ff", "#ff7bb5", "#ffd27a"];
  const TAU = Math.PI * 2;

  let w = 0;
  let h = 0;
  let dpr = 1;
  let parts = [];
  let last = 0;
  let clock = 0;
  let shoot = null;
  let shootIn = 3;
  const pointer = { x: 0, y: 0, tx: 0, ty: 0 };

  const rand = (a, b) => a + Math.random() * (b - a);
  const pick = (list) => list[Math.floor(Math.random() * list.length)];

  function make(kind, scatter) {
    const depth = rand(0.4, 1); // far things are smaller, slower and shift less with the mouse
    const p = {
      kind, depth, phase: rand(0, TAU), rot: rand(0, TAU), spin: rand(-1.4, 1.4),
      x: rand(-40, w + 40), y: scatter ? rand(0, h) : (kind === "petal" ? -20 : h + 20), alpha: rand(0.5, 0.95),
    };
    if (kind === "petal") {
      p.size = rand(5, 11) * depth; p.vy = rand(20, 44) * depth; p.vx = rand(8, 24) * depth;
      p.sway = rand(16, 38); p.color = pick(PETAL);
    } else if (kind === "star") {
      p.size = rand(2.5, 6) * depth; p.vy = -rand(5, 15) * depth; p.rate = rand(1.2, 3); p.color = pick(STAR);
    } else {
      p.size = Math.round(rand(3, 5)); p.vy = -rand(14, 30) * depth; p.rate = rand(1.5, 4); p.color = pick(PIXEL);
    }
    return p;
  }

  function build() {
    const total = Math.max(24, Math.min(80, Math.round((w * h) / 22000)));
    parts = [];
    for (let i = 0; i < total; i++) {
      const roll = i / total;
      parts.push(make(roll < 0.4 ? "petal" : roll < 0.75 ? "star" : "pixel", true));
    }
  }

  function resize() {
    dpr = Math.min(window.devicePixelRatio || 1, 1.5);
    w = window.innerWidth;
    h = window.innerHeight;
    canvas.width = Math.round(w * dpr);
    canvas.height = Math.round(h * dpr);
    build();
    if (still) frame(0);
  }

  function update(dt) {
    pointer.x += (pointer.tx - pointer.x) * Math.min(1, dt * 3);
    pointer.y += (pointer.ty - pointer.y) * Math.min(1, dt * 3);
    for (const p of parts) {
      p.y += p.vy * dt;
      if (p.kind === "petal") {
        p.x += (p.vx + Math.sin(clock * 0.9 + p.phase) * p.sway) * dt;
        p.rot += p.spin * dt;
        if (p.y > h + 24 || p.x > w + 40) { p.y = -20; p.x = rand(-80, w); }
      } else {
        p.x += Math.sin(clock * 0.4 + p.phase) * 6 * dt;
        if (p.y < -24) { p.y = h + 20; p.x = rand(0, w); }
      }
    }
    shootIn -= dt;
    if (!shoot && shootIn <= 0) {
      shoot = { x: rand(w * 0.1, w * 0.8), y: rand(0, h * 0.35), vx: rand(520, 760), vy: rand(240, 380), life: 0, max: rand(0.7, 1.1) };
      shootIn = rand(5, 11);
    }
    if (shoot) {
      shoot.life += dt; shoot.x += shoot.vx * dt; shoot.y += shoot.vy * dt;
      if (shoot.life > shoot.max) shoot = null;
    }
  }

  function petal(p, a) {
    const s = p.size;
    ctx.globalAlpha = a;
    ctx.fillStyle = p.color;
    ctx.rotate(p.rot);
    ctx.scale(1, 0.35 + 0.65 * Math.abs(Math.cos(clock * 1.3 + p.phase))); // the flutter
    ctx.beginPath();
    ctx.moveTo(0, -s);
    ctx.bezierCurveTo(s * 0.9, -s * 0.6, s * 0.8, s * 0.6, 0, s);
    ctx.bezierCurveTo(-s * 0.8, s * 0.6, -s * 0.9, -s * 0.6, 0, -s);
    ctx.fill();
  }

  function star(p) {
    const s = p.size;
    const a = p.alpha * (0.35 + 0.65 * Math.abs(Math.sin(clock * p.rate + p.phase))); // the twinkle
    ctx.fillStyle = p.color;
    ctx.globalAlpha = a * 0.18;
    ctx.beginPath();
    ctx.arc(0, 0, s * 2.2, 0, TAU);
    ctx.fill();
    ctx.globalAlpha = a;
    ctx.beginPath();
    ctx.moveTo(0, -s * 1.7);
    ctx.quadraticCurveTo(0, 0, s * 1.7, 0);
    ctx.quadraticCurveTo(0, 0, 0, s * 1.7);
    ctx.quadraticCurveTo(0, 0, -s * 1.7, 0);
    ctx.quadraticCurveTo(0, 0, 0, -s * 1.7);
    ctx.fill();
  }

  function pixel(p) {
    ctx.fillStyle = p.color;
    ctx.globalAlpha = Math.floor(clock * p.rate + p.phase) % 2 ? p.alpha : p.alpha * 0.3; // blinks in steps, like a game
    ctx.fillRect(-p.size / 2, -p.size / 2, p.size, p.size);
  }

  function streak() {
    if (!shoot) return;
    const k = shoot.life / shoot.max;
    const a = Math.sin(Math.PI * k);
    const m = Math.hypot(shoot.vx, shoot.vy);
    const tx = shoot.x - (shoot.vx / m) * 150;
    const ty = shoot.y - (shoot.vy / m) * 150;
    const g = ctx.createLinearGradient(shoot.x, shoot.y, tx, ty);
    g.addColorStop(0, "rgba(255,255,255," + a.toFixed(3) + ")");
    g.addColorStop(1, "rgba(94,224,255,0)");
    ctx.globalAlpha = 1;
    ctx.strokeStyle = g;
    ctx.lineWidth = 2;
    ctx.lineCap = "round";
    ctx.beginPath();
    ctx.moveTo(shoot.x, shoot.y);
    ctx.lineTo(tx, ty);
    ctx.stroke();
  }

  function draw() {
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, w, h);
    for (const p of parts) {
      const x = p.x + pointer.x * p.depth * 16;
      const y = p.y + pointer.y * p.depth * 10;
      ctx.save();
      ctx.translate(p.kind === "pixel" ? Math.round(x) : x, p.kind === "pixel" ? Math.round(y) : y);
      if (p.kind === "petal") petal(p, p.alpha);
      else if (p.kind === "star") star(p);
      else pixel(p);
      ctx.restore();
    }
    streak();
    ctx.globalAlpha = 1;
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
