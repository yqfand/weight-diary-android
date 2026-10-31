package com.weightdiary.model;

import java.time.LocalDate;

/**
 * 单条体重记录：日期 + 时段（一天最多六条）。
 */
public final class Record implements Comparable<Record> {

    public final LocalDate date;
    public final int slot;
    public final double weight;

    public Record(LocalDate date, int slot, double weight) {
        this.date = date;
        this.slot = slot;
        this.weight = round1(weight);
    }

    /**
     * 兼容仅按日期使用的场景（如日均曲线）。
     */
    public Record(LocalDate date, double weight) {
        this(date, 0, weight);
    }

    public static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    @Override
    public int compareTo(Record other) {
        int cmp = date.compareTo(other.date);
        return cmp != 0 ? cmp : Integer.compare(slot, other.slot);
    }
}
