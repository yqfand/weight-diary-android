package com.weightdiary.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.util.TypedValue;
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
import com.weightdiary.ui.view.RingProgressView;
import com.weightdiary.ui.view.SparklineView;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class TodayPage extends Page {

    private static final int[] TILE_IDS = {
            R.id.tileSlot0, R.id.tileSlot1, R.id.tileSlot2,
            R.id.tileSlot3, R.id.tileSlot4, R.id.tileSlot5
    };
    private static final int[] VALUE_IDS = {
            R.id.tvSlot0, R.id.tvSlot1, R.id.tvSlot2,
            R.id.tvSlot3, R.id.tvSlot4, R.id.tvSlot5
    };
    private static final int[] DOT_IDS = {
            R.id.dotSlot0, R.id.dotSlot1, R.id.dotSlot2,
            R.id.dotSlot3, R.id.dotSlot4, R.id.dotSlot5
    };

    private final TextView tvHeroHint;
    private final TextView tvTodayWeight;
    private final TextView tvUnit;
    private final TextView tvTodayDelta;
    private final TextView tvRingPct;
    private final TextView tvRingLabel;
    private final TextView btnRecord;
    private final TextView tvGoalNote;
    private final TextView tvCompletion;
    private final TextView tvStatWeek;
    private final TextView tvStatStreak;
    private final TextView tvStatTotal;
    private final TextView tvSparkRange;
    private final TextView tvSparkMin;
    private final TextView tvSparkAvg;
    private final TextView tvSparkMax;
    private final TextView tvInsightToday;
    private final RingProgressView ring;
    private final SparklineView spark;
    private final LinearLayout[] tiles = new LinearLayout[Slots.COUNT];
    private final TextView[] slotValues = new TextView[Slots.COUNT];

    private int nextSlot = -1;

    public TodayPage(Activity activity, ViewGroup parent) {
        super(activity, parent, R.layout.page_today);
        tvHeroHint = root.findViewById(R.id.tvHeroHint);
        tvTodayWeight = root.findViewById(R.id.tvTodayWeight);
        tvUnit = root.findViewById(R.id.tvUnit);
        tvTodayDelta = root.findViewById(R.id.tvTodayDelta);
        tvRingPct = root.findViewById(R.id.tvRingPct);
        tvRingLabel = root.findViewById(R.id.tvRingLabel);
        btnRecord = root.findViewById(R.id.btnRecordToday);
        tvGoalNote = root.findViewById(R.id.tvGoalNote);
        tvCompletion = root.findViewById(R.id.tvCompletion);
        tvStatWeek = root.findViewById(R.id.tvStatWeek);
        tvStatStreak = root.findViewById(R.id.tvStatStreak);
        tvStatTotal = root.findViewById(R.id.tvStatTotal);
        tvSparkRange = root.findViewById(R.id.tvSparkRange);
        tvSparkMin = root.findViewById(R.id.tvSparkMin);
        tvSparkAvg = root.findViewById(R.id.tvSparkAvg);
        tvSparkMax = root.findViewById(R.id.tvSparkMax);
        tvInsightToday = root.findViewById(R.id.tvInsightToday);
        ring = root.findViewById(R.id.ringToday);
        spark = root.findViewById(R.id.sparkToday);

        for (int i = 0; i < Slots.COUNT; i++) {
            final int slot = i;
            tiles[i] = root.findViewById(TILE_IDS[i]);
            slotValues[i] = root.findViewById(VALUE_IDS[i]);
            View dot = root.findViewById(DOT_IDS[i]);
            dot.setBackgroundTintList(ColorStateList.valueOf(Slots.color(activity, i)));
            tiles[i].setOnClickListener(v ->
                    WeightDialog.show(activity, LocalDate.now(), slot));
        }

        btnRecord.setOnClickListener(v -> {
            LocalDate today = LocalDate.now();
            if (nextSlot >= 0) {
                WeightDialog.show(activity, today, nextSlot);
            } else {
                Ui.toast(activity, "今日六次已全部记录，点击下方方格可修改");
            }
        });
        tvGoalNote.setOnClickListener(v ->
                activity.startActivity(new Intent(activity, SettingsActivity.class)));
    }

    @Override
    public void refresh() {
        WeightStore store = WeightStore.get(activity);
        Prefs prefs = Prefs.get(activity);
        List<Record> all = store.all();
        Stats st = Stats.compute(all, prefs.targetKg(), prefs.heightCm());
        LocalDate today = LocalDate.now();

        int mint = Ui.color(activity, R.color.mint);
        int coral = Ui.color(activity, R.color.coral);
        int tertiary = Ui.color(activity, R.color.text_tertiary);
        int primary = Ui.color(activity, R.color.text_primary);

        if (st.todayCount > 0) {
            tvUnit.setVisibility(View.VISIBLE);
            tvTodayWeight.setTextSize(TypedValue.COMPLEX_UNIT_SP, 48f);
            tvTodayWeight.setTextColor(primary);
            tvTodayWeight.setText(Fmt.kg(st.todayAvg));
            tvHeroHint.setText("今日日均 · " + st.todayCount + " / 6 次");
            if (st.prevDay != null && !Double.isNaN(st.prevDayAvg)) {
                double delta = st.todayAvg - st.prevDayAvg;
                tvTodayDelta.setText("较 " + Fmt.cn(st.prevDay) + " " + Fmt.signedKg(delta));
                tvTodayDelta.setTextColor(delta < -0.05 ? mint : (delta > 0.05 ? coral : tertiary));
            } else {
                tvTodayDelta.setText("今日第一组数据，继续记录可对比");
                tvTodayDelta.setTextColor(tertiary);
            }
        } else if (st.hasData) {
            tvUnit.setVisibility(View.VISIBLE);
            tvTodayWeight.setTextSize(TypedValue.COMPLEX_UNIT_SP, 48f);
            tvTodayWeight.setTextColor(Ui.color(activity, R.color.text_secondary));
            tvTodayWeight.setText(Fmt.kg(st.latestAvg));
            tvHeroHint.setText("今日未记录");
            long gap = Math.max(0, ChronoUnit.DAYS.between(st.latestDay, today));
            String when = gap == 0 ? "今天" : (gap == 1 ? "昨天" : gap + " 天前");
            tvTodayDelta.setText("上次记录 " + Fmt.md(st.latestDay) + " · " + when);
            tvTodayDelta.setTextColor(tertiary);
        } else {
            tvUnit.setVisibility(View.GONE);
            tvTodayWeight.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f);
            tvTodayWeight.setTextColor(tertiary);
            tvTodayWeight.setText("未记录");
            tvHeroHint.setText("今天还没有记录");
            tvTodayDelta.setText("记录早餐前体重，开启日均分析");
            tvTodayDelta.setTextColor(tertiary);
        }

        if (st.target > 0 && st.hasData) {
            float progress = Double.isNaN(st.goalProgress) ? 0f : (float) st.goalProgress;
            ring.setProgress(progress, true);
            tvRingPct.setText(Fmt.percent(st.goalProgress));
            tvRingLabel.setText("目标进度");
            double remain = st.remainingToTarget();
            if (remain <= 0.05) {
                tvGoalNote.setText("已达成目标 " + Fmt.kg(st.target) + " kg");
            } else {
                tvGoalNote.setText("目标 " + Fmt.kg(st.target) + " kg · 还差 "
                        + Fmt.kg(remain) + " kg");
            }
        } else {
            ring.setProgress(0f, true);
            tvRingPct.setText("—");
            tvRingLabel.setText("目标进度");
            tvGoalNote.setText(st.target > 0
                    ? "目标 " + Fmt.kg(st.target) + " kg · 等待记录"
                    : "设置目标体重，开启进度追踪");
        }

        tvCompletion.setText(st.todayCount + " / 6");
        tvCompletion.setTextColor(st.todayCount >= 6 ? mint : Ui.color(activity, R.color.text_secondary));

        nextSlot = -1;
        Record[] dayRecords = new Record[Slots.COUNT];
        int lastRecorded = -1;
        for (int i = 0; i < Slots.COUNT; i++) {
            dayRecords[i] = store.get(today, i);
            if (dayRecords[i] != null) {
                lastRecorded = i;
            }
        }
        if (lastRecorded < 0) {
            nextSlot = 0;
        } else {
            for (int i = lastRecorded + 1; i < Slots.COUNT; i++) {
                if (dayRecords[i] == null) {
                    nextSlot = i;
                    break;
                }
            }
            if (nextSlot < 0) {
                for (int i = 0; i < Slots.COUNT; i++) {
                    if (dayRecords[i] == null) {
                        nextSlot = i;
                        break;
                    }
                }
            }
        }

        for (int i = 0; i < Slots.COUNT; i++) {
            Record record = dayRecords[i];
            if (record != null) {
                slotValues[i].setText(Fmt.kg(record.weight));
                slotValues[i].setTextColor(primary);
                tiles[i].setAlpha(1f);
            } else {
                slotValues[i].setText("—");
                slotValues[i].setTextColor(tertiary);
                tiles[i].setAlpha(0.72f);
            }
        }
        btnRecord.setText(nextSlot >= 0
                ? "记录 · " + Slots.name(nextSlot)
                : "今日已完成 · 6 / 6");

        tvStatWeek.setText(Double.isNaN(st.weekAvg) ? "—" : Fmt.kg(st.weekAvg));
        tvStatStreak.setText(st.streakDays > 0 ? st.streakDays + " 天" : "—");
        if (Double.isNaN(st.totalChange)) {
            tvStatTotal.setText("—");
            tvStatTotal.setTextColor(primary);
        } else {
            tvStatTotal.setText(Fmt.signed(st.totalChange));
            tvStatTotal.setTextColor(st.totalChange < -0.05 ? mint
                    : (st.totalChange > 0.05 ? coral : primary));
        }

        List<Record> recent = new ArrayList<>();
        for (Stats.DayPoint p : st.days) {
            if (!p.date.isBefore(today.minusDays(6))) {
                recent.add(new Record(p.date, p.avg));
            }
        }
        if (recent.size() < 2 && st.days.size() > 1) {
            recent.clear();
            for (int i = Math.max(0, st.days.size() - 8); i < st.days.size(); i++) {
                Stats.DayPoint p = st.days.get(i);
                recent.add(new Record(p.date, p.avg));
            }
        }
        spark.setData(recent);

        if (recent.isEmpty()) {
            tvSparkRange.setText("—");
            tvSparkMin.setText("最低 —");
            tvSparkAvg.setText("平均 —");
            tvSparkMax.setText("最高 —");
        } else {
            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;
            double sum = 0.0;
            for (Record r : recent) {
                min = Math.min(min, r.weight);
                max = Math.max(max, r.weight);
                sum += r.weight;
            }
            LocalDate firstDate = recent.get(0).date;
            LocalDate lastDate = recent.get(recent.size() - 1).date;
            tvSparkRange.setText(firstDate.equals(lastDate)
                    ? Fmt.md(lastDate)
                    : Fmt.md(firstDate) + " – " + Fmt.md(lastDate));
            tvSparkMin.setText("最低 " + Fmt.kg(min));
            tvSparkAvg.setText("平均 " + Fmt.kg(sum / recent.size()));
            tvSparkMax.setText("最高 " + Fmt.kg(max));
        }

        List<String> insights = st.insights();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(2, insights.size()); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append("· ").append(insights.get(i));
        }
        tvInsightToday.setText(sb.toString());
    }
}
