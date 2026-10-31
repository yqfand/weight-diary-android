package com.weightdiary.core;

import android.content.Context;

import com.weightdiary.R;

/**
 * 一天七次的固定测量时段（三餐前后 + 睡前）。
 */
public final class Slots {

    public static final int COUNT = 7;

    public static final int BREAKFAST_BEFORE = 0;
    public static final int BREAKFAST_AFTER = 1;
    public static final int LUNCH_BEFORE = 2;
    public static final int LUNCH_AFTER = 3;
    public static final int DINNER_BEFORE = 4;
    public static final int DINNER_AFTER = 5;
    public static final int BEDTIME = 6;

    public static final String[] NAME = {"早餐前", "早餐后", "午餐前", "午餐后", "晚餐前", "晚餐后", "睡前"};
    public static final String[] SHORT = {"早前", "早后", "午前", "午后", "晚前", "晚后", "睡前"};
    public static final String[] MEAL = {"早餐", "午餐", "晚餐"};

    private static final int[] COLOR_RES = {
            R.color.slot0, R.color.slot1, R.color.slot2,
            R.color.slot3, R.color.slot4, R.color.slot5, R.color.slot6
    };

    private Slots() {
    }

    public static int color(Context context, int slot) {
        return context.getColor(COLOR_RES[clamp(slot)]);
    }

    public static int clamp(int slot) {
        return Math.max(0, Math.min(COUNT - 1, slot));
    }

    public static int mealOf(int slot) {
        return clamp(slot) / 2;
    }

    public static boolean afterMeal(int slot) {
        return clamp(slot) % 2 == 1;
    }

    public static String name(int slot) {
        return NAME[clamp(slot)];
    }

    public static String shortName(int slot) {
        return SHORT[clamp(slot)];
    }

    public static String mealName(int meal) {
        return MEAL[Math.max(0, Math.min(2, meal))];
    }
}
