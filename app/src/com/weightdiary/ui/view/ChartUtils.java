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
     * 用三次贝塞尔在相邻点之间做平滑，控制点限制在两端点的纵向区间内，避免过冲。
     */
    public static void smoothPath(Path path, float[] xs, float[] ys) {
        int n = xs.length;
        if (n < 2) {
            return;
        }
        path.moveTo(xs[0], ys[0]);
        for (int i = 0; i < n - 1; i++) {
            float dx = xs[i + 1] - xs[i];
            float m0 = slope(xs, ys, i);
            float m1 = slope(xs, ys, i + 1);
            float c1x = xs[i] + dx / 3f;
            float c1y = ys[i] + m0 * dx / 3f;
            float c2x = xs[i + 1] - dx / 3f;
            float c2y = ys[i + 1] - m1 * dx / 3f;
            float lo = Math.min(ys[i], ys[i + 1]);
            float hi = Math.max(ys[i], ys[i + 1]);
            c1y = Math.max(lo, Math.min(hi, c1y));
            c2y = Math.max(lo, Math.min(hi, c2y));
            path.cubicTo(c1x, c1y, c2x, c2y, xs[i + 1], ys[i + 1]);
        }
    }

    private static float slope(float[] xs, float[] ys, int i) {
        int n = xs.length;
        if (i == 0) {
            return safeDiv(ys[1] - ys[0], xs[1] - xs[0]);
        }
        if (i == n - 1) {
            return safeDiv(ys[n - 1] - ys[n - 2], xs[n - 1] - xs[n - 2]);
        }
        return safeDiv(ys[i + 1] - ys[i - 1], xs[i + 1] - xs[i - 1]);
    }

    private static float safeDiv(float a, float b) {
        return Math.abs(b) < 0.0001f ? 0f : a / b;
    }
}
