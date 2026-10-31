import {
  SLOTS, SLOT_COLORS, SLOT_COUNT, addDays, addMonths, diffDays, fmtDateCN, fmtDelta, fmtFullCN, fmtKg,
  fmtMD, monthLabel, monthOf, prefs, store, todayISO, weekdayCN,
} from './storage.js';
import {
  bmi, buildInsights, completion, dailyEntries, goal, intradayRange, latestEntry, mealDeltas,
  previousEntry, rangeEntries, regression, slotAverages, streak, summary, totalChange,
  volatility, weekAverage, weekBars, weekChange,
} from './stats.js';
import {
  DailyChart, SixChart, drawSparkline, drawWeeklyBars, renderHeatmap, renderSlotAverages,
} from './charts.js?v=1.4';

const $ = (id) => document.getElementById(id);

const GEAR_SVG = `<svg viewBox="0 0 24 24" width="21" height="21" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round">
  <circle cx="12" cy="12" r="3.2"></circle>
  <path d="M12 2.8v2.6M12 18.6v2.6M2.8 12h2.6M18.6 12h2.6M5.5 5.5l1.9 1.9M16.6 16.6l1.9 1.9M18.5 5.5l-1.9 1.9M7.4 16.6l-1.9 1.9"></path>
</svg>`;
const BACK_SVG = `<svg viewBox="0 0 24 24" width="21" height="21" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
  <path d="M14.5 5.5 8 12l6.5 6.5"></path>
</svg>`;

const state = {
  page: 'today',
  lastTab: 'today',
  trendRange: 30,
  trendMode: 'six',
  visibleSlots: new Set([0, 1, 2, 3, 4, 5, 6]),
  month: monthOf(todayISO()),
  heatMonth: monthOf(todayISO()),
  openDates: new Set(),
  modal: null,
  chartMode: null,
  chart: null,
};

let toastTimer = null;

/* -------------------- 通用 -------------------- */

function signed(v, digits = 1) {
  if (v == null || Number.isNaN(v)) return '—';
  if (Math.abs(v) < Math.pow(10, -digits) / 2) return digits === 1 ? '±0.0' : `±${(0).toFixed(digits)}`;
  return `${v > 0 ? '+' : '−'}${Math.abs(v).toFixed(digits)}`;
}

function toast(message) {
  const el = $('toast');
  el.textContent = message;
  el.classList.remove('hidden');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.add('hidden'), 2000);
}

function deltaClass(v) {
  if (v == null || Math.abs(v) < 0.05) return '';
  return v < 0 ? 'down' : 'up';
}

function setDeltaPill(el, text, value) {
  el.textContent = text;
  el.classList.remove('down', 'up');
  const cls = deltaClass(value);
  if (cls) el.classList.add(cls);
}

function statHTML(label, value, unit = '', cls = '') {
  return `<div class="stat"><div class="stat-label">${label}</div>
    <div class="stat-value ${cls}">${value}${unit ? `<small>${unit}</small>` : ''}</div></div>`;
}

/* -------------------- 页面切换 -------------------- */

function updateHeader() {
  const settings = state.page === 'settings';
  $('headerTitle').textContent = settings ? '设置' : '体重日记';
  $('headerDate').textContent = settings
    ? '偏好与数据'
    : `${fmtDateCN(todayISO())} · ${weekdayCN(todayISO())}`;
  $('btnSettings').innerHTML = settings ? BACK_SVG : GEAR_SVG;
  $('btnSettings').setAttribute('aria-label', settings ? '返回' : '设置');
}

function showPage(page) {
  if (page !== 'settings') state.lastTab = page;
  state.page = page;
  document.querySelectorAll('.page').forEach((el) => {
    el.classList.toggle('active', el.id === `page-${page}`);
  });
  document.querySelectorAll('.tab').forEach((el) => {
    el.classList.toggle('active', el.dataset.page === page);
  });
  updateHeader();
  renderPage(page);
}

function renderPage(page) {
  if (page === 'today') renderToday();
  else if (page === 'trend') renderTrend();
  else if (page === 'analysis') renderAnalysis();
  else if (page === 'history') renderHistory();
  else if (page === 'settings') renderSettings();
}

function refresh() {
  renderToday();
  if (state.page !== 'today') renderPage(state.page);
}

/* -------------------- 今日 -------------------- */

function nextSlotFor(date) {
  const recs = store.day(date);
  if (!recs.length) return 0;
  const filled = new Set(recs.map((r) => r.slot));
  const last = Math.max(...recs.map((r) => r.slot));
  for (let i = last + 1; i < SLOT_COUNT; i++) if (!filled.has(i)) return i;
  for (let i = 0; i < SLOT_COUNT; i++) if (!filled.has(i)) return i;
  return -1;
}

function renderToday() {
  const today = todayISO();
  const entries = dailyEntries(store.all());
  const todayEntry = entries.find((e) => e.date === today) || null;
  const latest = latestEntry(entries);
  const display = todayEntry || latest;

  $('heroLabel').textContent = todayEntry ? '今日日均' : (display ? `上次日均 · ${fmtMD(display.date)}` : '今日日均');
  $('heroValue').textContent = display ? fmtKg(display.avg) : '—';
  $('heroCount').textContent = `${todayEntry ? todayEntry.count : 0} / ${SLOT_COUNT}`;

  if (display) {
    const prev = previousEntry(entries, display.date);
    if (prev) {
      const delta = display.avg - prev.avg;
      setDeltaPill($('heroDelta'), `较上次 ${fmtDelta(delta)} kg`, delta);
    } else {
      setDeltaPill($('heroDelta'), '首次记录', null);
    }
  } else {
    setDeltaPill($('heroDelta'), '暂无记录', null);
  }

  const g = goal(entries, prefs.target);
  const ring = $('goalRing');
  if (prefs.target) {
    if (!g) {
      $('heroTarget').textContent = `目标 ${prefs.target.toFixed(1)} kg`;
      ring.style.setProperty('--p', 0);
      $('ringPct').textContent = '—';
    } else if (g.achieved) {
      $('heroTarget').textContent = `目标 ${prefs.target.toFixed(1)} kg · 已达成`;
      ring.style.setProperty('--p', 100);
      $('ringPct').textContent = '100%';
    } else {
      $('heroTarget').textContent = `目标 ${prefs.target.toFixed(1)} kg · 还差 ${g.remaining.toFixed(1)} kg`;
      ring.style.setProperty('--p', Math.round(g.progress));
      $('ringPct').textContent = `${Math.round(g.progress)}%`;
    }
  } else {
    $('heroTarget').textContent = '未设置目标 · 可在设置中填写';
    ring.style.setProperty('--p', 0);
    $('ringPct').textContent = '—';
  }

  const next = nextSlotFor(today);
  const btn = $('btnRecord');
  btn.textContent = next >= 0 ? `记录 · ${SLOTS[next]}` : `今日已完成 · ${SLOT_COUNT} / ${SLOT_COUNT}`;
  btn.classList.toggle('done', next < 0);

  const grid = $('slotGrid');
  grid.innerHTML = '';
  for (let slot = 0; slot < SLOT_COUNT; slot++) {
    const rec = store.get(today, slot);
    const btnEl = document.createElement('button');
    btnEl.type = 'button';
    btnEl.className = `slot-tile${rec ? '' : ' is-empty'}${slot === next ? ' is-next' : ''}`;
    btnEl.innerHTML = `
      <span class="tile-index">第 ${slot + 1} 次</span>
      <span class="tile-name">${SLOTS[slot]}</span>
      <span class="tile-value ${rec ? '' : 'empty'}">${rec ? fmtKg(rec.weight) : '—'}</span>`;
    btnEl.addEventListener('click', () => openModal(today, slot));
    grid.appendChild(btnEl);
  }

  const week = weekAverage(entries, 0);
  const tChange = totalChange(entries);
  $('todayStats').innerHTML =
    statHTML('本周日均', week ? fmtKg(week.avg) : '—', week ? 'kg' : '') +
    statHTML('连续记录', `${streak(store.all())}`, '天') +
    statHTML('累计变化', tChange == null ? '—' : fmtDelta(tChange), tChange == null ? '' : 'kg', deltaClass(tChange));

  const last7 = entries.filter((e) => e.date >= addDays(today, -6));
  drawSparkline($('sparkChart'), last7);
  $('sparkLast').textContent = last7.length ? `最近 ${fmtKg(last7[last7.length - 1].avg)} kg` : '—';
  if (last7.length) {
    $('sparkLabels').innerHTML = `<span>${fmtMD(last7[0].date)}</span><span>${fmtMD(last7[last7.length - 1].date)}</span>`;
  } else {
    $('sparkLabels').innerHTML = '';
  }

  const insights = buildInsights(entries, store.all(), prefs.target);
  $('todayInsight').innerHTML = insights.slice(0, 2).map((t) => `<p>${t}</p>`).join('')
    || '<p>开始记录你的第一次体重吧。</p>';
}

/* -------------------- 趋势 -------------------- */

function ensureChart(mode) {
  if (state.chartMode === mode && state.chart) return state.chart;
  if (state.chart) {
    state.chart.destroy();
    state.chart = null;
  }
  state.chartMode = mode;
  state.chart = mode === 'six'
    ? new SixChart($('trendCanvas'), $('trendTooltip'))
    : new DailyChart($('trendCanvas'), $('trendTooltip'));
  return state.chart;
}

function renderLegend() {
  const legend = $('sixLegend');
  legend.innerHTML = SLOTS.map((name, slot) => {
    const on = state.visibleSlots.has(slot);
    return `<button type="button" data-slot="${slot}" class="${on ? '' : 'off'}">
      <i style="background:${SLOT_COLORS[slot]}"></i>${name}</button>`;
  }).join('');
}

function renderTrend() {
  const entries = dailyEntries(store.all());
  const filtered = rangeEntries(entries, state.trendRange);
  const isSix = state.trendMode === 'six';

  $('trendWindow').textContent = state.trendRange === 'all'
    ? `${filtered.length} 天`
    : `近 ${state.trendRange} 天`;

  document.querySelectorAll('#rangeChips .chip').forEach((el) => {
    el.classList.toggle('active', el.dataset.range === String(state.trendRange));
  });
  document.querySelectorAll('#modeSwitch button').forEach((el) => {
    el.classList.toggle('active', el.dataset.mode === state.trendMode);
  });

  $('sixLegend').classList.toggle('hidden', !isSix);
  if (isSix) renderLegend();

  const chart = ensureChart(state.trendMode);
  if (isSix) {
    chart.setData(filtered, state.visibleSlots);
    let values = 0;
    for (const slot of state.visibleSlots) {
      for (const e of filtered) if (e.slots[slot] != null) values += 1;
    }
    $('trendEmpty').classList.toggle('hidden', values > 0);
    $('trendEmpty').textContent = filtered.length ? '当前没有可显示的时段' : '还没有数据，先记录一次吧';
  } else {
    chart.setData(filtered, prefs.target);
    $('trendEmpty').classList.toggle('hidden', filtered.length > 0);
    $('trendEmpty').textContent = '还没有数据，先记录一次吧';
  }

  const s = summary(filtered);
  $('trendStats').innerHTML = s
    ? statHTML('区间最低', fmtKg(s.min), 'kg') +
      statHTML('区间最高', fmtKg(s.max), 'kg') +
      statHTML('区间平均', fmtKg(s.avg), 'kg')
    : statHTML('区间最低', '—') + statHTML('区间最高', '—') + statHTML('区间平均', '—');

  const bars = weekBars(entries, 8);
  const ok = drawWeeklyBars($('weeklyCanvas'), bars);
  $('weeklyEmpty').classList.toggle('hidden', ok);
}

/* -------------------- 分析 -------------------- */

function metric({ label, value, unit = '', note = '', cls = '' }) {
  return `<div class="metric ${cls}">
    <div class="m-label">${label}</div>
    <div class="m-value">${value}${unit ? `<small>${unit}</small>` : ''}</div>
    <div class="m-note">${note}</div>
  </div>`;
}

function renderAnalysis() {
  const today = todayISO();
  const entries = dailyEntries(store.all());
  const records = store.all();

  const wc = weekChange(entries);
  const reg = regression(entries);
  const g = goal(entries, prefs.target);
  const vol = volatility(entries);
  const intra = intradayRange(entries);
  const comp = completion(records);
  const latest = latestEntry(entries);
  const bmiValue = bmi(latest ? latest.avg : null, prefs.height);
  const meals = mealDeltas(entries);

  const cards = [];

  cards.push(metric({
    label: '周环比',
    value: wc ? signed(wc.delta) : '—',
    unit: wc ? 'kg' : '',
    note: wc ? `本周 ${wc.current.toFixed(1)} · 上周 ${wc.previous.toFixed(1)}` : '本周或上周没有足够记录',
    cls: wc ? deltaClass(wc.delta) : '',
  }));

  cards.push(metric({
    label: '趋势速度',
    value: reg ? signed(reg.weekly, 2) : '—',
    unit: reg ? 'kg/周' : '',
    note: reg ? `R² ${reg.r2.toFixed(2)} · ${reg.n} 天拟合` : '至少需要 2 天数据',
    cls: reg ? deltaClass(reg.weekly) : '',
  }));

  let goalValue = '—';
  let goalNote = '在设置中填写目标体重后启用';
  if (prefs.target) {
    if (g && g.achieved) {
      goalValue = '已达成';
      goalNote = `目标 ${prefs.target.toFixed(1)} kg`;
    } else if (g && g.eta) {
      goalValue = fmtDateCN(g.eta);
      goalNote = `还差 ${g.remaining.toFixed(1)} kg · 约 ${g.etaDays} 天`;
    } else {
      goalValue = '暂不可估';
      goalNote = g ? `还差 ${g.remaining.toFixed(1)} kg · 趋势不足` : '还没有记录';
    }
  }
  cards.push(metric({ label: '目标预计', value: goalValue, note: goalNote }));

  cards.push(metric({
    label: '日间波动',
    value: vol ? vol.sigma.toFixed(2) : '—',
    unit: vol ? 'kg' : '',
    note: vol ? `相邻两天日均差 σ · ${vol.n} 组` : '需要连续两天的记录',
  }));

  cards.push(metric({
    label: '日内极差',
    value: intra ? intra.avg.toFixed(1) : '—',
    unit: intra ? 'kg' : '',
    note: intra ? `${intra.n} 天有 2 次以上记录` : '一天至少记录 2 次后才有',
  }));

  cards.push(metric({
    label: '记录完整度',
    value: `${comp.count}`,
    unit: `/ ${comp.total}`,
    note: `近 7 天 · ${Math.round(comp.ratio * 100)}%`,
  }));

  cards.push(metric({
    label: 'BMI',
    value: bmiValue ? bmiValue.value.toFixed(1) : '—',
    unit: bmiValue ? bmiValue.label : '',
    note: bmiValue ? `身高 ${prefs.height} cm · 最新日均` : '填写身高后启用',
  }));

  const best = meals.best;
  cards.push(metric({
    label: '餐前餐后',
    value: best ? signed(best.delta) : '—',
    unit: best ? 'kg' : '',
    note: best ? `${best.name}餐后 − 餐前 · ${best.count} 天` : '需要同一餐前后都有记录',
    cls: best ? deltaClass(best.delta) : '',
  }));

  $('metricGrid').innerHTML = cards.join('');

  renderSlotAverages($('slotAvg'), slotAverages(records));

  $('heatMonth').textContent = monthLabel(state.heatMonth);
  renderHeatmap($('heatmap'), state.heatMonth, entries, today);

  const insights = buildInsights(entries, records, prefs.target);
  $('insights').innerHTML = insights.map((text) => `<div class="insight"><i></i><div>${text}</div></div>`).join('')
    || '<div class="insight"><i></i><div>记录几天后，这里会给出针对你的建议。</div></div>';
}

/* -------------------- 记录页 -------------------- */

function renderHistory() {
  const entries = dailyEntries(store.all());
  const records = store.all();
  const month = state.month;
  const inMonth = entries.filter((e) => e.date.slice(0, 7) === month);
  const monthRecords = records.filter((r) => r.date.slice(0, 7) === month);

  $('histMonth').textContent = monthLabel(month);
  if (monthRecords.length) {
    const sum = inMonth.reduce((acc, e) => acc + e.avg, 0);
    $('historySummary').textContent = `${monthRecords.length} 次 · ${inMonth.length} 天 · 平均 ${fmtKg(sum / inMonth.length)} kg`;
  } else {
    $('historySummary').textContent = '本月暂无记录';
  }

  if (!inMonth.length) {
    $('historyList').innerHTML = '<div class="empty-state">这个月还没有记录</div>';
    return;
  }

  const list = inMonth.slice().reverse();
  $('historyList').innerHTML = '';

  for (const entry of list) {
    const prev = previousEntry(entries, entry.date);
    const delta = prev ? entry.avg - prev.avg : null;
    const open = state.openDates.has(entry.date);
    const card = document.createElement('div');
    card.className = `day-card${open ? ' open' : ''}`;

    const dots = SLOTS.map((_, slot) => {
      const has = entry.slots[slot] != null;
      return `<span class="dot ${has ? 'on' : 'off'}"${has ? ` style="background:${SLOT_COLORS[slot]}"` : ''}></span>`;
    }).join('');

    const details = SLOTS.map((name, slot) => {
      const value = entry.slots[slot];
      return `<button class="detail-slot" type="button" data-date="${entry.date}" data-slot="${slot}">
        <span>第 ${slot + 1} 次 · ${name}</span>
        <b class="${value == null ? 'muted' : ''}">${value == null ? '未记录' : `${fmtKg(value)} kg`}</b>
      </button>`;
    }).join('');

    card.innerHTML = `
      <div class="day-row">
        <div class="day-date"><b>${parseInt(entry.date.slice(8, 10), 10)}</b><span>${weekdayCN(entry.date)}</span></div>
        <div class="day-main">
          <div class="day-line">
            <span class="day-avg">${fmtKg(entry.avg)}<small>kg</small></span>
            <span class="day-delta ${deltaClass(delta)}">${delta == null ? '' : `较前 ${signed(delta)}`}</span>
          </div>
          <div class="day-dots">${dots}</div>
        </div>
        <span class="day-count">${entry.count}/6</span>
        <span class="chev">›</span>
      </div>
      <div class="day-detail">
        <div class="detail-grid">${details}</div>
      </div>`;

    card.querySelector('.day-row').addEventListener('click', () => {
      if (state.openDates.has(entry.date)) state.openDates.delete(entry.date);
      else state.openDates.add(entry.date);
      renderHistory();
    });

    card.querySelectorAll('.detail-slot').forEach((btn) => {
      btn.addEventListener('click', (ev) => {
        ev.stopPropagation();
        openModal(entry.date, Number(btn.dataset.slot));
      });
    });

    $('historyList').appendChild(card);
  }
}

/* -------------------- 设置 -------------------- */

function renderSettings() {
  $('heightInput').value = prefs.height ? String(prefs.height) : '';
  $('targetInput').value = prefs.target ? prefs.target.toFixed(1) : '';
  const dates = store.dates();
  $('storageInfo').textContent = `${store.count()} 条 · ${dates.length} 天`;
}

function exportBackup() {
  const text = JSON.stringify(store.exportObject(), null, 2);
  const filename = `体重日记-备份-${todayISO()}.json`;
  const blob = new Blob([text], { type: 'application/json' });
  try {
    const file = new File([blob], filename, { type: 'application/json' });
    if (navigator.canShare && navigator.canShare({ files: [file] })) {
      navigator.share({ files: [file], title: '体重日记备份' }).catch(() => downloadBlob(blob, filename));
      return;
    }
  } catch (e) {
    // 忽略，退回下载
  }
  downloadBlob(blob, filename);
}

function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 3000);
}

/* -------------------- 录入弹窗 -------------------- */

function lastMeasurement(date, slot) {
  const day = store.day(date);
  const earlier = day.filter((r) => r.slot < slot).sort((a, b) => b.slot - a.slot)[0];
  if (earlier) return earlier;
  const any = day.filter((r) => r.slot !== slot).sort((a, b) => b.slot - a.slot)[0];
  if (any) return any;
  const latest = store.latest();
  if (latest && latest.date <= date) return latest;
  return null;
}

function openModal(date, slot) {
  state.modal = { date, slot };
  $('modalOverlay').classList.remove('hidden');
  document.body.style.overflow = 'hidden';
  renderModal();
  setTimeout(() => $('weightInput').focus(), 260);
}

function closeModal() {
  $('modalOverlay').classList.add('hidden');
  document.body.style.overflow = '';
  state.modal = null;
}

function renderModal() {
  if (!state.modal) return;
  const { date, slot } = state.modal;
  const existing = store.get(date, slot);
  const isToday = date === todayISO();
  const action = existing ? '修改' : (isToday ? '记录' : '补记');
  $('modalTitle').textContent = `${action} · 第 ${slot + 1} 次 · ${SLOTS[slot]}`;
  $('modalSub').textContent = fmtFullCN(date) + (existing ? ' · 已记录' : '');

  $('modalSlots').innerHTML = SLOTS.map((name, i) =>
    `<button type="button" data-slot="${i}" class="${i === slot ? 'active' : ''}">${i + 1} ${name}</button>`).join('');
  $('modalSlots').querySelectorAll('button').forEach((btn) => {
    btn.addEventListener('click', () => {
      const next = Number(btn.dataset.slot);
      if (next === state.modal.slot) return;
      state.modal.slot = next;
      renderModal();
    });
  });

  const prevDate = store.previousSameSlot(date, slot);
  const prevHint = $('modalPrev');
  if (prevDate) {
    const record = store.get(prevDate, slot);
    const gap = diffDays(prevDate, date);
    prevHint.textContent = `上次第 ${slot + 1} 次 ${SLOTS[slot]} ${fmtKg(record.weight)} kg · ${gap === 1 ? '昨天' : `${gap} 天前`}`;
    prevHint.classList.remove('hidden');
  } else {
    prevHint.classList.add('hidden');
  }

  const yesterday = store.get(addDays(date, -1), slot);
  const fillYesterday = $('fillYesterday');
  if (yesterday) {
    fillYesterday.textContent = `沿用昨日 ${fmtKg(yesterday.weight)}`;
    fillYesterday.classList.remove('hidden');
  } else {
    fillYesterday.classList.add('hidden');
  }

  const last = lastMeasurement(date, slot);
  const fillLast = $('fillLast');
  if (last) {
    fillLast.textContent = `沿用上次 ${fmtKg(last.weight)}`;
    fillLast.classList.remove('hidden');
  } else {
    fillLast.classList.add('hidden');
  }

  let fallback = existing ? existing.weight : null;
  if (fallback == null) {
    const earlier = store.day(date).filter((r) => r.slot < slot).sort((a, b) => b.slot - a.slot)[0];
    if (earlier) fallback = earlier.weight;
  }
  if (fallback == null && yesterday) fallback = yesterday.weight;
  if (fallback == null && last) fallback = last.weight;
  $('weightInput').value = fallback == null ? '' : fmtKg(fallback);

  $('btnDelete').classList.toggle('hidden', !existing);
}

function setInput(value) {
  $('weightInput').value = fmtKg(value);
  const el = $('weightInput');
  el.focus();
  el.setSelectionRange(el.value.length, el.value.length);
}

function step(delta) {
  const current = parseFloat($('weightInput').value.replace(',', '.'));
  const base = Number.isFinite(current) ? current : 60;
  const value = Math.max(20, Math.min(300, base + delta));
  $('weightInput').value = fmtKg(value);
}

function saveModal() {
  if (!state.modal) return;
  const value = parseFloat($('weightInput').value.replace(',', '.'));
  if (!Number.isFinite(value) || value < 20 || value > 300) {
    toast('请输入 20.0 – 300.0 kg 之间的体重');
    return;
  }
  const { date, slot } = state.modal;
  store.upsert(date, slot, value);
  toast(`已保存第 ${slot + 1} 次 ${SLOTS[slot]} ${fmtKg(value)} kg`);
  closeModal();
  refresh();
}

/* -------------------- 初始化 -------------------- */

function bindEvents() {
  document.querySelectorAll('.tab').forEach((tab) => {
    tab.addEventListener('click', () => showPage(tab.dataset.page));
  });

  $('btnSettings').addEventListener('click', () => {
    if (state.page === 'settings') showPage(state.lastTab);
    else showPage('settings');
  });

  $('btnRecord').addEventListener('click', () => {
    const next = nextSlotFor(todayISO());
    if (next >= 0) openModal(todayISO(), next);
    else toast('今日七次已全部记录，点击方格可修改');
  });

  $('rangeChips').addEventListener('click', (e) => {
    const btn = e.target.closest('.chip');
    if (!btn) return;
    const range = btn.dataset.range;
    state.trendRange = range === 'all' ? 'all' : Number(range);
    renderTrend();
  });

  $('modeSwitch').addEventListener('click', (e) => {
    const btn = e.target.closest('button');
    if (!btn || btn.dataset.mode === state.trendMode) return;
    state.trendMode = btn.dataset.mode;
    renderTrend();
  });

  $('sixLegend').addEventListener('click', (e) => {
    const btn = e.target.closest('button');
    if (!btn) return;
    const slot = Number(btn.dataset.slot);
    if (state.visibleSlots.has(slot)) {
      if (state.visibleSlots.size === 1) {
        toast('至少保留一个时段');
        return;
      }
      state.visibleSlots.delete(slot);
    } else {
      state.visibleSlots.add(slot);
    }
    renderTrend();
  });

  $('heatPrev').addEventListener('click', () => {
    state.heatMonth = addMonths(state.heatMonth, -1);
    renderAnalysis();
  });
  $('heatNext').addEventListener('click', () => {
    const nextMonth = addMonths(state.heatMonth, 1);
    if (nextMonth > monthOf(todayISO())) return;
    state.heatMonth = nextMonth;
    renderAnalysis();
  });

  $('histPrev').addEventListener('click', () => {
    state.month = addMonths(state.month, -1);
    renderHistory();
  });
  $('histNext').addEventListener('click', () => {
    const nextMonth = addMonths(state.month, 1);
    if (nextMonth > monthOf(todayISO())) return;
    state.month = nextMonth;
    renderHistory();
  });

  $('heightInput').addEventListener('change', () => {
    const raw = $('heightInput').value.trim();
    if (!raw) {
      prefs.setHeight(null);
      toast('已清除身高');
    } else {
      const value = Number(raw);
      if (!Number.isFinite(value) || value < 100 || value > 250) {
        toast('身高请输入 100 – 250 cm');
        renderSettings();
        return;
      }
      prefs.setHeight(value);
      toast(`身高已保存为 ${value} cm`);
    }
    renderSettings();
    refresh();
  });

  $('targetInput').addEventListener('change', () => {
    const raw = $('targetInput').value.trim();
    if (!raw) {
      prefs.setTarget(null);
      toast('已清除目标体重');
    } else {
      const value = Number(raw);
      if (!Number.isFinite(value) || value < 20 || value > 300) {
        toast('目标体重请输入 20.0 – 300.0 kg');
        renderSettings();
        return;
      }
      prefs.setTarget(value);
      toast(`目标体重已保存为 ${fmtKg(value)} kg`);
    }
    renderSettings();
    refresh();
  });

  $('btnExport').addEventListener('click', exportBackup);
  $('btnImport').addEventListener('click', () => $('importFile').click());
  $('importFile').addEventListener('change', async (e) => {
    const file = e.target.files && e.target.files[0];
    if (!file) return;
    try {
      const text = await file.text();
      const count = store.importJSON(text);
      toast(`已导入 ${count} 条记录`);
      refresh();
      renderSettings();
    } catch (err) {
      console.warn(err);
      toast('导入失败：文件不是有效的备份 JSON');
    } finally {
      e.target.value = '';
    }
  });

  $('btnClear').addEventListener('click', () => {
    if (!confirm('确定清空全部记录？此操作不可撤销。')) return;
    store.clear();
    toast('已清空全部记录');
    refresh();
    renderSettings();
  });

  $('modalClose').addEventListener('click', closeModal);
  $('btnCancel').addEventListener('click', closeModal);
  $('modalOverlay').addEventListener('click', (e) => {
    if (e.target === $('modalOverlay')) closeModal();
  });

  $('stepDown').addEventListener('click', () => step(-0.1));
  $('stepUp').addEventListener('click', () => step(0.1));

  $('fillYesterday').addEventListener('click', () => {
    if (!state.modal) return;
    const rec = store.get(addDays(state.modal.date, -1), state.modal.slot);
    if (rec) setInput(rec.weight);
  });
  $('fillLast').addEventListener('click', () => {
    if (!state.modal) return;
    const rec = lastMeasurement(state.modal.date, state.modal.slot);
    if (rec) setInput(rec.weight);
  });

  $('btnDelete').addEventListener('click', () => {
    if (!state.modal) return;
    const { date, slot } = state.modal;
    store.remove(date, slot);
    toast(`已删除 ${fmtDateCN(date)} 第 ${slot + 1} 次 ${SLOTS[slot]}`);
    closeModal();
    refresh();
  });

  $('btnSave').addEventListener('click', saveModal);
  $('weightInput').addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      saveModal();
    }
  });
  $('weightInput').addEventListener('input', () => {
    const el = $('weightInput');
    let value = el.value.replace(',', '.').replace(/[^0-9.]/g, '');
    const parts = value.split('.');
    if (parts.length > 2) value = `${parts[0]}.${parts.slice(1).join('')}`;
    if (el.value !== value) el.value = value;
  });

  $('iosHintClose').addEventListener('click', () => {
    localStorage.setItem('weightdiary.iosHint', '1');
    $('iosHint').classList.add('hidden');
  });
}

function maybeShowIOSHint() {
  const ua = navigator.userAgent;
  const isIOS = /iPad|iPhone|iPod/.test(ua) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
  const standalone = window.navigator.standalone === true
    || (window.matchMedia && window.matchMedia('(display-mode: standalone)').matches);
  if (isIOS && !standalone && !localStorage.getItem('weightdiary.iosHint')) {
    $('iosHint').classList.remove('hidden');
  }
}

function registerServiceWorker() {
  if (!('serviceWorker' in navigator)) return;
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('./sw.js').catch((err) => {
      console.warn('Service Worker 注册失败', err);
    });
  });
}

function init() {
  bindEvents();
  updateHeader();
  renderToday();
  maybeShowIOSHint();
  registerServiceWorker();
}

init();
