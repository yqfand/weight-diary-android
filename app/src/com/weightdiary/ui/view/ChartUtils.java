package com.weightdiary.ui.view;

import android.content.Context;
import android.graphics.Path;

public final class ChartUtils {

    private ChartUtils() {
    }

    public static float dp(Context context, float value) {
        return value * context.getResources().getDisplayMetrics().density;
    }

    /**
     * 用 Catmull-Rom 转三次贝塞尔做平滑：控制点由相邻点决定，曲线在节点处保持连续切线。
     */
    public static void smoothPath(Path path, float[] xs, float[] ys) {
        int n = xs.length;
        if (n < 2) {
            return;
        }
        path.moveTo(xs[0], ys[0]);
        for (int i = 0; i < n - 1; i++) {
            float p0x = i > 0 ? xs[i - 1] : xs[i];
            float p0y = i > 0 ? ys[i - 1] : ys[i];
            float p3x = i + 2 < n ? xs[i + 2] : xs[i + 1];
            float p3y = i + 2 < n ? ys[i + 2] : ys[i + 1];

            float c1x = xs[i] + (xs[i + 1] - p0x) / 6f;
            float c1y = ys[i] + (ys[i + 1] - p0y) / 6f;
            float c2x = xs[i + 1] - (p3x - xs[i]) / 6f;
            float c2y = ys[i + 1] - (p3y - ys[i]) / 6f;

            path.cubicTo(c1x, c1y, c2x, c2y, xs[i + 1], ys[i + 1]);
        }
    }
}
