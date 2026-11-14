package com.weightdiary.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.weightdiary.R;
import com.weightdiary.core.Slots;
import com.weightdiary.model.Record;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 月度日历热力图：颜色由轻到重映射，未记录的日期留白。
 */
public class HeatmapView extends View {

    private final List<Record> data = new ArrayList<>();
    private LocalDate month = LocalDate.now().withDayOfMonth(1);

    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF cell = new RectF();

    public HeatmapView(Context context) {
        super(context);
        init();
    }

    public HeatmapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(1.5f));
        ringPaint.setColor(getContext().getColor(R.color.mint));

        textPaint.setTextSize(dp(9f));
        textPaint.setTextAlign(Paint.Align.CENTER);

        headPaint.setColor(getContext().getColor(R.color.text_tertiary));
        headPaint.setTextSize(dp(9f));
        headPaint.setTextAlign(Paint.Align.CENTER);
    }

    private float dp(float value) {
        return ChartUtils.dp(getContext(), value);
    }

    public void setMonth(LocalDate month, List<Record> records) {
        this.month = month.withDayOfMonth(1);
        data.clear();
        YearMonth ym = YearMonth.from(this.month);
        for (Record r : records) {
            if (r.date.getYear() == this.month.getYear()
                    && r.date.getMonthValue() == this.month.getMonthValue()) {
                data.add(r);
            }
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float headerH = dp(18f);
        float gap = dp(3f);
        float cellW = getWidth() / 7f;
        float cellH = (getHeight() - headerH) / 6f;

        String[] heads = {"一", "二", "三", "四", "五", "六", "日"};
        for (int i = 0; i < 7; i++) {
            canvas.drawText(heads[i], cellW * i + cellW / 2f, headerH - dp(5f), headPaint);
        }

        Map<Integer, double[]> byDay = new HashMap<>();
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (Record r : data) {
            int dayOfMonth = r.date.getDayOfMonth();
            double[] arr = byDay.get(dayOfMonth);
            if (arr == null) {
                // 0=日均累计（仅饭前/睡前）, 1=日均条数, 2=当天总记录数
                arr = new double[]{0.0, 0.0, 0.0};
                byDay.put(dayOfMonth, arr);
            }
            arr[2] += 1.0;
            if (Slots.countsInDailyAvg(r.slot)) {
                arr[0] += r.weight;
                arr[1] += 1.0;
            }
        }
        for (double[] arr : byDay.values()) {
            if (arr[1] <= 0) {
                continue;
            }
            double avg = arr[0] / arr[1];
            min = Math.min(min, avg);
            max = Math.max(max, avg);
        }

        YearMonth ym = YearMonth.from(month);
        int days = ym.lengthOfMonth();
        int offset = month.getDayOfWeek().getValue() - 1;
        LocalDate today = LocalDate.now();

        for (int day = 1; day <= days; day++) {
            int index = offset + day - 1;
            int col = index % 7;
            int row = index / 7;
            float left = col * cellW + gap;
            float top = headerH + row * cellH + gap;
            float right = (col + 1) * cellW - gap;
            float bottom = headerH + (row + 1) * cellH - gap;
            cell.set(left, top, right, bottom);

            LocalDate date = month.withDayOfMonth(day);
            double[] arr = byDay.get(day);
            boolean recorded = arr != null;
            boolean hasAvg = recorded && arr[1] > 0;
            boolean future = date.isAfter(today);

            if (hasAvg) {
                double avg = arr[0] / arr[1];
                int count = (int) arr[2];
                float t = (max - min) < 0.01 ? 0.5f : (float) ((avg - min) / (max - min));
                int alpha = 0x60 + (int) ((0xAA - 0x60) * (count / (float) Slots.COUNT));
                int color = weightColor(t, alpha);
                cellPaint.setColor(color);
                canvas.drawRoundRect(cell, dp(7f), dp(7f), cellPaint);
                textPaint.setColor(Color.WHITE);
            } else if (recorded) {
                // 当天有记录但没有饭前 / 睡前数据，不参与日均着色
                cellPaint.setColor(Color.parseColor("#1FFFFFFF"));
                canvas.drawRoundRect(cell, dp(7f), dp(7f), cellPaint);
                textPaint.setColor(Color.parseColor("#99FFFFFF"));
            } else if (future) {
                cellPaint.setColor(Color.parseColor("#05FFFFFF"));
                canvas.drawRoundRect(cell, dp(7f), dp(7f), cellPaint);
                textPaint.setColor(Color.parseColor("#22FFFFFF"));
            } else {
                cellPaint.setColor(Color.parseColor("#0AFFFFFF"));
                canvas.drawRoundRect(cell, dp(7f), dp(7f), cellPaint);
                textPaint.setColor(Color.parseColor("#4DFFFFFF"));
            }

            canvas.drawText(String.valueOf(day), cell.centerX(), cell.centerY() + dp(3.2f), textPaint);

            if (date.equals(today)) {
                canvas.drawRoundRect(cell, dp(7f), dp(7f), ringPaint);
            }
        }
    }

    private int weightColor(float t, int alpha) {
        int mint = getContext().getColor(R.color.mint);
        int violet = getContext().getColor(R.color.violet);
        int coral = getContext().getColor(R.color.coral);
        if (t <= 0.5f) {
            return blend(mint, violet, t * 2f, alpha);
        }
        return blend(violet, coral, (t - 0.5f) * 2f, alpha);
    }

    private int blend(int from, int to, float t, int alpha) {
        int r = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * t);
        int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * t);
        int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t);
        return Color.argb(alpha, r, g, b);
    }
}
