package com.weightdiary.data;

import android.content.Context;

import java.util.Locale;

/**
 * 轻量设置：身高与目标体重，均可留空。
 */
public final class Prefs {

    private static final String NAME = "weight_diary_prefs";

    private final Context context;
    private final android.content.SharedPreferences sp;

    private Prefs(Context context) {
        this.context = context;
        this.sp = context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static Prefs get(Context context) {
        return new Prefs(context.getApplicationContext());
    }

    public double heightCm() {
        return parse(sp.getString("height", ""));
    }

    public void setHeightCm(double value) {
        sp.edit().putString("height", value <= 0 ? "" : String.format(Locale.US, "%.0f", value)).apply();
    }

    public double targetKg() {
        return parse(sp.getString("target", ""));
    }

    public void setTargetKg(double value) {
        sp.edit().putString("target", value <= 0 ? "" : String.format(Locale.US, "%.1f", value)).apply();
    }

    private double parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return 0.0;
        }
        try {
            double v = Double.parseDouble(raw.trim());
            return (v > 0 && v < 1000) ? v : 0.0;
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
