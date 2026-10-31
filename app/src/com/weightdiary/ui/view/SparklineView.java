package com.weightdiary.ui.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.weightdiary.R;
import com.weightdiary.model.Record;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页的迷你趋势线。
 */
public class SparklineView extends View {

    private final List<Record> data = new ArrayList<>();
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final Path areaPath = new Path();
    private float reveal = 1f;
    private ValueAnimator animator;

    public SparklineView(Context context) {
        super(context);
        init();
    }

    public SparklineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(ChartUtils.dp(getContext(), 2f));
        linePaint.setColor(getContext().getColor(R.color.mint));
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(getContext().getColor(R.color.mint_bright));

        hintPaint.setColor(Color.parseColor("#5AFFFFFF"));
        hintPaint.setTextSize(ChartUtils.dp(getContext(), 11f));
        hintPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setData(List<Record> records) {
        data.clear();
        data.addAll(records);
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(600);
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
            canvas.drawText("还没有足够的数据", getWidth() / 2f, getHeight() / 2f + ChartUtils.dp(getContext(), 4f), hintPaint);
            return;
        }

        float pad = ChartUtils.dp(getContext(), 6f);
        float left = pad;
        float right = getWidth() - pad;
        float top = pad + ChartUtils.dp(getContext(), 4f);
        float bottom = getHeight() - pad - ChartUtils.dp(getContext(), 4f);

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (Record r : data) {
            min = Math.min(min, r.weight);
            max = Math.max(max, r.weight);
        }
        if (max - min < 0.3) {
            double mid = (max + min) / 2.0;
            min = mid - 0.15;
            max = mid + 0.15;
        }
        double span = max - min;

        int n = data.size();
        long first = data.get(0).date.toEpochDay();
        long last = data.get(n - 1).date.toEpochDay();
        long daySpan = Math.max(1, last - first);

        float[] xs = new float[n];
        float[] ys = new float[n];
        for (int i = 0; i < n; i++) {
            xs[i] = left + (data.get(i).date.toEpochDay() - first) / (float) daySpan * (right - left);
            ys[i] = top + (float) ((max - data.get(i).weight) / span * (bottom - top));
        }

        linePath.reset();
        areaPath.reset();
        if (n == 1) {
            xs[0] = (left + right) / 2f;
            ys[0] = (top + bottom) / 2f;
            linePath.moveTo(xs[0], ys[0]);
        } else {
            ChartUtils.smoothPath(linePath, xs, ys);
            areaPath.set(linePath);
            areaPath.lineTo(xs[n - 1], bottom + ChartUtils.dp(getContext(), 6f));
            areaPath.lineTo(xs[0], bottom + ChartUtils.dp(getContext(), 6f));
            areaPath.close();
        }

        fillPaint.setShader(new LinearGradient(0f, top, 0f, bottom + ChartUtils.dp(getContext(), 6f),
                new int[]{Color.parseColor("#4646E0A8"), Color.parseColor("#0046E0A8")},
                null, Shader.TileMode.CLAMP));

        canvas.save();
        canvas.clipRect(left - ChartUtils.dp(getContext(), 2f), 0f,
                left + (right - left + ChartUtils.dp(getContext(), 4f)) * reveal, getHeight());
        if (n > 1) {
            canvas.drawPath(areaPath, fillPaint);
        }
        canvas.drawPath(linePath, linePaint);
        canvas.restore();

        // 末端高亮
        if (reveal > 0.65f) {
            float lx = xs[n - 1];
            float ly = ys[n - 1];
            dotPaint.setAlpha(70);
            canvas.drawCircle(lx, ly, ChartUtils.dp(getContext(), 7f), dotPaint);
            dotPaint.setAlpha(255);
            canvas.drawCircle(lx, ly, ChartUtils.dp(getContext(), 3.2f), dotPaint);
        }
    }
}
