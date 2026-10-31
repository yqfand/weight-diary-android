package com.weightdiary.ui.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.weightdiary.R;
import com.weightdiary.core.Fmt;
import com.weightdiary.core.Stats;

import java.util.ArrayList;
import java.util.List;

/**
 * 每周平均体重的变化量柱状图（相对上一周）。
 */
public class WeeklyBarChartView extends View {

    private final List<Stats.WeekPoint> weeks = new ArrayList<>();

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private float reveal = 0f;
    private ValueAnimator animator;

    public WeeklyBarChartView(Context context) {
        super(context);
        init();
    }

    public WeeklyBarChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        basePaint.setStyle(Paint.Style.STROKE);
        basePaint.setStrokeWidth(dp(1f));
        basePaint.setColor(Color.parseColor("#33FFFFFF"));

        textPaint.setColor(getContext().getColor(R.color.text_tertiary));
        textPaint.setTextSize(dp(9f));
        textPaint.setTextAlign(Paint.Align.CENTER);

        valuePaint.setTextSize(dp(9f));
        valuePaint.setTextAlign(Paint.Align.CENTER);

        hintPaint.setColor(Color.parseColor("#4DFFFFFF"));
        hintPaint.setTextSize(dp(11f));
        hintPaint.setTextAlign(Paint.Align.CENTER);
    }

    private float dp(float value) {
        return ChartUtils.dp(getContext(), value);
    }

    public void setWeeks(List<Stats.WeekPoint> points) {
        weeks.clear();
        weeks.addAll(points);
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(650);
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

        if (weeks.size() < 2) {
            hintPaint.setColor(Color.parseColor("#4DFFFFFF"));
            canvas.drawText("记录满两周后，这里会显示每周变化", getWidth() / 2f, getHeight() / 2f, hintPaint);
            return;
        }

        float left = dp(6f);
        float right = getWidth() - dp(6f);
        float top = dp(20f);
        float bottom = getHeight() - dp(20f);
        float zeroY = (top + bottom) / 2f + dp(2f);
        float maxAbs = 0.3f;

        for (Stats.WeekPoint p : weeks) {
            if (!Double.isNaN(p.delta)) {
                maxAbs = (float) Math.max(maxAbs, Math.abs(p.delta));
            }
        }
        maxAbs *= 1.18f;

        canvas.drawLine(left, zeroY, right, zeroY, basePaint);

        float slot = (right - left) / weeks.size();
        float barW = Math.min(dp(18f), slot * 0.52f);
        float fullH = Math.max(dp(10f), Math.min(zeroY - top, bottom - zeroY));

        int labelColor = getContext().getColor(R.color.text_tertiary);
        int todayWeek = getContext().getColor(R.color.mint);
        int lastIndex = weeks.size() - 1;

        for (int i = 0; i < weeks.size(); i++) {
            Stats.WeekPoint p = weeks.get(i);
            float cx = left + slot * i + slot / 2f;

            if (!Double.isNaN(p.delta) && Math.abs(p.delta) >= 0.05) {
                float h = (float) (Math.abs(p.delta) / maxAbs) * fullH * reveal;
                h = Math.max(dp(2f), h);
                float barTop;
                float barBottom;
                if (p.delta > 0) {
                    barTop = zeroY - h;
                    barBottom = zeroY;
                    barPaint.setColor(getContext().getColor(R.color.coral));
                } else {
                    barTop = zeroY;
                    barBottom = zeroY + h;
                    barPaint.setColor(getContext().getColor(R.color.mint));
                }
                rect.set(cx - barW / 2f, barTop, cx + barW / 2f, barBottom);
                canvas.drawRoundRect(rect, dp(5f), dp(5f), barPaint);

                valuePaint.setColor(p.delta > 0
                        ? getContext().getColor(R.color.coral)
                        : getContext().getColor(R.color.mint));
                String value = Fmt.signed(p.delta);
                float vine = p.delta > 0 ? barTop - dp(6f) : barBottom + dp(12f);
                canvas.drawText(value, cx, Math.max(dp(12f), Math.min(getHeight() - dp(4f), vine)), valuePaint);
            } else if (Double.isNaN(p.delta)) {
                barPaint.setColor(Color.parseColor("#1FFFFFFF"));
                rect.set(cx - barW / 2f, zeroY - dp(1f), cx + barW / 2f, zeroY + dp(1f));
                canvas.drawRoundRect(rect, dp(1f), dp(1f), barPaint);
            }

            textPaint.setColor(i == lastIndex ? todayWeek : labelColor);
            String label = i == lastIndex ? "本周" : Fmt.md(p.start);
            canvas.drawText(label, cx, getHeight() - dp(4f), textPaint);
        }

        textPaint.setColor(Color.parseColor("#66FFFFFF"));
        canvas.drawText("0 kg", left + dp(16f), zeroY - dp(5f), textPaint);
    }
}
