package com.weightdiary.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.weightdiary.R;
import com.weightdiary.core.Fmt;
import com.weightdiary.core.Slots;
import com.weightdiary.core.Stats;

import java.util.ArrayList;
import java.util.List;

/**
 * 六时段近 30 天均值横向对比。
 */
public class SlotAverageChartView extends View {

    private final List<Stats.SlotStat> stats = new ArrayList<>();

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public SlotAverageChartView(Context context) {
        super(context);
        init();
    }

    public SlotAverageChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        trackPaint.setColor(Color.parseColor("#12FFFFFF"));
        labelPaint.setTextSize(dp(9f));
        labelPaint.setTextAlign(Paint.Align.LEFT);
        valuePaint.setTextSize(dp(10f));
        valuePaint.setTextAlign(Paint.Align.RIGHT);
        hintPaint.setColor(Color.parseColor("#4DFFFFFF"));
        hintPaint.setTextSize(dp(11f));
        hintPaint.setTextAlign(Paint.Align.CENTER);
    }

    private float dp(float value) {
        return ChartUtils.dp(getContext(), value);
    }

    public void setStats(List<Stats.SlotStat> slotStats) {
        stats.clear();
        stats.addAll(slotStats);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (stats.size() < Slots.COUNT) {
            canvas.drawText("暂无时段数据", getWidth() / 2f, getHeight() / 2f, hintPaint);
            return;
        }

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (Stats.SlotStat s : stats) {
            if (!Double.isNaN(s.avg)) {
                min = Math.min(min, s.avg);
                max = Math.max(max, s.avg);
            }
        }
        if (Double.isNaN(min) || Double.isNaN(max)) {
            canvas.drawText("近 30 天暂无记录", getWidth() / 2f, getHeight() / 2f, hintPaint);
            return;
        }
        double span = Math.max(0.2, max - min);

        float labelW = dp(32f);
        float valueW = dp(42f);
        float trackLeft = labelW + dp(6f);
        float trackRight = getWidth() - valueW - dp(2f);
        float rowH = getHeight() / (float) Slots.COUNT;
        float barH = dp(7f);

        int primary = getContext().getColor(R.color.text_primary);
        int tertiary = getContext().getColor(R.color.text_tertiary);

        for (int i = 0; i < Slots.COUNT; i++) {
            Stats.SlotStat stat = stats.get(i);
            float cy = rowH * i + rowH / 2f;
            int color = Slots.color(getContext(), i);

            labelPaint.setColor(color);
            canvas.drawText(Slots.SHORT[i], 0f, cy + dp(3f), labelPaint);

            rect.set(trackLeft, cy - barH / 2f, trackRight, cy + barH / 2f);
            canvas.drawRoundRect(rect, barH / 2f, barH / 2f, trackPaint);

            if (!Double.isNaN(stat.avg)) {
                float norm = (float) ((stat.avg - min) / span);
                float width = (trackRight - trackLeft) * (0.12f + 0.88f * norm);
                rect.set(trackLeft, cy - barH / 2f, trackLeft + Math.max(dp(8f), width), cy + barH / 2f);
                barPaint.setColor(color);
                barPaint.setAlpha(235);
                canvas.drawRoundRect(rect, barH / 2f, barH / 2f, barPaint);
                valuePaint.setColor(primary);
                canvas.drawText(Fmt.kg(stat.avg), getWidth() - dp(2f), cy + dp(3.5f), valuePaint);
            } else {
                valuePaint.setColor(tertiary);
                canvas.drawText("—", getWidth() - dp(2f), cy + dp(3.5f), valuePaint);
            }
        }
    }
}
