package com.weightdiary.ui.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.weightdiary.R;
import com.weightdiary.core.Fmt;
import com.weightdiary.model.Record;

import java.util.ArrayList;
import java.util.List;

/**
 * 主趋势图：平滑曲线 + 渐变面积 + 7 日均线 + 目标线 + 触摸提示。
 */
public class LineChartView extends View {

    private final List<Record> data = new ArrayList<>();

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint areaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint maPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint targetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tipBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path linePath = new Path();
    private final Path areaPath = new Path();
    private final Path maPath = new Path();
    private final RectF plot = new RectF();
    private final RectF tipRect = new RectF();

    private double targetKg = 0.0;
    private boolean showTarget = true;
    private float reveal = 0f;
    private ValueAnimator animator;
    private int touched = -1;
    private float[] xs = new float[0];
    private float[] ys = new float[0];
    private double minVal = 0.0;
    private double maxVal = 1.0;

    public LineChartView(Context context) {
        super(context);
        init();
    }

    public LineChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        int mint = getContext().getColor(R.color.mint);
        int violet = getContext().getColor(R.color.violet);
        int tertiary = getContext().getColor(R.color.text_tertiary);

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dp(1f));
        gridPaint.setColor(Color.parseColor("#12FFFFFF"));
        gridPaint.setPathEffect(new DashPathEffect(new float[]{dp(2f), dp(4f)}, 0f));

        labelPaint.setColor(tertiary);
        labelPaint.setTextSize(dp(9f));

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(2.4f));
        linePaint.setColor(mint);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        areaPaint.setStyle(Paint.Style.FILL);
        areaPaint.setShader(new LinearGradient(0f, 0f, 0f, 1f,
                new int[]{Color.parseColor("#3D46E0A8"), Color.parseColor("#0046E0A8")},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));

        maPaint.setStyle(Paint.Style.STROKE);
        maPaint.setStrokeWidth(dp(1.6f));
        maPaint.setColor(violet);
        maPaint.setAlpha(200);
        maPaint.setPathEffect(new DashPathEffect(new float[]{dp(5f), dp(4f)}, 0f));
        maPaint.setStrokeCap(Paint.Cap.ROUND);

        targetPaint.setStyle(Paint.Style.STROKE);
        targetPaint.setStrokeWidth(dp(1.3f));
        targetPaint.setColor(Color.parseColor("#B3FFFFFF"));
        targetPaint.setPathEffect(new DashPathEffect(new float[]{dp(3f), dp(5f)}, 0f));

        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(getContext().getColor(R.color.mint_bright));

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(1.4f));
        ringPaint.setColor(Color.parseColor("#B3FFFFFF"));

        guidePaint.setStyle(Paint.Style.STROKE);
        guidePaint.setStrokeWidth(dp(1f));
        guidePaint.setColor(Color.parseColor("#4DFFFFFF"));
        guidePaint.setPathEffect(new DashPathEffect(new float[]{dp(4f), dp(4f)}, 0f));

        tipBgPaint.setStyle(Paint.Style.FILL);
        tipBgPaint.setColor(Color.parseColor("#F2182229"));

        tipTextPaint.setColor(Color.parseColor("#F2F7F9"));
        tipTextPaint.setTextSize(dp(10f));

        hintPaint.setColor(Color.parseColor("#4DFFFFFF"));
        hintPaint.setTextSize(dp(11f));
        hintPaint.setTextAlign(Paint.Align.CENTER);
    }

    private float dp(float value) {
        return ChartUtils.dp(getContext(), value);
    }

    public void setData(List<Record> records, double targetKg, boolean showTarget) {
        data.clear();
        data.addAll(records);
        this.targetKg = targetKg;
        this.showTarget = showTarget && targetKg > 0;
        this.touched = -1;
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(760);
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

        if (data.isEmpty()) {
            hintPaint.setColor(Color.parseColor("#4DFFFFFF"));
            canvas.drawText("暂无数据", getWidth() / 2f, getHeight() / 2f, hintPaint);
            return;
        }

        plot.set(dp(40f), dp(18f), getWidth() - dp(12f), getHeight() - dp(26f));

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (Record r : data) {
            min = Math.min(min, r.weight);
            max = Math.max(max, r.weight);
        }
        double rawSpan = Math.max(0.1, max - min);
        boolean drawTarget = showTarget
                && targetKg > min - rawSpan * 0.6
                && targetKg < max + rawSpan * 0.6;
        if (drawTarget) {
            min = Math.min(min, targetKg);
            max = Math.max(max, targetKg);
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
        minVal = min;
        maxVal = max;

        int n = data.size();
        long first = data.get(0).date.toEpochDay();
        long last = data.get(n - 1).date.toEpochDay();
        long daySpan = Math.max(1, last - first);

        xs = new float[n];
        ys = new float[n];
        for (int i = 0; i < n; i++) {
            Record r = data.get(i);
            xs[i] = plot.left + (r.date.toEpochDay() - first) / (float) daySpan * plot.width();
            ys[i] = plot.top + (float) ((max - r.weight) / span * plot.height());
        }
        if (n == 1) {
            xs[0] = plot.centerX();
            ys[0] = plot.centerY();
        }

        drawGrid(canvas);
        if (drawTarget) {
            drawTargetLine(canvas, min, max);
        }

        linePath.reset();
        areaPath.reset();
        if (n >= 2) {
            ChartUtils.smoothPath(linePath, xs, ys);
            areaPath.set(linePath);
            areaPath.lineTo(xs[n - 1], plot.bottom);
            areaPath.lineTo(xs[0], plot.bottom);
            areaPath.close();
        }

        maPath.reset();
        if (n >= 5) {
            float[] mx = new float[n];
            float[] my = new float[n];
            for (int i = 0; i < n; i++) {
                int from = Math.max(0, i - 3);
                int to = Math.min(n - 1, i + 3);
                double sum = 0.0;
                for (int j = from; j <= to; j++) {
                    sum += data.get(j).weight;
                }
                double avg = sum / (to - from + 1);
                mx[i] = xs[i];
                my[i] = plot.top + (float) ((max - avg) / span * plot.height());
            }
            ChartUtils.smoothPath(maPath, mx, my);
        }

        float revealX = plot.left + plot.width() * reveal;
        canvas.save();
        canvas.clipRect(plot.left - dp(2f), 0f, revealX + dp(1f), getHeight());

        if (n >= 2) {
            areaPaint.setShader(new LinearGradient(0f, plot.top, 0f, plot.bottom,
                    new int[]{Color.parseColor("#3D46E0A8"), Color.parseColor("#0046E0A8")},
                    null, Shader.TileMode.CLAMP));
            canvas.drawPath(areaPath, areaPaint);
            canvas.drawPath(linePath, linePaint);
            if (n >= 5) {
                canvas.drawPath(maPath, maPaint);
            }
        } else if (n == 1) {
            canvas.drawCircle(xs[0], ys[0], dp(3f), dotPaint);
        }

        canvas.restore();

        drawXLabels(canvas, n);

        if (touched >= 0 && touched < n) {
            drawTooltip(canvas, touched);
        }
    }

    private void drawGrid(Canvas canvas) {
        for (int k = 0; k <= 3; k++) {
            float y = plot.top + plot.height() * k / 3f;
            canvas.drawLine(plot.left, y, plot.right, y, gridPaint);
            double value = maxVal - (maxVal - minVal) * k / 3.0;
            labelPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(Fmt.kg(value), plot.left - dp(6f), y + dp(3f), labelPaint);
        }
    }

    private void drawTargetLine(Canvas canvas, double min, double max) {
        float y = plot.top + (float) ((max - targetKg) / (max - min) * plot.height());
        canvas.drawLine(plot.left, y, plot.right, y, targetPaint);
        labelPaint.setTextAlign(Paint.Align.RIGHT);
        labelPaint.setColor(Color.parseColor("#99FFFFFF"));
        canvas.drawText("目标 " + Fmt.kg(targetKg), plot.right - dp(2f), y - dp(5f), labelPaint);
        labelPaint.setColor(getContext().getColor(R.color.text_tertiary));
    }

    private void drawXLabels(Canvas canvas, int n) {
        if (n < 2) {
            return;
        }
        labelPaint.setTextAlign(Paint.Align.LEFT);
        labelPaint.setColor(getContext().getColor(R.color.text_tertiary));
        canvas.drawText(Fmt.md(data.get(0).date), plot.left, plot.bottom + dp(15f), labelPaint);

        int mid = n / 2;
        labelPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(Fmt.md(data.get(mid).date), plot.centerX(), plot.bottom + dp(15f), labelPaint);

        labelPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(Fmt.md(data.get(n - 1).date), plot.right, plot.bottom + dp(15f), labelPaint);
    }

    private void drawTooltip(Canvas canvas, int index) {
        float x = xs[index];
        float y = ys[index];

        canvas.drawLine(x, plot.top, x, plot.bottom, guidePaint);

        String text = Fmt.md(data.get(index).date) + "  " + Fmt.kg(data.get(index).weight) + " kg";
        float textWidth = tipTextPaint.measureText(text);
        float width = textWidth + dp(18f);
        float height = dp(26f);
        float left = Math.max(plot.left, Math.min(plot.right - width, x - width / 2f));
        float top = y - height - dp(12f);
        if (top < 0) {
            top = y + dp(12f);
        }
        tipRect.set(left, top, left + width, top + height);
        canvas.drawRoundRect(tipRect, dp(9f), dp(9f), tipBgPaint);
        tipTextPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(text, left + width / 2f, top + height / 2f + dp(3.5f), tipTextPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (data.isEmpty() || xs.length == 0) {
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
        int nearest = -1;
        float best = Float.MAX_VALUE;
        for (int i = 0; i < xs.length; i++) {
            float d = Math.abs(xs[i] - x);
            if (d < best) {
                best = d;
                nearest = i;
            }
        }
        return nearest;
    }
}
