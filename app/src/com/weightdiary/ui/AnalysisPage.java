package com.weightdiary.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.util.TypedValue;
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
import com.weightdiary.ui.view.HeatmapView;
import com.weightdiary.ui.view.SlotAverageChartView;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

public class AnalysisPage extends Page {

    private final TextView tvAnaSub;
    private final TextView tvAnaWeek;
    private final TextView tvAnaWeekSub;
    private final TextView tvAnaRate;
    private final TextView tvAnaRateSub;
    private final TextView tvAnaGoal;
    private final TextView tvAnaGoalSub;
    private final TextView tvAnaVol;
    private final TextView tvAnaVolSub;
    private final TextView tvAnaRange;
    private final TextView tvAnaRangeSub;
    private final TextView tvAnaComplete;
    private final TextView tvAnaCompleteSub;
    private final TextView tvAnaBmi;
    private final TextView tvAnaBmiSub;
    private final TextView tvAnaMeal;
    private final TextView tvAnaMealSub;
    private final SlotAverageChartView chartSlotAvg;
    private final HeatmapView heatMonth;
    private final TextView tvHeatMonth;
    private final TextView tvHeatSummary;
    private final LinearLayout insightList;

    public AnalysisPage(Activity activity, ViewGroup parent) {
        super(activity, parent, R.layout.page_analysis);
        tvAnaSub = root.findViewById(R.id.tvAnaSub);
        tvAnaWeek = root.findViewById(R.id.tvAnaWeek);
        tvAnaWeekSub = root.findViewById(R.id.tvAnaWeekSub);
        tvAnaRate = root.findViewById(R.id.tvAnaRate);
        tvAnaRateSub = root.findViewById(R.id.tvAnaRateSub);
        tvAnaGoal = root.findViewById(R.id.tvAnaGoal);
        tvAnaGoalSub = root.findViewById(R.id.tvAnaGoalSub);
        tvAnaVol = root.findViewById(R.id.tvAnaVol);
        tvAnaVolSub = root.findViewById(R.id.tvAnaVolSub);
        tvAnaRange = root.findViewById(R.id.tvAnaRange);
        tvAnaRangeSub = root.findViewById(R.id.tvAnaRangeSub);
        tvAnaComplete = root.findViewById(R.id.tvAnaComplete);
        tvAnaCompleteSub = root.findViewById(R.id.tvAnaCompleteSub);
        tvAnaBmi = root.findViewById(R.id.tvAnaBmi);
        tvAnaBmiSub = root.findViewById(R.id.tvAnaBmiSub);
        tvAnaMeal = root.findViewById(R.id.tvAnaMeal);
        tvAnaMealSub = root.findViewById(R.id.tvAnaMealSub);
        chartSlotAvg = root.findViewById(R.id.chartSlotAvg);
        heatMonth = root.findViewById(R.id.heatMonth);
        tvHeatMonth = root.findViewById(R.id.tvHeatMonth);
        tvHeatSummary = root.findViewById(R.id.tvHeatSummary);
        insightList = root.findViewById(R.id.insightList);

        root.findViewById(R.id.cardBmi).setOnClickListener(v ->
                activity.startActivity(new Intent(activity, SettingsActivity.class)));
    }

    @Override
    public void refresh() {
        WeightStore store = WeightStore.get(activity);
        Prefs prefs = Prefs.get(activity);
        List<Record> all = store.all();
        Stats st = Stats.compute(all, prefs.targetKg(), prefs.heightCm());

        int mint = Ui.color(activity, R.color.mint);
        int coral = Ui.color(activity, R.color.coral);
        int warn = Ui.color(activity, R.color.warn);
        int primary = Ui.color(activity, R.color.text_primary);
        int secondary = Ui.color(activity, R.color.text_secondary);
        int tertiary = Ui.color(activity, R.color.text_tertiary);

        tvAnaSub.setText(st.hasData
                ? "基于 " + st.recordCount + " 条记录 · " + st.dayCount + " 天 · 近 30 天窗口"
                : "还没有记录 · 每日六次记录后自动生成分析");

        if (Double.isNaN(st.weekDelta)) {
            tvAnaWeek.setText("—");
            tvAnaWeek.setTextColor(primary);
            tvAnaWeekSub.setText("需要连续两周数据");
        } else {
            tvAnaWeek.setText(Fmt.signed(st.weekDelta));
            tvAnaWeek.setTextColor(st.weekDelta < -0.05 ? mint : (st.weekDelta > 0.05 ? coral : primary));
            tvAnaWeekSub.setText("本周日均 " + Fmt.kg(st.weekAvg) + " · 上周 " + Fmt.kg(st.prevWeekAvg));
        }

        if (Double.isNaN(st.weeklyRate)) {
            tvAnaRate.setText("—");
            tvAnaRate.setTextColor(primary);
            tvAnaRateSub.setText("至少需要 3 天数据");
        } else {
            tvAnaRate.setText(Fmt.signed(st.weeklyRate));
            tvAnaRate.setTextColor(st.weeklyRate < -0.05 ? mint : (st.weeklyRate > 0.05 ? coral : primary));
            String sub = "kg / 周（日均拟合）";
            if (!Double.isNaN(st.r2)) {
                sub = String.format(Locale.US, "kg / 周 · R² %.2f", st.r2);
            }
            tvAnaRateSub.setText(sub);
        }

        if (st.target <= 0) {
            tvAnaGoal.setText("未设置");
            tvAnaGoal.setTextColor(secondary);
            tvAnaGoalSub.setText("前往设置目标体重");
        } else if (!st.hasData) {
            tvAnaGoal.setText("暂无记录");
            tvAnaGoal.setTextColor(secondary);
            tvAnaGoalSub.setText("目标 " + Fmt.kg(st.target) + " kg");
        } else {
            double remain = st.remainingToTarget();
            if (st.predictDate != null) {
                tvAnaGoal.setText(Fmt.cn(st.predictDate));
                tvAnaGoal.setTextColor(primary);
            } else {
                tvAnaGoal.setText(st.goalProgress >= 0.999 ? "已达成" : "进行中");
                tvAnaGoal.setTextColor(primary);
            }
            String sub = "已达成 " + Fmt.percent(st.goalProgress);
            if (remain > 0.05) {
                sub += " · 还差 " + Fmt.kg(remain) + " kg";
            } else {
                sub += " · 目标 " + Fmt.kg(st.target) + " kg";
            }
            tvAnaGoalSub.setText(sub);
        }

        if (Double.isNaN(st.volatility)) {
            tvAnaVol.setText("—");
            tvAnaVol.setTextColor(primary);
            tvAnaVolSub.setText("需要连续日期的日均");
        } else {
            tvAnaVol.setText(Fmt.kg(st.volatility));
            String level = st.volatility < 0.25 ? "平稳" : (st.volatility < 0.45 ? "正常" : "偏大");
            tvAnaVol.setTextColor(st.volatility < 0.25 ? mint : (st.volatility < 0.45 ? primary : coral));
            tvAnaVolSub.setText("kg · 日间波动 " + level);
        }

        if (Double.isNaN(st.withinDayRangeAvg)) {
            tvAnaRange.setText("—");
            tvAnaRange.setTextColor(primary);
            tvAnaRangeSub.setText("需要一天多次记录");
        } else {
            tvAnaRange.setText(Fmt.kg(st.withinDayRangeAvg));
            tvAnaRange.setTextColor(primary);
            String sub = "kg · 近 30 天平均极差";
            if (!Double.isNaN(st.todayRange)) {
                sub += " · 今日 " + Fmt.kg(st.todayRange);
            }
            tvAnaRangeSub.setText(sub);
        }

        tvAnaComplete.setText(st.recordsLast7 + " / 42");
        tvAnaComplete.setTextColor(st.recordsLast7 >= 42 ? mint : primary);
        tvAnaCompleteSub.setText("近 7 天 · 今日 " + st.todayCount + " / 6");

        if (st.heightCm <= 0) {
            tvAnaBmi.setText("未设置");
            tvAnaBmi.setTextColor(secondary);
            tvAnaBmiSub.setText("点击填写身高");
        } else if (!st.hasData) {
            tvAnaBmi.setText("—");
            tvAnaBmi.setTextColor(primary);
            tvAnaBmiSub.setText("记录后计算 BMI");
        } else {
            String label = st.bmiLabel();
            tvAnaBmi.setText(Fmt.kg(st.bmi));
            tvAnaBmi.setTextColor(label.equals("正常") ? mint : (label.equals("偏瘦") ? warn : coral));
            tvAnaBmiSub.setText(label + " · 身高 "
                    + String.format(Locale.US, "%.0f", st.heightCm) + " cm");
        }

        Stats.MealDelta strongest = null;
        for (Stats.MealDelta m : st.meals) {
            if (m.days > 0 && !Double.isNaN(m.avgDelta)) {
                if (strongest == null || Math.abs(m.avgDelta) > Math.abs(strongest.avgDelta)) {
                    strongest = m;
                }
            }
        }
        if (strongest == null) {
            tvAnaMeal.setText("—");
            tvAnaMeal.setTextColor(primary);
            tvAnaMealSub.setText("需要同一餐前后数据");
        } else {
            tvAnaMeal.setText(Fmt.signed(strongest.avgDelta));
            tvAnaMeal.setTextColor(strongest.avgDelta < 0 ? mint : coral);
            tvAnaMealSub.setText("kg · " + Slots.mealName(strongest.meal)
                    + " · " + strongest.days + " 天");
        }

        chartSlotAvg.setStats(st.slots);

        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        heatMonth.setMonth(monthStart, all);
        tvHeatMonth.setText(monthStart.getMonthValue() + " 月");

        double[] daySum = new double[32];
        int[] dayCount = new int[32];
        for (Record r : all) {
            if (r.date.getYear() == monthStart.getYear()
                    && r.date.getMonthValue() == monthStart.getMonthValue()) {
                daySum[r.date.getDayOfMonth()] += r.weight;
                dayCount[r.date.getDayOfMonth()]++;
            }
        }
        int recordedDays = 0;
        double avgSum = 0.0;
        for (int d = 1; d < 32; d++) {
            if (dayCount[d] > 0) {
                recordedDays++;
                avgSum += daySum[d] / dayCount[d];
            }
        }
        if (recordedDays == 0) {
            tvHeatSummary.setText("本月暂无记录");
        } else {
            tvHeatSummary.setText("记录 " + recordedDays + " 天 · 日均 "
                    + Fmt.kg(avgSum / recordedDays) + " kg");
        }

        insightList.removeAllViews();
        for (String insight : st.insights()) {
            TextView tv = new TextView(activity);
            tv.setText("·  " + insight);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            tv.setTextColor(Color.parseColor("#D5E1E6"));
            tv.setLineSpacing(Ui.dp(activity, 4), 1f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Ui.dp(activity, 6);
            tv.setLayoutParams(lp);
            insightList.addView(tv);
        }
        tvHeatSummary.setTextColor(tertiary);
    }
}
