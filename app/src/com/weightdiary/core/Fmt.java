package com.weightdiary.core;

import java.time.LocalDate;
import java.util.Locale;

/**
 * 统一的文案与数字格式化。
 */
public final class Fmt {

    private static final String[] WEEK_CN = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private Fmt() {
    }

    public static String kg(double value) {
        if (Double.isNaN(value)) {
            return "—";
        }
        return String.format(Locale.US, "%.1f", value);
    }

    public static String signed(double value) {
        if (Double.isNaN(value)) {
            return "—";
        }
        if (Math.abs(value) < 0.05) {
            return "0.0";
        }
        return (value > 0 ? "+" : "−") + String.format(Locale.US, "%.1f", Math.abs(value));
    }

    public static String signedKg(double value) {
        return signed(value) + " kg";
    }

    public static String md(LocalDate date) {
        return date.getMonthValue() + "/" + date.getDayOfMonth();
    }

    public static String cn(LocalDate date) {
        return date.getMonthValue() + "月" + date.getDayOfMonth() + "日";
    }

    public static String week(LocalDate date) {
        return WEEK_CN[date.getDayOfWeek().getValue() - 1];
    }

    public static String fullCn(LocalDate date) {
        return date.getYear() + "年" + cn(date) + " " + week(date);
    }

    public static String monthCn(LocalDate date) {
        return date.getYear() + "年" + date.getMonthValue() + "月";
    }

    public static String percent(double ratio) {
        if (Double.isNaN(ratio)) {
            return "—";
        }
        return String.format(Locale.US, "%.0f%%", ratio * 100);
    }
}
