import {
  SLOTS, SLOT_COUNT, addDays, clamp, diffDays, mondayOf, todayISO, weekdayCN,
} from './storage.js?v=1.4.2';

/* 每日汇总：日均、次数、当天极值、各时段数值 */
export function dailyEntries(records) {
  const map = new Map();
  for (const r of records) {
    let entry = map.get(r.date);
    if (!entry) {
      entry = { date: r.date, count: 0, sum: 0, min: Infinity, max: -Infinity, slots: {} };
      map.set(r.date, entry);
    }
    entry.count += 1;
    entry.sum += r.weight;
    entry.min = Math.min(entry.min, r.weight);
    entry.max = Math.max(entry.max, r.weight);
    entry.slots[r.slot] = r.weight;
  }
  const list = [];
  for (const entry of map.values()) {
    list.push({
      date: entry.date,
      count: entry.count,
      avg: entry.sum / entry.count,
      min: entry.min,
      max: entry.max,
      slots: entry.slots,
    });
  }
  list.sort((a, b) => (a.date < b.date ? -1 : 1));
  return list;
}

export function dailyMap(entries) {
  return new Map(entries.map((e) => [e.date, e]));
}

export function rangeEntries(entries, range) {
  if (range === 'all') return entries.slice();
  const n = Number(range) || 30;
  const from = addDays(todayISO(), -(n - 1));
  return entries.filter((e) => e.date >= from);
}

export function summary(entries) {
  if (!entries.length) return null;
  let min = Infinity;
  let max = -Infinity;
  let sum = 0;
  for (const e of entries) {
    min = Math.min(min, e.avg);
    max = Math.max(max, e.avg);
    sum += e.avg;
  }
  return { min, max, avg: sum / entries.length, count: entries.length };
}

export function latestEntry(entries) {
  return entries.length ? entries[entries.length - 1] : null;
}

export function previousEntry(entries, date) {
  for (let i = entries.length - 1; i >= 0; i--) {
    if (entries[i].date < date) return entries[i];
  }
  return null;
}

/* 某个自然周的日均（offset 0 = 本周，-1 = 上周） */
export function weekAverage(entries, offset = 0) {
  const start = addDays(mondayOf(todayISO()), offset * 7);
  const end = addDays(start, 6);
  const list = entries.filter((e) => e.date >= start && e.date <= end);
  if (!list.length) return null;
  const sum = list.reduce((acc, e) => acc + e.avg, 0);
  return { avg: sum / list.length, count: list.length, start, end };
}

export function weekChange(entries) {
  const current = weekAverage(entries, 0);
  const previous = weekAverage(entries, -1);
  if (!current || !previous) return null;
  return { current: current.avg, previous: previous.avg, delta: current.avg - previous.avg };
}

/* 最近 N 周的日均柱状数据 */
export function weekBars(entries, weeks = 8) {
  const bars = [];
  for (let i = weeks - 1; i >= 0; i--) {
    const week = weekAverage(entries, -i);
    const start = addDays(mondayOf(todayISO()), -i * 7);
    bars.push({
      label: `${start.slice(5, 7)}/${start.slice(8, 10)}`,
      avg: week ? week.avg : null,
      count: week ? week.count : 0,
    });
  }
  return bars;
}

/* 线性回归：日均 kg/天，返回斜率、周速度与 R² */
export function regression(entries) {
  let list = entries.slice(-30);
  if (list.length < 3) list = entries.slice();
  if (list.length < 2) return null;
  const base = list[0].date;
  const xs = list.map((e) => diffDays(base, e.date));
  const ys = list.map((e) => e.avg);
  const n = list.length;
  const meanX = xs.reduce((a, b) => a + b, 0) / n;
  const meanY = ys.reduce((a, b) => a + b, 0) / n;
  let sxx = 0;
  let sxy = 0;
  for (let i = 0; i < n; i++) {
    sxx += (xs[i] - meanX) ** 2;
    sxy += (xs[i] - meanX) * (ys[i] - meanY);
  }
  if (sxx === 0) return null;
  const slope = sxy / sxx;
  let ssRes = 0;
  let ssTot = 0;
  for (let i = 0; i < n; i++) {
    const pred = meanY + slope * (xs[i] - meanX);
    ssRes += (ys[i] - pred) ** 2;
    ssTot += (ys[i] - meanY) ** 2;
  }
  const r2 = ssTot === 0 ? 1 : Math.max(0, 1 - ssRes / ssTot);
  return { slope, weekly: slope * 7, r2, n };
}

/* 每日均值相邻两天差值的样本标准差 */
export function volatility(entries) {
  const list = entries.filter((e) => e.date >= addDays(todayISO(), -29));
  const diffs = [];
  for (let i = 1; i < list.length; i++) {
    if (diffDays(list[i - 1].date, list[i].date) === 1) {
      diffs.push(list[i].avg - list[i - 1].avg);
    }
  }
  if (diffs.length < 2) return null;
  const mean = diffs.reduce((a, b) => a + b, 0) / diffs.length;
  const variance = diffs.reduce((acc, d) => acc + (d - mean) ** 2, 0) / (diffs.length - 1);
  return { sigma: Math.sqrt(variance), n: diffs.length };
}

/* 近 30 天有两次及以上记录的日期，日内极差的平均 */
export function intradayRange(entries) {
  const list = entries.filter((e) => e.date >= addDays(todayISO(), -29) && e.count >= 2);
  if (!list.length) return null;
  const avg = list.reduce((acc, e) => acc + (e.max - e.min), 0) / list.length;
  return { avg, n: list.length };
}

const MEALS = [
  { name: '早餐', pre: 0, post: 1 },
  { name: '午餐', pre: 2, post: 3 },
  { name: '晚餐', pre: 4, post: 5 },
];

/* 各餐「餐后 − 餐前」的平均差值，并返回差值最大的一餐 */
export function mealDeltas(entries) {
  const list = entries.filter((e) => e.date >= addDays(todayISO(), -29));
  const meals = MEALS.map((meal) => {
    let sum = 0;
    let n = 0;
    for (const e of list) {
      const pre = e.slots[meal.pre];
      const post = e.slots[meal.post];
      if (pre != null && post != null) {
        sum += post - pre;
        n += 1;
      }
    }
    return { ...meal, delta: n ? sum / n : null, count: n };
  });
  const valid = meals.filter((m) => m.count > 0);
  let best = null;
  for (const m of valid) {
    if (!best || Math.abs(m.delta) > Math.abs(best.delta)) best = m;
  }
  return { meals, best };
}

/* 近 30 天六个时段的均值 */
export function slotAverages(records) {
  const from = addDays(todayISO(), -29);
  return SLOTS.map((name, slot) => {
    let sum = 0;
    let n = 0;
    for (const r of records) {
      if (r.slot === slot && r.date >= from) {
        sum += r.weight;
        n += 1;
      }
    }
    return { slot, name, avg: n ? sum / n : null, count: n };
  });
}

export function completion(records) {
  const from = addDays(todayISO(), -6);
  const recent = records.filter((r) => r.date >= from);
  return { count: recent.length, total: 7 * SLOT_COUNT, ratio: recent.length / (7 * SLOT_COUNT) };
}

export function streak(records) {
  const dates = new Set(records.map((r) => r.date));
  let cursor = todayISO();
  if (!dates.has(cursor)) cursor = addDays(cursor, -1);
  let days = 0;
  while (dates.has(cursor)) {
    days += 1;
    cursor = addDays(cursor, -1);
  }
  return days;
}

export function bmi(latestAvg, heightCm) {
  if (!latestAvg || !heightCm) return null;
  const h = heightCm / 100;
  const value = latestAvg / (h * h);
  let label = '正常';
  if (value < 18.5) label = '偏瘦';
  else if (value >= 28) label = '肥胖';
  else if (value >= 24) label = '超重';
  return { value, label };
}

export function goal(entries, target) {
  if (!target || !entries.length) return null;
  const first = entries[0].avg;
  const latest = entries[entries.length - 1].avg;
  const remaining = latest - target;
  if (remaining <= 0) {
    return { achieved: true, progress: 100, remaining: 0, etaDays: 0, eta: null };
  }
  let progress = 0;
  if (first > target) {
    progress = clamp(((first - latest) / (first - target)) * 100, 0, 100);
  }
  const reg = regression(entries);
  let etaDays = null;
  let eta = null;
  if (reg && reg.slope < -0.005) {
    etaDays = Math.ceil(remaining / -reg.slope);
    if (etaDays <= 365 * 5) {
      eta = addDays(todayISO(), etaDays);
    } else {
      etaDays = null;
    }
  }
  return { achieved: false, progress, remaining, etaDays, eta };
}

export function totalChange(entries) {
  if (entries.length < 2) return null;
  return entries[entries.length - 1].avg - entries[0].avg;
}

export function weekdayShort(iso) {
  return weekdayCN(iso).replace('周', '');
}

/* 生成今日/分析页使用的建议 */
export function buildInsights(entries, records, target) {
  const out = [];
  if (entries.length < 2) {
    out.push('记录满两天后，这里会出现趋势速度、餐前餐后差值与目标预测。');
    if (entries.length === 1) {
      out.push('今天先完成一次记录，明天同一时段再测一次即可形成对比。');
    }
    return out;
  }
  const reg = regression(entries);
  if (reg) {
    const weekly = Math.abs(reg.weekly);
    if (weekly < 0.05) {
      out.push(`近 30 天日均基本持平，平均每周变化 <b>${weekly.toFixed(2)} kg</b>，当前节奏较稳定。`);
    } else {
      out.push(`近 30 天日均${reg.weekly < 0 ? '下降' : '上升'} <b>${weekly.toFixed(2)} kg/周</b>，拟合度 R² ${reg.r2.toFixed(2)}。`);
    }
  }
  const meals = mealDeltas(entries);
  if (meals.best && Math.abs(meals.best.delta) >= 0.2) {
    out.push(`${meals.best.name}后比餐前平均 <b>${meals.best.delta > 0 ? '高' : '低'} ${Math.abs(meals.best.delta).toFixed(1)} kg</b>，是三餐中波动最明显的一餐。`);
  }
  const comp = completion(records);
  if (comp.count > 0) {
    const pct = Math.round(comp.ratio * 100);
    out.push(`近 7 天已记录 <b>${comp.count} / ${comp.total}</b> 次，完整度 ${pct}%。${pct < 50 ? '优先固定早餐前与晚餐前两个锚点，更容易坚持。' : '保持当前节奏即可。'}`);
  }
  const vol = volatility(entries);
  if (vol && vol.sigma >= 0.35) {
    out.push(`日间波动 σ = <b>${vol.sigma.toFixed(2)} kg</b>，建议固定测量条件（晨起空腹、同一台秤）。`);
  }
  if (target) {
    const g = goal(entries, target);
    if (g && g.achieved) {
      out.push(`已到达目标体重 <b>${target.toFixed(1)} kg</b>，接下来以稳定维持为主。`);
    } else if (g && g.eta) {
      out.push(`按当前速度，预计 <b>${g.eta}</b> 达到 ${target.toFixed(1)} kg，还差 ${g.remaining.toFixed(1)} kg。`);
    } else if (g) {
      out.push(`距目标 ${target.toFixed(1)} kg 还差 <b>${g.remaining.toFixed(1)} kg</b>；当前趋势不足以给出可靠预测。`);
    }
  }
  return out.slice(0, 4);
}
