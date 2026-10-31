import {
  SLOTS, SLOT_COLORS, clamp, fmtDateCN, fmtKg, fmtMD, fmtFullCN, weekdayCN,
} from './storage.js';

const FONT = '-apple-system, BlinkMacSystemFont, "PingFang SC", "Noto Sans SC", sans-serif';

function setupCanvas(canvas) {
  const rect = canvas.getBoundingClientRect();
  const w = Math.max(1, Math.round(rect.width));
  const h = Math.max(1, Math.round(rect.height));
  const dpr = Math.min(window.devicePixelRatio || 1, 2.5);
  if (canvas.width !== Math.round(w * dpr) || canvas.height !== Math.round(h * dpr)) {
    canvas.width = Math.round(w * dpr);
    canvas.height = Math.round(h * dpr);
  }
  const ctx = canvas.getContext('2d');
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, w, h);
  ctx.font = `10px ${FONT}`;
  return { ctx, w, h, rect };
}

function yDomain(values) {
  let min = Math.min(...values);
  let max = Math.max(...values);
  if (!Number.isFinite(min) || !Number.isFinite(max)) return { min: 60, max: 80 };
  if (max - min < 0.6) {
    min -= 0.4;
    max += 0.4;
  } else {
    const pad = Math.max(0.2, (max - min) * 0.12);
    min -= pad;
    max += pad;
  }
  return { min, max };
}

function drawGrid(ctx, geom, domain) {
  const { left, right, top, bottom } = geom;
  ctx.save();
  ctx.strokeStyle = 'rgba(255,255,255,0.055)';
  ctx.lineWidth = 1;
  ctx.fillStyle = 'rgba(255,255,255,0.32)';
  ctx.textAlign = 'right';
  ctx.textBaseline = 'middle';
  const steps = 4;
  for (let i = 0; i <= steps; i++) {
    const y = top + ((bottom - top) / steps) * i;
    ctx.beginPath();
    ctx.moveTo(left, y);
    ctx.lineTo(right, y);
    ctx.stroke();
    const value = domain.max - ((domain.max - domain.min) / steps) * i;
    ctx.fillText(value.toFixed(1), left - 6, y);
  }
  ctx.restore();
}

function drawXLabels(ctx, geom, entries) {
  if (!entries.length) return;
  ctx.save();
  ctx.fillStyle = 'rgba(255,255,255,0.30)';
  ctx.textBaseline = 'top';
  ctx.font = `9px ${FONT}`;
  const idxs = entries.length <= 2
    ? [0, entries.length - 1]
    : [0, Math.floor((entries.length - 1) / 2), entries.length - 1];
  const seen = new Set();
  for (const idx of idxs) {
    if (seen.has(idx)) continue;
    seen.add(idx);
    const x = geom.xFor(idx);
    ctx.textAlign = idx === 0 ? 'left' : (idx === entries.length - 1 ? 'right' : 'center');
    ctx.fillText(fmtMD(entries[idx].date), clamp(x, geom.left, geom.right), geom.bottom + 7);
  }
  ctx.restore();
}

function smoothPath(ctx, pts) {
  if (!pts.length) return;
  ctx.moveTo(pts[0].x, pts[0].y);
  for (let i = 0; i < pts.length - 1; i++) {
    const p0 = pts[Math.max(0, i - 1)];
    const p1 = pts[i];
    const p2 = pts[i + 1];
    const p3 = pts[Math.min(pts.length - 1, i + 2)];
    ctx.bezierCurveTo(
      p1.x + (p2.x - p0.x) / 6, p1.y + (p2.y - p0.y) / 6,
      p2.x - (p3.x - p1.x) / 6, p2.y - (p3.y - p1.y) / 6,
      p2.x, p2.y,
    );
  }
}

function tooltipHTML(entry, visibleSlots, avgLabel = '日均') {
  const rows = visibleSlots
    .filter((s) => entry.slots[s] != null)
    .map((s) => `<div class="tt-row"><i style="background:${SLOT_COLORS[s]}"></i><span>${SLOTS[s]}</span><b>${fmtKg(entry.slots[s])}</b></div>`)
    .join('');
  const hasAny = Object.keys(entry.slots).length > 0;
  return `<div class="tt-date">${fmtDateCN(entry.date)} · ${weekdayCN(entry.date)}</div>` +
    (rows || `<div class="tt-row"><span>${hasAny ? '该时段未显示' : '当天未记录'}</span></div>`) +
    `<div class="tt-avg">${avgLabel} ${fmtKg(entry.avg)} kg</div>`;
}

function placeTooltip(tooltip, geom, canvasX, wrapWidth) {
  const half = (tooltip.offsetWidth || 120) / 2;
  const left = clamp(canvasX, half + 8, wrapWidth - half - 8);
  tooltip.style.left = `${left}px`;
  tooltip.style.top = '8px';
}

/* ---------------- 六时段平行折线 ---------------- */

export class SixChart {
  constructor(canvas, tooltip) {
    this.canvas = canvas;
    this.tooltip = tooltip;
    this.entries = [];
    this.visible = new Set([0, 1, 2, 3, 4, 5]);
    this.selected = -1;
    this.pressing = false;
    this.geom = null;

    this.onDown = (e) => {
      this.pressing = true;
      this.pick(e);
    };
    this.onMove = (e) => {
      if (this.pressing) this.pick(e);
    };
    this.onUp = () => { this.pressing = false; };
    this.onClick = (e) => this.pick(e);

    canvas.addEventListener('pointerdown', this.onDown);
    canvas.addEventListener('pointermove', this.onMove);
    canvas.addEventListener('click', this.onClick);
    window.addEventListener('pointerup', this.onUp);
    window.addEventListener('pointercancel', this.onUp);

    this.resizeObserver = new ResizeObserver(() => this.draw());
    this.resizeObserver.observe(canvas);
  }

  setData(entries, visible) {
    this.entries = entries;
    this.visible = visible;
    this.selected = -1;
    this.tooltip.classList.add('hidden');
    this.draw();
  }

  pick(event) {
    if (!this.geom || !this.entries.length) return;
    const rect = this.canvas.getBoundingClientRect();
    const x = event.clientX - rect.left;
    let best = -1;
    let bestDist = Infinity;
    for (let i = 0; i < this.entries.length; i++) {
      const px = this.geom.xFor(i);
      const d = Math.abs(px - x);
      if (d < bestDist) {
        bestDist = d;
        best = i;
      }
    }
    if (best < 0 || bestDist > 40) return;
    this.selected = best;
    this.draw();
    const entry = this.entries[best];
    this.tooltip.innerHTML = tooltipHTML(entry, [...this.visible].sort((a, b) => a - b));
    this.tooltip.classList.remove('hidden');
    placeTooltip(this.tooltip, this.geom, this.geom.xFor(best), this.geom.w);
  }

  draw() {
    const { ctx, w, h } = setupCanvas(this.canvas);
    const entries = this.entries;
    if (!entries.length) {
      this.geom = null;
      return;
    }
    const values = [];
    for (const slot of this.visible) {
      for (const e of entries) {
        if (e.slots[slot] != null) values.push(e.slots[slot]);
      }
    }
    if (!values.length) {
      this.geom = null;
      return;
    }
    const domain = yDomain(values);
    const left = 36;
    const right = w - 8;
    const top = 12;
    const bottom = h - 26;
    const geom = {
      left, right, top, bottom, w, h,
      xFor: (i) => (entries.length === 1 ? (left + right) / 2 : left + ((right - left) * i) / (entries.length - 1)),
      yFor: (v) => bottom - ((v - domain.min) / (domain.max - domain.min)) * (bottom - top),
    };
    this.geom = geom;

    drawGrid(ctx, geom, domain);
    drawXLabels(ctx, geom, entries);

    if (this.selected >= 0 && this.selected < entries.length) {
      const x = geom.xFor(this.selected);
      ctx.save();
      ctx.setLineDash([3, 3]);
      ctx.strokeStyle = 'rgba(255,255,255,0.28)';
      ctx.beginPath();
      ctx.moveTo(x, top);
      ctx.lineTo(x, bottom);
      ctx.stroke();
      ctx.restore();
    }

    for (const slot of this.visible) {
      const color = SLOT_COLORS[slot];
      const pts = entries
        .map((e, i) => ({ x: geom.xFor(i), y: e.slots[slot] == null ? null : geom.yFor(e.slots[slot]) }))
        .filter((p) => p.y != null);
      if (!pts.length) continue;

      ctx.save();
      ctx.strokeStyle = color;
      ctx.lineWidth = 2;
      ctx.lineJoin = 'round';
      ctx.lineCap = 'round';

      let started = false;
      let prevIndex = -2;
      for (let i = 0; i < entries.length; i++) {
        const value = entries[i].slots[slot];
        if (value == null) {
          started = false;
          continue;
        }
        const x = geom.xFor(i);
        const y = geom.yFor(value);
        if (!started || prevIndex !== i - 1) {
          ctx.beginPath();
          ctx.moveTo(x, y);
          started = true;
        } else {
          ctx.lineTo(x, y);
          ctx.stroke();
        }
        prevIndex = i;
      }
      if (started && prevIndex === entries.length - 1) {
        ctx.stroke();
      }

      ctx.fillStyle = color;
      for (let i = 0; i < entries.length; i++) {
        if (entries[i].slots[slot] == null) continue;
        const x = geom.xFor(i);
        const y = geom.yFor(entries[i].slots[slot]);
        ctx.beginPath();
        ctx.arc(x, y, 2.1, 0, Math.PI * 2);
        ctx.fill();
      }
      ctx.restore();
    }

    ctx.save();
    ctx.fillStyle = 'rgba(255,255,255,0.22)';
    ctx.font = `9px ${FONT}`;
    ctx.textAlign = 'left';
    ctx.textBaseline = 'top';
    ctx.fillText('kg', 2, 0);
    ctx.restore();
  }

  destroy() {
    this.canvas.removeEventListener('pointerdown', this.onDown);
    this.canvas.removeEventListener('pointermove', this.onMove);
    this.canvas.removeEventListener('click', this.onClick);
    window.removeEventListener('pointerup', this.onUp);
    window.removeEventListener('pointercancel', this.onUp);
    this.resizeObserver.disconnect();
  }
}

/* ---------------- 日均曲线 ---------------- */

export class DailyChart {
  constructor(canvas, tooltip) {
    this.canvas = canvas;
    this.tooltip = tooltip;
    this.entries = [];
    this.target = null;
    this.selected = -1;
    this.pressing = false;
    this.geom = null;
    this.ma = [];

    this.onDown = (e) => {
      this.pressing = true;
      this.pick(e);
    };
    this.onMove = (e) => {
      if (this.pressing) this.pick(e);
    };
    this.onUp = () => { this.pressing = false; };
    this.onClick = (e) => this.pick(e);
    canvas.addEventListener('pointerdown', this.onDown);
    canvas.addEventListener('pointermove', this.onMove);
    canvas.addEventListener('click', this.onClick);
    window.addEventListener('pointerup', this.onUp);
    window.addEventListener('pointercancel', this.onUp);

    this.resizeObserver = new ResizeObserver(() => this.draw());
    this.resizeObserver.observe(canvas);
  }

  setData(entries, target) {
    this.entries = entries;
    this.target = target;
    this.selected = -1;
    this.tooltip.classList.add('hidden');
    this.ma = entries.map((e, i) => {
      const from = Math.max(0, i - 6);
      let sum = 0;
      for (let j = from; j <= i; j++) sum += entries[j].avg;
      return sum / (i - from + 1);
    });
    this.draw();
  }

  pick(event) {
    if (!this.geom || !this.entries.length) return;
    const rect = this.canvas.getBoundingClientRect();
    const x = event.clientX - rect.left;
    let best = -1;
    let bestDist = Infinity;
    for (let i = 0; i < this.entries.length; i++) {
      const px = this.geom.xFor(i);
      const d = Math.abs(px - x);
      if (d < bestDist) {
        bestDist = d;
        best = i;
      }
    }
    if (best < 0 || bestDist > 40) return;
    this.selected = best;
    this.draw();
    const entry = this.entries[best];
    this.tooltip.innerHTML =
      `<div class="tt-date">${fmtDateCN(entry.date)} · ${weekdayCN(entry.date)}</div>` +
      `<div class="tt-row"><i style="background:#46E0A8"></i><span>日均</span><b>${fmtKg(entry.avg)}</b></div>` +
      `<div class="tt-avg">7 日均 ${fmtKg(this.ma[best])} kg · ${entry.count} 次</div>`;
    this.tooltip.classList.remove('hidden');
    placeTooltip(this.tooltip, this.geom, this.geom.xFor(best), this.geom.w);
  }

  draw() {
    const { ctx, w, h } = setupCanvas(this.canvas);
    const entries = this.entries;
    if (!entries.length) {
      this.geom = null;
      return;
    }
    const values = entries.map((e) => e.avg);
    if (this.target) values.push(this.target);
    const domain = yDomain(values);
    const left = 36;
    const right = w - 10;
    const top = 12;
    const bottom = h - 26;
    const geom = {
      left, right, top, bottom, w, h,
      xFor: (i) => (entries.length === 1 ? (left + right) / 2 : left + ((right - left) * i) / (entries.length - 1)),
      yFor: (v) => bottom - ((v - domain.min) / (domain.max - domain.min)) * (bottom - top),
    };
    this.geom = geom;

    drawGrid(ctx, geom, domain);
    drawXLabels(ctx, geom, entries);

    if (this.target) {
      const ty = geom.yFor(this.target);
      if (ty >= top && ty <= bottom) {
        ctx.save();
        ctx.setLineDash([4, 4]);
        ctx.strokeStyle = 'rgba(255,122,122,0.55)';
        ctx.beginPath();
        ctx.moveTo(left, ty);
        ctx.lineTo(right, ty);
        ctx.stroke();
        ctx.fillStyle = 'rgba(255,122,122,0.75)';
        ctx.font = `9px ${FONT}`;
        ctx.textAlign = 'right';
        ctx.fillText(`目标 ${this.target.toFixed(1)}`, right, ty - 4);
        ctx.restore();
      }
    }

    const pts = entries.map((e, i) => ({ x: geom.xFor(i), y: geom.yFor(e.avg) }));

    ctx.save();
    ctx.beginPath();
    smoothPath(ctx, pts);
    ctx.lineTo(pts[pts.length - 1].x, bottom);
    ctx.lineTo(pts[0].x, bottom);
    ctx.closePath();
    const grad = ctx.createLinearGradient(0, top, 0, bottom);
    grad.addColorStop(0, 'rgba(70,224,168,0.20)');
    grad.addColorStop(1, 'rgba(70,224,168,0.005)');
    ctx.fillStyle = grad;
    ctx.fill();
    ctx.restore();

    ctx.save();
    ctx.strokeStyle = '#46E0A8';
    ctx.lineWidth = 2.2;
    ctx.lineJoin = 'round';
    ctx.lineCap = 'round';
    ctx.beginPath();
    smoothPath(ctx, pts);
    ctx.stroke();
    ctx.restore();

    if (this.ma.length) {
      ctx.save();
      ctx.setLineDash([4, 4]);
      ctx.strokeStyle = 'rgba(255,255,255,0.34)';
      ctx.lineWidth = 1.2;
      ctx.beginPath();
      this.ma.forEach((v, i) => {
        const x = geom.xFor(i);
        const y = geom.yFor(v);
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      });
      ctx.stroke();
      ctx.restore();
    }

    ctx.save();
    ctx.fillStyle = '#46E0A8';
    for (const p of pts) {
      ctx.beginPath();
      ctx.arc(p.x, p.y, 2.2, 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.restore();

    if (this.selected >= 0 && this.selected < entries.length) {
      const x = geom.xFor(this.selected);
      ctx.save();
      ctx.setLineDash([3, 3]);
      ctx.strokeStyle = 'rgba(255,255,255,0.28)';
      ctx.beginPath();
      ctx.moveTo(x, top);
      ctx.lineTo(x, bottom);
      ctx.stroke();
      ctx.restore();
      const p = pts[this.selected];
      ctx.save();
      ctx.fillStyle = '#0A0E13';
      ctx.strokeStyle = '#46E0A8';
      ctx.lineWidth = 2;
      ctx.beginPath();
      ctx.arc(p.x, p.y, 4.2, 0, Math.PI * 2);
      ctx.fill();
      ctx.stroke();
      ctx.restore();
    }

    ctx.save();
    ctx.fillStyle = 'rgba(255,255,255,0.22)';
    ctx.font = `9px ${FONT}`;
    ctx.textAlign = 'left';
    ctx.textBaseline = 'top';
    ctx.fillText('kg', 2, 0);
    ctx.restore();
  }

  destroy() {
    this.canvas.removeEventListener('pointerdown', this.onDown);
    this.canvas.removeEventListener('pointermove', this.onMove);
    this.canvas.removeEventListener('click', this.onClick);
    window.removeEventListener('pointerup', this.onUp);
    window.removeEventListener('pointercancel', this.onUp);
    this.resizeObserver.disconnect();
  }
}

/* ---------------- 每周柱状图 ---------------- */

export function drawWeeklyBars(canvas, bars) {
  const { ctx, w, h } = setupCanvas(canvas);
  const valid = bars.filter((b) => b.avg != null);
  if (valid.length < 2) return false;

  const values = valid.map((b) => b.avg);
  const min = Math.min(...values);
  const max = Math.max(...values);
  const pad = Math.max(0.25, (max - min) * 0.25);
  const base = min - pad;
  const top = max + pad * 0.6;
  const left = 12;
  const right = w - 12;
  const topY = 10;
  const bottom = h - 22;
  const n = bars.length;
  const slotW = (right - left) / n;
  const barW = Math.min(26, slotW * 0.54);

  let prev = null;
  for (let i = 0; i < n; i++) {
    const bar = bars[i];
    const x = left + slotW * i + slotW / 2;
    if (bar.avg == null) {
      prev = null;
      ctx.save();
      ctx.strokeStyle = 'rgba(255,255,255,0.10)';
      ctx.setLineDash([2, 3]);
      ctx.beginPath();
      ctx.moveTo(x - barW / 2, bottom);
      ctx.lineTo(x + barW / 2, bottom);
      ctx.stroke();
      ctx.restore();
    } else {
      const y = bottom - ((bar.avg - base) / (top - base)) * (bottom - topY);
      let color = '#46E0A8';
      let endColor = 'rgba(70,224,168,0.13)';
      if (prev != null && bar.avg > prev) {
        color = '#FF7A7A';
        endColor = 'rgba(255,122,122,0.13)';
      } else if (prev != null && Math.abs(bar.avg - prev) < 0.02) {
        color = 'rgba(255,255,255,0.45)';
        endColor = 'rgba(255,255,255,0.05)';
      }
      ctx.save();
      const grad = ctx.createLinearGradient(0, y, 0, bottom);
      grad.addColorStop(0, color);
      grad.addColorStop(1, endColor);
      ctx.fillStyle = grad;
      roundRect(ctx, x - barW / 2, y, barW, bottom - y, 4);
      ctx.fill();
      ctx.fillStyle = 'rgba(255,255,255,0.55)';
      ctx.font = `9px ${FONT}`;
      ctx.textAlign = 'center';
      ctx.fillText(bar.avg.toFixed(1), x, y - 6);
      ctx.restore();
      prev = bar.avg;
    }
    ctx.save();
    ctx.fillStyle = 'rgba(255,255,255,0.28)';
    ctx.font = `9px ${FONT}`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'top';
    ctx.fillText(bar.label, x, bottom + 6);
    ctx.restore();
  }
  return true;
}

function roundRect(ctx, x, y, width, height, radius) {
  const r = Math.min(radius, width / 2, height / 2);
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + width, y, x + width, y + height, r);
  ctx.arcTo(x + width, y + height, x, y + height, r);
  ctx.arcTo(x, y + height, x, y, r);
  ctx.arcTo(x, y, x + width, y, r);
  ctx.closePath();
}

/* ---------------- 迷你趋势 ---------------- */

export function drawSparkline(container, entries) {
  if (!entries.length) {
    container.innerHTML = '<div class="empty-state" style="padding:18px">还没有足够的数据</div>';
    return;
  }
  const w = 300;
  const h = 64;
  const pad = 6;
  const values = entries.map((e) => e.avg);
  const min = Math.min(...values);
  const max = Math.max(...values);
  const range = max - min || 1;
  const pts = entries.map((e, i) => ({
    x: entries.length === 1 ? w / 2 : pad + ((w - pad * 2) * i) / (entries.length - 1),
    y: pad + (h - pad * 2) * (1 - (e.avg - min) / range),
  }));
  let d = '';
  pts.forEach((p, i) => {
    if (i === 0) d += `M ${p.x.toFixed(1)} ${p.y.toFixed(1)}`;
    else {
      const prev = pts[i - 1];
      const cx = (prev.x + p.x) / 2;
      d += ` C ${cx.toFixed(1)} ${prev.y.toFixed(1)}, ${cx.toFixed(1)} ${p.y.toFixed(1)}, ${p.x.toFixed(1)} ${p.y.toFixed(1)}`;
    }
  });
  const area = `${d} L ${pts[pts.length - 1].x.toFixed(1)} ${h} L ${pts[0].x.toFixed(1)} ${h} Z`;
  const last = pts[pts.length - 1];
  container.innerHTML = `
    <svg viewBox="0 0 ${w} ${h}" preserveAspectRatio="none" width="100%" height="100%">
      <defs>
        <linearGradient id="sparkFill" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#46E0A8" stop-opacity="0.22"/>
          <stop offset="100%" stop-color="#46E0A8" stop-opacity="0.01"/>
        </linearGradient>
      </defs>
      <path d="${area}" fill="url(#sparkFill)"></path>
      <path d="${d}" fill="none" stroke="#46E0A8" stroke-width="2" stroke-linecap="round"></path>
      <circle cx="${last.x.toFixed(1)}" cy="${last.y.toFixed(1)}" r="3.2" fill="#0A0E13" stroke="#46E0A8" stroke-width="2"></circle>
    </svg>`;
}

/* ---------------- 时段均值 ---------------- */

export function renderSlotAverages(container, averages) {
  const valid = averages.filter((s) => s.avg != null);
  if (!valid.length) {
    container.innerHTML = '<div class="empty-state">近 30 天还没有记录</div>';
    return;
  }
  const min = Math.min(...valid.map((s) => s.avg));
  const max = Math.max(...valid.map((s) => s.avg));
  const range = max - min;
  container.innerHTML = averages.map((s) => {
    if (s.avg == null) {
      return `<div class="slot-avg-row"><span class="name">${s.name}</span>
        <div class="bar"></div><span class="val muted">—</span></div>`;
    }
    const ratio = range < 0.02 ? 0.6 : (s.avg - min) / range;
    const width = 22 + ratio * 78;
    return `<div class="slot-avg-row">
      <span class="name">${s.name}</span>
      <div class="bar"><i style="width:${width.toFixed(1)}%;background:${SLOT_COLORS[s.slot]}"></i></div>
      <span class="val">${s.avg.toFixed(1)}</span>
    </div>`;
  }).join('');
}

/* ---------------- 月度热力图 ---------------- */

function heatColor(t) {
  const mint = [70, 224, 168];
  const amber = [255, 210, 122];
  const coral = [255, 122, 122];
  const lerp = (a, b, k) => Math.round(a + (b - a) * k);
  if (t < 0.5) {
    const k = t * 2;
    return mint.map((c, i) => lerp(c, amber[i], k));
  }
  const k = (t - 0.5) * 2;
  return amber.map((c, i) => lerp(c, coral[i], k));
}

export function renderHeatmap(container, month, entries, today) {
  const [y, m] = month.split('-').map(Number);
  const days = new Date(y, m, 0).getDate();
  const first = new Date(y, m - 1, 1);
  const offset = (first.getDay() + 6) % 7;
  const inMonth = entries.filter((e) => e.date.slice(0, 7) === month);
  let min = Infinity;
  let max = -Infinity;
  for (const e of inMonth) {
    min = Math.min(min, e.avg);
    max = Math.max(max, e.avg);
  }
  const range = max - min;

  let cells = '';
  for (let i = 0; i < offset; i++) cells += '<div></div>';
  for (let day = 1; day <= days; day++) {
    const iso = `${month}-${String(day).padStart(2, '0')}`;
    const entry = inMonth.find((e) => e.date === iso);
    if (!entry) {
      cells += `<div class="heat-day empty${iso === today ? ' today' : ''}">${day}</div>`;
      continue;
    }
    const t = range < 0.05 ? 0.5 : (entry.avg - min) / range;
    const [r, g, b] = heatColor(t);
    const alpha = 0.28 + 0.72 * Math.min(6, entry.count) / 6;
    const dark = alpha > 0.72;
    cells += `<div class="heat-day has-data${iso === today ? ' today' : ''}" style="background:rgba(${r},${g},${b},${alpha.toFixed(2)});color:${dark ? 'rgba(0,0,0,0.75)' : 'rgba(255,255,255,0.9)'}">${day}</div>`;
  }
  const header = `<div class="heat-weekdays">${['一', '二', '三', '四', '五', '六', '日']
    .map((d) => `<span>${d}</span>`).join('')}</div>`;
  container.innerHTML = `${header}<div class="heatmap">${cells}</div>`;
}
