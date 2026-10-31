package com.weightdiary.ui.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.weightdiary.R;

public class RingProgressView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();

    private float displayed = 0f;
    private ValueAnimator animator;
    private SweepGradient gradient;
    private final Matrix gradientMatrix = new Matrix();

    public RingProgressView(Context context) {
        super(context);
        init();
    }

    public RingProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        float stroke = ChartUtils.dp(getContext(), 7f);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(stroke);
        trackPaint.setColor(Color.parseColor("#1AFFFFFF"));
        trackPaint.setStrokeCap(Paint.Cap.ROUND);

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(stroke);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setProgress(float progress, boolean animate) {
        float target = Math.max(0f, Math.min(1f, progress));
        if (animator != null) {
            animator.cancel();
        }
        if (!animate) {
            displayed = target;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(displayed, target);
        animator.setDuration(650);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            displayed = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            gradient = new SweepGradient(w / 2f, h / 2f,
                    new int[]{getContext().getColor(R.color.mint_bright),
                            getContext().getColor(R.color.mint),
                            getContext().getColor(R.color.violet)},
                    new float[]{0f, 0.55f, 1f});
            gradientMatrix.setRotate(-90f, w / 2f, h / 2f);
            gradient.setLocalMatrix(gradientMatrix);
            arcPaint.setShader(gradient);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float stroke = arcPaint.getStrokeWidth();
        float inset = stroke / 2f + ChartUtils.dp(getContext(), 2f);
        arcRect.set(inset, inset, getWidth() - inset, getHeight() - inset);

        canvas.drawArc(arcRect, 0f, 360f, false, trackPaint);
        if (displayed > 0.001f) {
            canvas.drawArc(arcRect, -90f, 360f * displayed, false, arcPaint);
        }
    }
}
