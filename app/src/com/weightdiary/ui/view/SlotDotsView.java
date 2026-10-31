package com.weightdiary.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.weightdiary.core.Slots;
import com.weightdiary.model.Record;

import java.util.ArrayList;
import java.util.List;

/**
 * 记录列表里的六时段小圆点，用于快速看出当天哪些时段已记录。
 */
public class SlotDotsView extends View {

    private final boolean[] present = new boolean[Slots.COUNT];
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public SlotDotsView(Context context) {
        super(context);
        init();
    }

    public SlotDotsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        fillPaint.setStyle(Paint.Style.FILL);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(ChartUtils.dp(getContext(), 1f));
        ringPaint.setColor(Color.parseColor("#26FFFFFF"));
    }

    public void setDay(List<Record> records) {
        for (int i = 0; i < Slots.COUNT; i++) {
            present[i] = false;
        }
        for (Record r : records) {
            present[Slots.clamp(r.slot)] = true;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float r = Math.min(ChartUtils.dp(getContext(), 2.2f), getHeight() / 2f - ChartUtils.dp(getContext(), 1f));
        float gap = (getWidth() - r * 2f * Slots.COUNT) / (Slots.COUNT - 1);
        float cy = getHeight() / 2f;
        for (int i = 0; i < Slots.COUNT; i++) {
            float cx = r + i * (r * 2f + gap);
            if (present[i]) {
                fillPaint.setColor(Slots.color(getContext(), i));
                canvas.drawCircle(cx, cy, r, fillPaint);
            } else {
                canvas.drawCircle(cx, cy, r, ringPaint);
            }
        }
    }
}
