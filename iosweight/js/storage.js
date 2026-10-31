export const SLOTS = ['早餐前', '早餐后', '午餐前', '午餐后', '晚餐前', '晚餐后', '睡前'];
export const SLOT_COLORS = ['#46E0A8', '#7BC8FF', '#FFD27A', '#FFA26B', '#C6A8FF', '#FF8FA3', '#B4F36F'];
export const SLOT_COUNT = 7;

const RECORDS_KEY = 'weightdiary.records.v2';
const PREFS_KEY = 'weightdiary.prefs.v2';

/* ---------------- 日期工具（全部按本地时区，避免 UTC 偏移） ---------------- */

export function toISO(d) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

export function parseISO(iso) {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d);
}

export function todayISO() {
  return toISO(new Date());
}

export function addDays(iso, n) {
  const d = parseISO(iso);
  d.setDate(d.getDate() + n);
  return toISO(d);
}

export function diffDays(fromISO, toISOValue) {
  const a = parseISO(fromISO);
  const b = parseISO(toISOValue);
  return Math.round((b - a) / 86400000);
}

export function mondayOf(iso) {
  const d = parseISO(iso);
  const shift = (d.getDay() + 6) % 7;
  return addDays(iso, -shift);
}

const WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];

export function weekdayCN(iso) {
  return WEEKDAYS[parseISO(iso).getDay()];
}

export function fmtMD(iso) {
  const d = parseISO(iso);
  return `${d.getMonth() + 1}/${d.getDate()}`;
}

export function fmtDateCN(iso) {
  const d = parseISO(iso);
  return `${d.getMonth() + 1}月${d.getDate()}日`;
}

export function fmtFullCN(iso) {
  const d = parseISO(iso);
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 · ${weekdayCN(iso)}`;
}

export function monthLabel(month) {
  const [y, m] = month.split('-').map(Number);
  return `${y}年${m}月`;
}

export function monthOf(iso) {
  return iso.slice(0, 7);
}

export function daysInMonth(month) {
  const [y, m] = month.split('-').map(Number);
  return new Date(y, m, 0).getDate();
}

export function addMonths(month, n) {
  const [y, m] = month.split('-').map(Number);
  const d = new Date(y, m - 1 + n, 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

/* ---------------- 数值工具 ---------------- */

export function round1(v) {
  return Math.round(v * 10) / 10;
}

export function fmtKg(v) {
  return v == null || Number.isNaN(v) ? '—' : v.toFixed(1);
}

export function fmtDelta(v, digits = 1) {
  if (v == null || Number.isNaN(v)) return '—';
  const s = Math.abs(v).toFixed(digits);
  if (Math.abs(v) < 0.05) return '±0.0';
  return `${v > 0 ? '+' : '−'}${s}`;
}

export function clamp(v, min, max) {
  return Math.min(max, Math.max(min, v));
}

/* ---------------- 数据存储 ---------------- */

function normalize(raw) {
  const list = Array.isArray(raw) ? raw : (raw && Array.isArray(raw.records) ? raw.records : []);
  const map = new Map();
  for (const item of list) {
    if (!item) continue;
    const date = String(item.date || '');
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) continue;
    const parsed = parseISO(date);
    if (Number.isNaN(parsed.getTime())) continue;
    let slot = Number(item.slot);
    if (!Number.isInteger(slot) || slot < 0 || slot >= SLOT_COUNT) slot = 0;
    const weight = Number(item.weight);
    if (!Number.isFinite(weight) || weight < 20 || weight > 300) continue;
    map.set(`${date}|${slot}`, { date, slot, weight: round1(weight) });
  }
  const records = [...map.values()].sort((a, b) =>
    a.date === b.date ? a.slot - b.slot : (a.date < b.date ? -1 : 1));
  return { version: 2, app: 'weight-diary', records };
}

export class WeightStore {
  constructor() {
    this.data = this.load();
  }

  load() {
    try {
      const raw = localStorage.getItem(RECORDS_KEY);
      if (!raw) return normalize(null);
      return normalize(JSON.parse(raw));
    } catch (e) {
      console.warn('读取本地数据失败', e);
      return normalize(null);
    }
  }

  save() {
    try {
      localStorage.setItem(RECORDS_KEY, JSON.stringify(this.data));
    } catch (e) {
      console.warn('写入本地数据失败', e);
    }
  }

  all() {
    return this.data.records;
  }

  dates() {
    const set = new Set();
    for (const r of this.data.records) set.add(r.date);
    return [...set].sort();
  }

  day(iso) {
    return this.data.records.filter((r) => r.date === iso);
  }

  get(iso, slot) {
    return this.data.records.find((r) => r.date === iso && r.slot === slot) || null;
  }

  upsert(iso, slot, weight) {
    const s = clamp(Number(slot), 0, SLOT_COUNT - 1);
    const w = round1(clamp(Number(weight), 20, 300));
    const index = this.data.records.findIndex((r) => r.date === iso && r.slot === s);
    if (index >= 0) {
      this.data.records[index].weight = w;
    } else {
      this.data.records.push({ date: iso, slot: s, weight: w });
      this.data.records.sort((a, b) =>
        a.date === b.date ? a.slot - b.slot : (a.date < b.date ? -1 : 1));
    }
    this.save();
  }

  remove(iso, slot) {
    this.data.records = this.data.records.filter((r) => !(r.date === iso && r.slot === slot));
    this.save();
  }

  latest() {
    const all = this.data.records;
    return all.length ? all[all.length - 1] : null;
  }

  latestDate() {
    const dates = this.dates();
    return dates.length ? dates[dates.length - 1] : null;
  }

  previousDate(iso) {
    const dates = this.dates();
    for (let i = dates.length - 1; i >= 0; i--) {
      if (dates[i] < iso) return dates[i];
    }
    return null;
  }

  previousSameSlot(iso, slot) {
    const dates = this.dates();
    for (let i = dates.length - 1; i >= 0; i--) {
      if (dates[i] < iso && this.get(dates[i], slot)) return dates[i];
    }
    return null;
  }

  count() {
    return this.data.records.length;
  }

  exportObject() {
    return {
      version: 2,
      app: 'weight-diary',
      exportedAt: new Date().toISOString(),
      records: this.data.records.map((r) => ({ date: r.date, slot: r.slot, weight: r.weight })),
    };
  }

  importJSON(text) {
    const raw = JSON.parse(text);
    const incoming = normalize(raw);
    const map = new Map(this.data.records.map((r) => [`${r.date}|${r.slot}`, r]));
    for (const r of incoming.records) map.set(`${r.date}|${r.slot}`, r);
    this.data = {
      version: 2,
      app: 'weight-diary',
      records: [...map.values()].sort((a, b) =>
        a.date === b.date ? a.slot - b.slot : (a.date < b.date ? -1 : 1)),
    };
    this.save();
    return incoming.records.length;
  }

  clear() {
    this.data = normalize(null);
    this.save();
  }
}

export const store = new WeightStore();

/* ---------------- 偏好设置 ---------------- */

export class Prefs {
  constructor() {
    this.height = null;
    this.target = null;
    this.load();
  }

  load() {
    try {
      const raw = localStorage.getItem(PREFS_KEY);
      if (!raw) return;
      const data = JSON.parse(raw);
      this.height = Number(data.height) || null;
      this.target = Number(data.target) || null;
    } catch (e) {
      console.warn('读取设置失败', e);
    }
  }

  save() {
    try {
      localStorage.setItem(PREFS_KEY, JSON.stringify({ height: this.height, target: this.target }));
    } catch (e) {
      console.warn('写入设置失败', e);
    }
  }

  setHeight(value) {
    this.height = value ? clamp(Number(value), 100, 250) : null;
    this.save();
  }

  setTarget(value) {
    this.target = value ? clamp(Number(value), 20, 300) : null;
    this.save();
  }
}

export const prefs = new Prefs();
