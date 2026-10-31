package com.weightdiary.ui;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.weightdiary.R;
import com.weightdiary.core.Fmt;
import com.weightdiary.core.Slots;
import com.weightdiary.core.Stats;
import com.weightdiary.data.Prefs;
import com.weightdiary.data.WeightStore;
import com.weightdiary.model.Record;
import com.weightdiary.ui.view.LineChartView;
import com.weightdiary.ui.view.MultiLineChartView;
import com.weightdiary.ui.view.WeeklyBarChartView;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TrendPage extends Page {

    private final MultiLineChartView chartSlots;
    private final LineChartView chartDaily;
    private final WeeklyBarChartView chartWeekly;
    private final TextView tvTrendSub;
    private final TextView tvTrendMin;
    private final TextView tvTrendAvg;
    private final TextView tvTrendMax;
    private final TextView tvChartTitle;
    private final TextView tvChartHint;
    private final LinearLayout legendRow;
    private final TextView[] segments;
    private final TextView modeSlots;
    private final TextView modeDaily;
    private final LinearLayout[] chips = new LinearLayout[Slots.COUNT + 1];

    private final boolean[] visible = {true, true, true, true, true, true};
    private boolean showAverage = true;
    private int range = 1; // 0:7天 1:30天 2:90天 3:全部
    private int mode = 0;  // 0:六次对比 1:日均曲线

    public TrendPage(Activity activity, ViewGroup parent) {
        super(activity, parent, R.layout.page_trend);
        chartSlots = root.findViewById(R.id.chartSlots);
        chartDaily = root.findViewById(R.id.chartDaily);
        chartWeekly = root.findViewById(R.id.chartWeekly);
        tvTrendSub = root.findViewById(R.id.tvTrendSub);
        tvTrendMin = root.findViewById(R.id.tvTrendMin);
        tvTrendAvg = root.findViewById(R.id.tvTrendAvg);
        tvTrendMax = root.findViewById(R.id.tvTrendMax);
        tvChartTitle = root.findViewById(R.id.tvChartTitle);
        tvChartHint = root.findViewById(R.id.tvChartHint);
        legendRow = root.findViewById(R.id.legendRow);
        modeSlots = root.findViewById(R.id.modeSlots);
        modeDaily = root.findViewById(R.id.modeDaily);
        segments = new TextView[]{
                root.findViewById(R.id.seg7),
                root.findViewById(R.id.seg30),
                root.findViewById(R.id.seg90),
                root.findViewById(R.id.segAll)
        };
        for (int i = 0; i < segments.length; i++) {
            final int index = i;
            segments[i].setOnClickListener(v -> {
                range = index;
                applySegments();
                refresh();
            });
        }
        modeSlots.setOnClickListener(v -> {
            mode = 0;
            applyMode();
            refresh();
        });
        modeDaily.setOnClickListener(v -> {
            mode = 1;
            applyMode();
            refresh();
        });

        buildLegend();
    }

    private void buildLegend() {
        for (int i = 0; i <= Slots.COUNT; i++) {
            final int index = i;
            LinearLayout chip = new LinearLayout(activity);
            chip.setOrientation(LinearLayout.HORIZONTAL);
            chip.setGravity(Gravity.CENTER);
            chip.setBackgroundResource(R.drawable.bg_pill_off);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, Ui.dp(activity, 30), 1f);
            lp.setMarginEnd(i < Slots.COUNT ? Ui.dp(activity, 4) : 0);
            chip.setLayoutParams(lp);

            View dot = new View(activity);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    Ui.dp(activity, 5), Ui.dp(activity, 5));
            dot.setLayoutParams(dlp);
            dot.setBackgroundResource(R.drawable.bg_dot);
            dot.setBackgroundTintList(ColorStateList.valueOf(
                    i < Slots.COUNT ? Slots.color(activity, i) : 0xD9FFFFFF));

            TextView label = new TextView(activity);
            label.setText(i < Slots.COUNT ? Slots.SHORT[i] : "日均");
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.setMarginStart(Ui.dp(activity, 3));
            label.setLayoutParams(tlp);

            chip.addView(dot);
            chip.addView(label);
            chip.setOnClickListener(v -> {
                if (index < Slots.COUNT) {
                    visible[index] = !visible[index];
                } else {
                    showAverage = !showAverage;
                }
                applyChips();
                if (mode == 0) {
                    chartSlots.setVisibleSlots(visible);
                    chartSlots.setShowAverage(showAverage);
                }
            });
            chips[i] = chip;
            legendRow.addView(chip);
        }
        applyChips();
    }

    private void applyChips() {
        for (int i = 0; i <= Slots.COUNT; i++) {
            boolean active = i < Slots.COUNT ? visible[i] : showAverage;
            LinearLayout chip = chips[i];
            chip.setBackgroundResource(active ? R.drawable.bg_pill_on : R.drawable.bg_pill_off);
            chip.setAlpha(active ? 1f : 0.45f);
            TextView label = (TextView) chip.getChildAt(1);
            label.setTextColor(Ui.color(activity,
                    active ? R.color.text_primary : R.color.text_tertiary));
        }
    }

    private void applySegments() {
        for (int i = 0; i < segments.length; i++) {
            boolean active = i == range;
            segments[i].setBackgroundResource(active ? R.drawable.bg_pill_on : R.drawable.bg_pill_off);
            segments[i].setTextColor(Ui.color(activity,
                    active ? R.color.mint : R.color.text_secondary));
        }
    }

    private void applyMode() {
        boolean slotsMode = mode == 0;
        modeSlots.setBackgroundResource(slotsMode ? R.drawable.bg_pill_on : R.drawable.bg_pill_off);
        modeDaily.setBackgroundResource(slotsMode ? R.drawable.bg_pill_off : R.drawable.bg_pill_on);
        modeSlots.setTextColor(Ui.color(activity,
                slotsMode ? R.color.mint : R.color.text_secondary));
        modeDaily.setTextColor(Ui.color(activity,
                slotsMode ? R.color.text_secondary : R.color.mint));
        modeSlots.setTypeface(null, slotsMode ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        modeDaily.setTypeface(null, slotsMode ? android.graphics.Typeface.NORMAL : android.graphics.Typeface.BOLD);
    }

    @Override
    public void refresh() {
        applySegments();
        applyMode();

        WeightStore store = WeightStore.get(activity);
        Prefs prefs = Prefs.get(activity);
        List<Record> all = store.all();
        Stats st = Stats.compute(all, prefs.targetKg(), prefs.heightCm());

        LocalDate today = LocalDate.now();
        LocalDate rangeStart;
        if (range == 0) {
            rangeStart = today.minusDays(6);
        } else if (range == 1) {
            rangeStart = today.minusDays(29);
        } else if (range == 2) {
            rangeStart = today.minusDays(89);
        } else {
            rangeStart = st.hasData ? st.days.get(0).date : today;
        }

        LocalDate from = rangeStart;
        if (st.hasData && from.isBefore(st.days.get(0).date)) {
            from = st.days.get(0).date;
        }
        if (from.isAfter(today)) {
            from = today;
        }

        List<Record> filtered = new ArrayList<>();
        for (Record r : all) {
            if (!r.date.isBefore(from) && !r.date.isAfter(today)) {
                filtered.add(r);
            }
        }

        boolean slotsMode = mode == 0;
        chartSlots.setVisibility(slotsMode ? View.VISIBLE : View.GONE);
        chartDaily.setVisibility(slotsMode ? View.GONE : View.VISIBLE);
        legendRow.setVisibility(slotsMode ? View.VISIBLE : View.GONE);
        tvChartTitle.setText(slotsMode ? "六次对比" : "日均曲线");
        tvChartHint.setText(slotsMode ? "点击图例筛选" : "平滑曲线 · 7日均线");

        List<Record> daily = new ArrayList<>();
        for (Stats.DayPoint p : st.days) {
            if (!p.date.isBefore(from) && !p.date.isAfter(today)) {
                daily.add(new Record(p.date, p.avg));
            }
        }

        if (slotsMode) {
            chartSlots.setData(filtered, from, today, visible, showAverage);
            String window = filtered.isEmpty() ? "—"
                    : Fmt.md(from) + " – " + Fmt.md(today);
            tvTrendSub.setText(filtered.isEmpty()
                    ? "这段时间还没有记录"
                    : filtered.size() + " 条记录 · 6 时段 · " + window);
        } else {
            chartDaily.setData(daily, st.target, true);
            String window = daily.isEmpty() ? "—"
                    : Fmt.md(from) + " – " + Fmt.md(today);
            tvTrendSub.setText(daily.isEmpty()
                    ? "这段时间还没有记录"
                    : daily.size() + " 天日均 · " + window);
        }

        chartWeekly.setWeeks(st.weeks);

        if (daily.isEmpty()) {
            tvTrendMin.setText("—");
            tvTrendAvg.setText("—");
            tvTrendMax.setText("—");
            return;
        }
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        double sum = 0.0;
        for (Record r : daily) {
            min = Math.min(min, r.weight);
            max = Math.max(max, r.weight);
            sum += r.weight;
        }
        tvTrendMin.setText(Fmt.kg(min));
        tvTrendAvg.setText(Fmt.kg(sum / daily.size()));
        tvTrendMax.setText(Fmt.kg(max));
    }
}
