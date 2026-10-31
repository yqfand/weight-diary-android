package com.weightdiary.ui;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.weightdiary.R;
import com.weightdiary.core.Fmt;
import com.weightdiary.core.Slots;
import com.weightdiary.data.WeightStore;
import com.weightdiary.model.Record;
import com.weightdiary.ui.view.SlotDotsView;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class HistoryPage extends Page {

    private final TextView tvHistSub;
    private final TextView tvMonth;
    private final TextView tvMonthSummary;
    private final TextView tvHistEmpty;
    private final LinearLayout recordList;
    private final FrameLayout btnPrevMonth;
    private final FrameLayout btnNextMonth;

    private int offset = 0;
    private LocalDate expandedDate;

    public HistoryPage(Activity activity, ViewGroup parent) {
        super(activity, parent, R.layout.page_history);
        tvHistSub = root.findViewById(R.id.tvHistSub);
        tvMonth = root.findViewById(R.id.tvMonth);
        tvMonthSummary = root.findViewById(R.id.tvMonthSummary);
        tvHistEmpty = root.findViewById(R.id.tvHistEmpty);
        recordList = root.findViewById(R.id.recordList);
        btnPrevMonth = root.findViewById(R.id.btnPrevMonth);
        btnNextMonth = root.findViewById(R.id.btnNextMonth);

        btnPrevMonth.setOnClickListener(v -> {
            offset++;
            refresh();
        });
        btnNextMonth.setOnClickListener(v -> {
            if (offset > 0) {
                offset--;
                refresh();
            }
        });
    }

    @Override
    public void refresh() {
        WeightStore store = WeightStore.get(activity);
        List<Record> all = store.all();
        LocalDate month = LocalDate.now().withDayOfMonth(1).minusMonths(offset);
        tvMonth.setText(Fmt.monthCn(month));

        if (all.isEmpty()) {
            tvHistSub.setText("还没有任何记录");
        } else {
            tvHistSub.setText("共 " + all.size() + " 条记录 · 从 " + Fmt.md(all.get(0).date) + " 开始");
        }

        // 所有日期的日均，供差值参考
        TreeMap<LocalDate, Double> dayAvgAll = new TreeMap<>();
        Map<LocalDate, List<Record>> groupAll = new TreeMap<>();
        for (Record r : all) {
            List<Record> list = groupAll.get(r.date);
            if (list == null) {
                list = new ArrayList<>();
                groupAll.put(r.date, list);
            }
            list.add(r);
        }
        for (Map.Entry<LocalDate, List<Record>> e : groupAll.entrySet()) {
            double sum = 0.0;
            for (Record r : e.getValue()) {
                sum += r.weight;
            }
            dayAvgAll.put(e.getKey(), sum / e.getValue().size());
        }

        Map<LocalDate, List<Record>> groupMonth = new TreeMap<>();
        for (Map.Entry<LocalDate, List<Record>> e : groupAll.entrySet()) {
            LocalDate d = e.getKey();
            if (d.getYear() == month.getYear() && d.getMonthValue() == month.getMonthValue()) {
                groupMonth.put(d, e.getValue());
            }
        }

        recordList.removeAllViews();
        int primary = Ui.color(activity, R.color.text_primary);
        int mint = Ui.color(activity, R.color.mint);
        int coral = Ui.color(activity, R.color.coral);
        int tertiary = Ui.color(activity, R.color.text_tertiary);
        int secondary = Ui.color(activity, R.color.text_secondary);

        btnNextMonth.setAlpha(offset == 0 ? 0.35f : 1f);

        if (groupMonth.isEmpty()) {
            recordList.setVisibility(View.GONE);
            tvHistEmpty.setVisibility(View.VISIBLE);
            tvHistEmpty.setText("这个月还没有记录\n在「今日」页完成一次测量即可");
            tvMonthSummary.setText("0 天 · 0 次");
            return;
        }

        recordList.setVisibility(View.VISIBLE);
        tvHistEmpty.setVisibility(View.GONE);

        int dayCount = groupMonth.size();
        int recordCount = 0;
        double dayAvgSum = 0.0;
        for (Map.Entry<LocalDate, List<Record>> e : groupMonth.entrySet()) {
            recordCount += e.getValue().size();
            dayAvgSum += dayAvgAll.get(e.getKey());
        }
        tvMonthSummary.setText(dayCount + " 天 · 日均 " + Fmt.kg(dayAvgSum / dayCount)
                + " kg · " + recordCount + " 次");

        List<LocalDate> dates = new ArrayList<>(groupMonth.keySet());
        // 日期倒序
        for (int i = dates.size() - 1; i >= 0; i--) {
            LocalDate date = dates.get(i);
            List<Record> dayRecords = groupMonth.get(date);
            double dayAvg = dayAvgAll.get(date);

            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Ui.dp(activity, 11), 0, Ui.dp(activity, 11));
            row.setBackgroundResource(R.drawable.bg_row_ripple);
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(v -> {
                expandedDate = expandedDate != null && expandedDate.equals(date) ? null : date;
                refresh();
            });

            LinearLayout left = new LinearLayout(activity);
            left.setOrientation(LinearLayout.VERTICAL);
            left.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView day = new TextView(activity);
            day.setText(Fmt.cn(date));
            day.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            day.setTextColor(primary);

            TextView meta = new TextView(activity);
            meta.setText(Fmt.week(date) + " · " + dayRecords.size() + " / 6 次"
                    + (expandedDate != null && expandedDate.equals(date) ? " · 收起" : ""));
            meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
            meta.setTextColor(tertiary);
            meta.setPadding(0, Ui.dp(activity, 2), 0, 0);

            left.addView(day);
            left.addView(meta);
            row.addView(left);

            SlotDotsView dots = new SlotDotsView(activity);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    Ui.dp(activity, 42), Ui.dp(activity, 12));
            dlp.setMarginEnd(Ui.dp(activity, 8));
            dots.setLayoutParams(dlp);
            dots.setDay(dayRecords);
            row.addView(dots);

            Double prevAvg = dayAvgAll.lowerKey(date) != null
                    ? dayAvgAll.get(dayAvgAll.lowerKey(date)) : null;
            if (prevAvg != null) {
                double delta = dayAvg - prevAvg;
                TextView badge = new TextView(activity);
                badge.setText(Fmt.signed(delta));
                badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
                badge.setTextColor(delta < -0.05 ? mint : (delta > 0.05 ? coral : tertiary));
                badge.setBackgroundResource(R.drawable.bg_pill_off);
                badge.setPadding(Ui.dp(activity, 8), Ui.dp(activity, 3),
                        Ui.dp(activity, 8), Ui.dp(activity, 3));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMarginEnd(Ui.dp(activity, 10));
                badge.setLayoutParams(lp);
                row.addView(badge);
            }

            TextView weight = new TextView(activity);
            weight.setText(Fmt.kg(dayAvg) + " kg");
            weight.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
            weight.setTextColor(primary);
            weight.setTypeface(null, Typeface.BOLD);
            row.addView(weight);

            recordList.addView(row);

            if (expandedDate != null && expandedDate.equals(date)) {
                recordList.addView(buildSlotPanel(date, dayRecords));
            }

            if (i > 0) {
                View divider = new View(activity);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 1));
                divider.setLayoutParams(lp);
                divider.setBackgroundColor(0x12FFFFFF);
                recordList.addView(divider);
            }
        }
    }

    private LinearLayout buildSlotPanel(LocalDate date, List<Record> dayRecords) {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(0, 0, 0, Ui.dp(activity, 8));

        int primary = Ui.color(activity, R.color.text_primary);
        int tertiary = Ui.color(activity, R.color.text_tertiary);
        int mint = Ui.color(activity, R.color.mint);

        for (int slot = 0; slot < Slots.COUNT; slot++) {
            Record record = null;
            for (Record r : dayRecords) {
                if (r.slot == slot) {
                    record = r;
                    break;
                }
            }

            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(activity, 6), Ui.dp(activity, 9),
                    Ui.dp(activity, 2), Ui.dp(activity, 9));
            row.setBackgroundResource(R.drawable.bg_row_ripple);
            row.setClickable(true);
            row.setFocusable(true);
            final int slotIndex = slot;
            row.setOnClickListener(v -> WeightDialog.show(activity, date, slotIndex));

            View dot = new View(activity);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    Ui.dp(activity, 6), Ui.dp(activity, 6));
            dlp.setMarginEnd(Ui.dp(activity, 8));
            dot.setLayoutParams(dlp);
            dot.setBackgroundResource(R.drawable.bg_dot);
            dot.setBackgroundTintList(ColorStateList.valueOf(Slots.color(activity, slot)));
            row.addView(dot);

            TextView name = new TextView(activity);
            name.setText(Slots.name(slot));
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            name.setTextColor(tertiary);
            name.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(name);

            TextView value = new TextView(activity);
            value.setText(record != null ? Fmt.kg(record.weight) + " kg" : "未记录");
            value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            value.setTextColor(record != null ? primary : tertiary);
            if (record != null) {
                value.setTypeface(null, Typeface.BOLD);
            }
            row.addView(value);

            TextView action = new TextView(activity);
            action.setText(record != null ? "修改" : "记录");
            action.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
            action.setTextColor(mint);
            action.setPadding(Ui.dp(activity, 10), 0, Ui.dp(activity, 4), 0);
            row.addView(action);

            panel.addView(row);
        }
        return panel;
    }
}
