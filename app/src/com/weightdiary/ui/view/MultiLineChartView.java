package com.weightdiary.ui.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.weightdiary.R;
import com.weightdiary.core.Fmt;
import com.weightdiary.core.Slots;
import com.weightdiary.model.Record;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 六时段平行折线：每条线对应固定的一个时段，横轴为日期，可配合图例筛选。
 */
public class MultiLineChartView extends View {

    private final List<Record> data = new ArrayList<>();
    private final boolean[] visible = {true, true, true, true, true, true, true};
    private boolean showAverage = true;

    private LocalDate from = LocalDate.now();
    private LocalDate to = LocalDate.now();
    private final List<LocalDate> dates = new ArrayList<>();
    private double[][] values = new double[0][0];
    private double[] averages = new double[0];
    private float[] xs = new float[0];

    private float reveal = 0f;
    private ValueAnimator animator;
    private int touched = -1;

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint avgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tipBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tipStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tipValuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF plot = new RectF();
    private final RectF tipRect = new RectF();

    public MultiLineChartView(Context context) {
        super(context);
        init();
    }

    public MultiLineChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dp(1f));
        gridPaint.setColor(Color.parseColor("#12FFFFFF"));
        gridPaint.setPathEffect(new DashPathEffect(new float[]{dp(2f), dp(4f)}, 0f));

        labelPaint.setColor(getContext().getColor(R.color.text_tertiary));
        labelPaint.setTextSize(dp(9f));

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(1.6f));
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        dotPaint.setStyle(Paint.Style.FILL);

        avgPaint.setStyle(Paint.Style.STROKE);
        avgPaint.setStrokeWidth(dp(1.2f));
        avgPaint.setColor(Color.parseColor("#B3FFFFFF"));
        avgPaint.setPathEffect(new DashPathEffect(new float[]{dp(5f), dp(4f)}, 0f));
        avgPaint.setStrokeCap(Paint.Cap.ROUND);

        guidePaint.setStyle(Paint.Style.STROKE);
        guidePaint.setStrokeWidth(dp(1f));
        guidePaint.setColor(Color.parseColor("#4DFFFFFF"));
        guidePaint.setPathEffect(new DashPathEffect(new float[]{dp(4f), dp(4f)}, 0f));

        tipBgPaint.setStyle(Paint.Style.FILL);
        tipBgPaint.setColor(Color.parseColor("#F2161F26"));

        tipStrokePaint.setStyle(Paint.Style.STROKE);
        tipStrokePaint.setStrokeWidth(dp(1f));
        tipStrokePaint.setColor(Color.parseColor("#33FFFFFF"));

        tipTextPaint.setColor(getContext().getColor(R.color.text_secondary));
        tipTextPaint.setTextSize(dp(9f));

        tipValuePaint.setColor(getContext().getColor(R.color.text_primary));
        tipValuePaint.setTextSize(dp(9f));
        tipValuePaint.setTextAlign(Paint.Align.RIGHT);

        hintPaint.setColor(Color.parseColor("#4DFFFFFF"));
        hintPaint.setTextSize(dp(11f));
        hintPaint.setTextAlign(Paint.Align.CENTER);
    }

    private float dp(float value) {
        return ChartUtils.dp(getContext(), value);
    }

    public void setData(List<Record> records, LocalDate from, LocalDate to,
                        boolean[] visibleSlots, boolean showAvg) {
        LocalDate safeTo = to.isBefore(from) ? from : to;
        long span = ChronoUnit.DAYS.between(from, safeTo);
        if (span > 5000) {
            from = safeTo.minusDays(5000);
        }
        this.from = from;
        this.to = safeTo;
        if (visibleSlots != null && visibleSlots.length >= Slots.COUNT) {
            System.arraycopy(visibleSlots, 0, visible, 0, Slots.COUNT);
        }
        this.showAverage = showAvg;
        data.clear();
        for (Record r : records) {
            if (!r.date.isBefore(from) && !r.date.isAfter(to)) {
                data.add(r);
            }
        }
        buildSeries();
        animateReveal();
    }

    public void setVisibleSlots(boolean[] visibleSlots) {
        if (visibleSlots != null && visibleSlots.length >= Slots.COUNT) {
            System.arraycopy(visibleSlots, 0, visible, 0, Slots.COUNT);
        }
        invalidate();
    }

    public void setShowAverage(boolean show) {
        showAverage = show;
        invalidate();
    }

    private void buildSeries() {
        dates.clear();
        LocalDate d = from;
        while (true) {
            dates.add(d);
            if (d.equals(to)) {
                break;
            }
            d = d.plusDays(1);
        }
        int n = dates.size();
        values = new double[Slots.COUNT][n];
        averages = new double[n];
        for (int s = 0; s < Slots.COUNT; s++) {
            for (int i = 0; i < n; i++) {
                values[s][i] = Double.NaN;
            }
        }
        double[] sum = new double[n];
        int[] cnt = new int[n];
        for (Record r : data) {
            int idx = (int) ChronoUnit.DAYS.between(from, r.date);
            if (idx < 0 || idx >= n) {
                continue;
            }
            values[Slots.clamp(r.slot)][idx] = r.weight;
            sum[idx] += r.weight;
            cnt[idx]++;
        }
        for (int i = 0; i < n; i++) {
            averages[i] = cnt[i] > 0 ? sum[i] / cnt[i] : Double.NaN;
        }
        xs = new float[n];
    }

    private void animateReveal() {
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(720);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            reveal = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (dates.isEmpty() || data.isEmpty()) {
            hintPaint.setColor(Color.parseColor("#4DFFFFFF"));
            canvas.drawText("暂无数据", getWidth() / 2f, getHeight() / 2f, hintPaint);
            return;
        }

        plot.set(dp(40f), dp(18f), getWidth() - dp(10f), getHeight() - dp(26f));
        int n = dates.size();
        for (int i = 0; i < n; i++) {
            xs[i] = n == 1 ? plot.centerX() : plot.left + plot.width() * i / (n - 1f);
        }

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        boolean any = false;
        for (int s = 0; s < Slots.COUNT; s++) {
            if (!visible[s]) {
                continue;
            }
            for (int i = 0; i < n; i++) {
                double v = values[s][i];
                if (!Double.isNaN(v)) {
                    any = true;
                    min = Math.min(min, v);
                    max = Math.max(max, v);
                }
            }
        }
        if (showAverage) {
            for (int i = 0; i < n; i++) {
                double v = averages[i];
                if (!Double.isNaN(v)) {
                    any = true;
                    min = Math.min(min, v);
                    max = Math.max(max, v);
                }
            }
        }
        if (!any) {
            hintPaint.setColor(Color.parseColor("#4DFFFFFF"));
            canvas.drawText("已隐藏全部时段", getWidth() / 2f, getHeight() / 2f, hintPaint);
            return;
        }

        double span = max - min;
        if (span < 0.5) {
            double mid = (max + min) / 2.0;
            min = mid - 0.25;
            max = mid + 0.25;
            span = 0.5;
        }
        double pad = span * 0.12;
        min -= pad;
        max += pad;
        span = max - min;

        drawGrid(canvas, min, max);
        drawXLabels(canvas, n);

        float revealX = plot.left + plot.width() * reveal;
        canvas.save();
        canvas.clipRect(plot.left - dp(2f), 0f, revealX + dp(1f), getHeight());

        if (showAverage) {
            drawAverage(canvas, min, max);
        }

        for (int s = 0; s < Slots.COUNT; s++) {
            if (!visible[s]) {
                continue;
            }
            float[] ys = new float[n];
            for (int i = 0; i < n; i++) {
                double v = values[s][i];
                ys[i] = Double.isNaN(v) ? Float.NaN
                        : plot.top + (float) ((max - v) / span * plot.height());
            }
            float[] px = new float[n];
            float[] py = new float[n];
            int count = 0;
            for (int i = 0; i < n; i++) {
                if (!Float.isNaN(ys[i]) && xs[i] <= revealX) {
                    px[count] = xs[i];
                    py[count] = ys[i];
                    count++;
                }
            }

            linePaint.setColor(Slots.color(getContext(), s));
            linePaint.setAlpha(230);
            if (count >= 2) {
                float[] tx = new float[count];
                float[] ty = new float[count];
                System.arraycopy(px, 0, tx, 0, count);
                System.arraycopy(py, 0, ty, 0, count);
                Path series = new Path();
                ChartUtils.smoothPath(series, tx, ty);
                canvas.drawPath(series, linePaint);
            } else if (count == 1) {
                dotPaint.setColor(Slots.color(getContext(), s));
                dotPaint.setAlpha(230);
                canvas.drawCircle(px[0], py[0], dp(2f), dotPaint);
            }
        }

        canvas.restore();

        if (touched >= 0 && touched < n) {
            drawTooltip(canvas, touched, min, max, span);
        }
    }

    private void drawGrid(Canvas canvas, double min, double max) {
        for (int k = 0; k <= 3; k++) {
            float y = plot.top + plot.height() * k / 3f;
            canvas.drawLine(plot.left, y, plot.right, y, gridPaint);
            double value = max - (max - min) * k / 3.0;
            labelPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(Fmt.kg(value), plot.left - dp(6f), y + dp(3f), labelPaint);
        }
    }

    private void drawXLabels(Canvas canvas, int n) {
        labelPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(Fmt.md(dates.get(0)), plot.left, plot.bottom + dp(15f), labelPaint);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(Fmt.md(dates.get(n / 2)), plot.centerX(), plot.bottom + dp(15f), labelPaint);
        labelPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(Fmt.md(dates.get(n - 1)), plot.right, plot.bottom + dp(15f), labelPaint);
    }

    private void drawAverage(Canvas canvas, double min, double max) {
        float[] ys = new float[dates.size()];
        float[] x = new float[dates.size()];
        int idx = 0;
        for (int i = 0; i < dates.size(); i++) {
            double v = averages[i];
            if (Double.isNaN(v)) {
                continue;
            }
            x[idx] = xs[i];
            ys[idx] = plot.top + (float) ((max - v) / (max - min) * plot.height());
            idx++;
        }
        if (idx < 2) {
            return;
        }
        float[] tx = new float[idx];
        float[] ty = new float[idx];
        System.arraycopy(x, 0, tx, 0, idx);
        System.arraycopy(ys, 0, ty, 0, idx);
        Path p = new Path();
        ChartUtils.smoothPath(p, tx, ty);
        canvas.drawPath(p, avgPaint);
    }

    private void drawTooltip(Canvas canvas, int index, double min, double max, double span) {
        float x = xs[index];
        canvas.drawLine(x, plot.top, x, plot.bottom, guidePaint);

        List<Integer> rows = new ArrayList<>();
        for (int s = 0; s < Slots.COUNT; s++) {
            if (visible[s] && !Double.isNaN(values[s][index])) {
                rows.add(s);
            }
        }
        boolean avgRow = showAverage && !Double.isNaN(averages[index]);

        String header = Fmt.md(dates.get(index));
        float boxW = dp(96f);
        for (int s : rows) {
            float rowW = dp(10f) + dp(4f) + tipTextPaint.measureText(Slots.name(s))
                    + dp(10f) + tipValuePaint.measureText(Fmt.kg(values[s][index]));
            boxW = Math.max(boxW, rowW + dp(20f));
        }
        if (avgRow) {
            boxW = Math.max(boxW, dp(10f) + dp(4f) + tipTextPaint.measureText("日均")
                    + dp(10f) + tipValuePaint.measureText(Fmt.kg(averages[index])) + dp(20f));
        }
        int rowCount = rows.size() + (avgRow ? 1 : 0);
        float boxH = dp(26f) + Math.max(1, rowCount) * dp(14f) + dp(6f);
        float left = x > plot.centerX() ? plot.left + dp(4f) : plot.right - boxW - dp(4f);
        float top = plot.top + dp(4f);
        tipRect.set(left, top, left + boxW, top + boxH);
        canvas.drawRoundRect(tipRect, dp(10f), dp(10f), tipBgPaint);
        canvas.drawRoundRect(tipRect, dp(10f), dp(10f), tipStrokePaint);

        tipTextPaint.setTextAlign(Paint.Align.LEFT);
        tipTextPaint.setColor(getContext().getColor(R.color.text_primary));
        canvas.drawText(header, left + dp(10f), top + dp(16f), tipTextPaint);
        tipTextPaint.setColor(getContext().getColor(R.color.text_secondary));

        float rowY = top + dp(26f);
        float right = left + boxW - dp(10f);
        dotPaint.setStyle(Paint.Style.FILL);
        for (int s : rows) {
            dotPaint.setColor(Slots.color(getContext(), s));
            dotPaint.setAlpha(255);
            canvas.drawCircle(left + dp(12f), rowY - dp(3f), dp(2.8f), dotPaint);
            canvas.drawText(Slots.name(s), left + dp(19f), rowY, tipTextPaint);
            canvas.drawText(Fmt.kg(values[s][index]), right, rowY, tipValuePaint);
            rowY += dp(14f);
        }
        if (avgRow) {
            dotPaint.setColor(Color.parseColor("#D9FFFFFF"));
            canvas.drawCircle(left + dp(12f), rowY - dp(3f), dp(2.8f), dotPaint);
            canvas.drawText("日均", left + dp(19f), rowY, tipTextPaint);
            canvas.drawText(Fmt.kg(averages[index]), right, rowY, tipValuePaint);
        }
        if (rows.isEmpty() && !avgRow) {
            canvas.drawText("无记录", left + dp(10f), rowY, tipTextPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (dates.isEmpty() || xs.length == 0) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {
                int nearest = nearestIndex(event.getX());
                if (nearest >= 0 && nearest != touched) {
                    touched = nearest;
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (touched != -1) {
                    touched = -1;
                    invalidate();
                }
                return true;
            }
            default:
                return super.onTouchEvent(event);
        }
    }

    private int nearestIndex(float x) {
        int best = -1;
        float bestD = Float.MAX_VALUE;
        for (int i = 0; i < xs.length; i++) {
            float d = Math.abs(xs[i] - x);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }
}
