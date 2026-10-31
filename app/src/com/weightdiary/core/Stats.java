package com.weightdiary.core;

import com.weightdiary.model.Record;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 统计与分析引擎：以「每日均值」为核心，同时抽取六时段的平行指标。
 */
public final class Stats {

    public static final class DayPoint {
        public final LocalDate date;
        public final double avg;
        public final double min;
        public final double max;
        public final int count;

        DayPoint(LocalDate date, double avg, double min, double max, int count) {
            this.date = date;
            this.avg = avg;
            this.min = min;
            this.max = max;
            this.count = count;
        }
    }

    public static final class WeekPoint {
        public final LocalDate start;
        public final double avg;
        public final int days;
        public final double delta;

        WeekPoint(LocalDate start, double avg, int days, double delta) {
            this.start = start;
            this.avg = avg;
            this.days = days;
            this.delta = delta;
        }
    }

    public static final class SlotStat {
        public final int slot;
        public final double avg;
        public final double min;
        public final double max;
        public final int count;
        public final double latest;
        public final LocalDate latestDate;

        SlotStat(int slot, double avg, double min, double max, int count,
                 double latest, LocalDate latestDate) {
            this.slot = slot;
            this.avg = avg;
            this.min = min;
            this.max = max;
            this.count = count;
            this.latest = latest;
            this.latestDate = latestDate;
        }
    }

    public static final class MealDelta {
        public final int meal;
        public final double avgDelta;
        public final int days;

        MealDelta(int meal, double avgDelta, int days) {
            this.meal = meal;
            this.avgDelta = avgDelta;
            this.days = days;
        }
    }

    public final LocalDate today;
    public final List<Record> records;
    public final boolean hasData;
    public final int recordCount;
    public final int dayCount;

    public final List<DayPoint> days;
    public final LocalDate latestDay;
    public final double latestAvg;
    public final double latestMin;
    public final double latestMax;
    public final int latestCount;

    public final int todayCount;
    public final double todayAvg;
    public final double todayMin;
    public final double todayMax;
    public final LocalDate prevDay;
    public final double prevDayAvg;

    public final double firstAvg;
    public final double totalChange;
    public final double minAvg;
    public final double maxAvg;
    public final double meanOfDays;
    public final double stdDevAvg;
    public final double medianAvg;

    public final int streakDays;
    public final int recordedLast30;
    public final int recordsLast30;
    public final int recordsLast7;
    public final double completion7;

    public final double slopePerDay;
    public final double r2;
    public final double weeklyRate;

    public final double weekAvg;
    public final double prevWeekAvg;
    public final double weekDelta;

    public final double volatility;
    public final List<WeekPoint> weeks;

    public final List<SlotStat> slots;
    public final List<MealDelta> meals;

    public final double withinDayRangeAvg;
    public final double latestDayRange;
    public final double todayRange;

    public final double target;
    public final double heightCm;
    public final double bmi;
    public final double goalProgress;
    public final LocalDate predictDate;

    private Stats(LocalDate today, List<Record> records, double target, double heightCm) {
        this.today = today;
        this.records = records;
        this.recordCount = records.size();
        this.hasData = recordCount > 0;

        // 按日期聚合
        List<DayPoint> dayPoints = new ArrayList<>();
        LocalDate curDate = null;
        double sum = 0.0;
        int cnt = 0;
        double lo = Double.MAX_VALUE;
        double hi = -Double.MAX_VALUE;
        for (Record r : records) {
            if (curDate == null || !r.date.equals(curDate)) {
                if (curDate != null) {
                    dayPoints.add(new DayPoint(curDate, sum / cnt, lo, hi, cnt));
                }
                curDate = r.date;
                sum = 0.0;
                cnt = 0;
                lo = Double.MAX_VALUE;
                hi = -Double.MAX_VALUE;
            }
            sum += r.weight;
            cnt++;
            lo = Math.min(lo, r.weight);
            hi = Math.max(hi, r.weight);
        }
        if (curDate != null) {
            dayPoints.add(new DayPoint(curDate, sum / cnt, lo, hi, cnt));
        }
        this.days = Collections.unmodifiableList(dayPoints);
        this.dayCount = dayPoints.size();

        double latestVal = Double.NaN;
        double latestMinVal = Double.NaN;
        double latestMaxVal = Double.NaN;
        int latestCnt = 0;
        LocalDate latestDt = today;
        if (!dayPoints.isEmpty()) {
            DayPoint last = dayPoints.get(dayPoints.size() - 1);
            latestVal = last.avg;
            latestMinVal = last.min;
            latestMaxVal = last.max;
            latestCnt = last.count;
            latestDt = last.date;
        }
        this.latestAvg = latestVal;
        this.latestMin = latestMinVal;
        this.latestMax = latestMaxVal;
        this.latestCount = latestCnt;
        this.latestDay = latestDt;

        // 今天
        int tCnt = 0;
        double tSum = 0.0;
        double tLo = Double.MAX_VALUE;
        double tHi = -Double.MAX_VALUE;
        for (Record r : records) {
            if (r.date.equals(today)) {
                tCnt++;
                tSum += r.weight;
                tLo = Math.min(tLo, r.weight);
                tHi = Math.max(tHi, r.weight);
            }
        }
        this.todayCount = tCnt;
        this.todayAvg = tCnt > 0 ? tSum / tCnt : Double.NaN;
        this.todayMin = tCnt > 0 ? tLo : Double.NaN;
        this.todayMax = tCnt > 0 ? tHi : Double.NaN;
        this.todayRange = tCnt >= 2 ? tHi - tLo : Double.NaN;

        // 今天之前最近的一天
        LocalDate previousDate = null;
        double previousAvg = Double.NaN;
        for (int i = dayPoints.size() - 1; i >= 0; i--) {
            DayPoint p = dayPoints.get(i);
            if (p.date.isBefore(today)) {
                previousDate = p.date;
                previousAvg = p.avg;
                break;
            }
        }
        this.prevDay = previousDate;
        this.prevDayAvg = previousAvg;

        double first = Double.NaN;
        double total = Double.NaN;
        double minDay = Double.NaN;
        double maxDay = Double.NaN;
        double meanDays = Double.NaN;
        double stdDays = Double.NaN;
        double medianDays = Double.NaN;
        if (!dayPoints.isEmpty()) {
            first = dayPoints.get(0).avg;
            total = latestVal - first;
            minDay = Double.MAX_VALUE;
            maxDay = -Double.MAX_VALUE;
            double acc = 0.0;
            List<Double> avgs = new ArrayList<>(dayPoints.size());
            for (DayPoint p : dayPoints) {
                minDay = Math.min(minDay, p.avg);
                maxDay = Math.max(maxDay, p.avg);
                acc += p.avg;
                avgs.add(p.avg);
            }
            meanDays = acc / dayPoints.size();
            Collections.sort(avgs);
            int n = avgs.size();
            medianDays = (n % 2 == 1) ? avgs.get(n / 2)
                    : (avgs.get(n / 2 - 1) + avgs.get(n / 2)) / 2.0;
            if (n >= 2) {
                double var = 0.0;
                for (double v : avgs) {
                    double d = v - meanDays;
                    var += d * d;
                }
                stdDays = Math.sqrt(var / (n - 1));
            } else {
                stdDays = 0.0;
            }
        }
        this.firstAvg = first;
        this.totalChange = total;
        this.minAvg = minDay;
        this.maxAvg = maxDay;
        this.meanOfDays = meanDays;
        this.stdDevAvg = stdDays;
        this.medianAvg = medianDays;

        // 连续记录天数
        Set<Long> dayEpoch = new HashSet<>();
        for (DayPoint p : dayPoints) {
            dayEpoch.add(p.date.toEpochDay());
        }
        int streak = 0;
        if (hasData) {
            LocalDate cursor = today;
            if (!dayEpoch.contains(cursor.toEpochDay())) {
                cursor = latestDt;
            }
            while (dayEpoch.contains(cursor.toEpochDay())) {
                streak++;
                cursor = cursor.minusDays(1);
            }
        }
        this.streakDays = streak;

        LocalDate from30 = today.minusDays(29);
        int recorded30 = 0;
        for (DayPoint p : dayPoints) {
            if (!p.date.isBefore(from30) && !p.date.isAfter(today)) {
                recorded30++;
            }
        }
        this.recordedLast30 = recorded30;

        int rec30 = 0;
        int rec7 = 0;
        for (Record r : records) {
            if (!r.date.isBefore(from30) && !r.date.isAfter(today)) {
                rec30++;
            }
            if (!r.date.isBefore(today.minusDays(6)) && !r.date.isAfter(today)) {
                rec7++;
            }
        }
        this.recordsLast30 = rec30;
        this.recordsLast7 = rec7;
        this.completion7 = rec7 / 42.0;

        // 日均线性回归（近 30 天，样本不足用全部）
        List<DayPoint> window = new ArrayList<>();
        for (DayPoint p : dayPoints) {
            if (!p.date.isBefore(from30) && !p.date.isAfter(today)) {
                window.add(p);
            }
        }
        if (window.size() < 3) {
            window = dayPoints;
        }
        double slope = Double.NaN;
        double rsq = Double.NaN;
        if (window.size() >= 2) {
            long base = window.get(0).date.toEpochDay();
            int n = window.size();
            double mx = 0.0;
            double my = 0.0;
            for (DayPoint p : window) {
                mx += (double) (p.date.toEpochDay() - base);
                my += p.avg;
            }
            mx /= n;
            my /= n;
            double sxx = 0.0;
            double sxy = 0.0;
            double syy = 0.0;
            for (DayPoint p : window) {
                double dx = (double) (p.date.toEpochDay() - base) - mx;
                double dy = p.avg - my;
                sxx += dx * dx;
                sxy += dx * dy;
                syy += dy * dy;
            }
            if (sxx > 0) {
                slope = sxy / sxx;
                if (syy > 0) {
                    double rr = sxy * sxy / (sxx * syy);
                    rsq = Math.max(0.0, Math.min(1.0, rr));
                } else {
                    rsq = 1.0;
                }
            }
        }
        this.slopePerDay = slope;
        this.r2 = rsq;
        this.weeklyRate = Double.isNaN(slope) ? Double.NaN : slope * 7.0;

        // 周对比
        LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        double thisWeek = meanDayAvg(thisMonday, thisMonday.plusDays(6));
        double lastWeek = meanDayAvg(thisMonday.minusWeeks(1), thisMonday.minusDays(1));
        this.weekAvg = thisWeek;
        this.prevWeekAvg = lastWeek;
        this.weekDelta = (!Double.isNaN(thisWeek) && !Double.isNaN(lastWeek))
                ? thisWeek - lastWeek : Double.NaN;

        // 周序列
        List<WeekPoint> points = new ArrayList<>();
        double prevAvailable = Double.NaN;
        for (int k = 7; k >= 0; k--) {
            LocalDate start = thisMonday.minusWeeks(k);
            LocalDate end = start.plusDays(6);
            double avg = meanDayAvg(start, end);
            if (Double.isNaN(avg)) {
                continue;
            }
            int daysInWeek = 0;
            for (DayPoint p : dayPoints) {
                if (!p.date.isBefore(start) && !p.date.isAfter(end)) {
                    daysInWeek++;
                }
            }
            double delta = Double.isNaN(prevAvailable) ? Double.NaN : avg - prevAvailable;
            points.add(new WeekPoint(start, avg, daysInWeek, delta));
            prevAvailable = avg;
        }
        this.weeks = Collections.unmodifiableList(points);

        // 日间波动
        double vol = Double.NaN;
        List<DayPoint> recentDays = new ArrayList<>();
        for (DayPoint p : dayPoints) {
            if (!p.date.isBefore(from30) && !p.date.isAfter(today)) {
                recentDays.add(p);
            }
        }
        List<Double> diffs = new ArrayList<>();
        for (int i = 1; i < recentDays.size(); i++) {
            DayPoint a = recentDays.get(i - 1);
            DayPoint b = recentDays.get(i);
            if (b.date.toEpochDay() - a.date.toEpochDay() == 1) {
                diffs.add(b.avg - a.avg);
            }
        }
        if (diffs.size() >= 2) {
            double mean = 0.0;
            for (double d : diffs) {
                mean += d;
            }
            mean /= diffs.size();
            double acc = 0.0;
            for (double d : diffs) {
                acc += (d - mean) * (d - mean);
            }
            vol = Math.sqrt(acc / (diffs.size() - 1));
        }
        this.volatility = vol;

        // 六时段统计（近 30 天）
        double[] sSum = new double[Slots.COUNT];
        int[] sCount = new int[Slots.COUNT];
        double[] sMin = new double[Slots.COUNT];
        double[] sMax = new double[Slots.COUNT];
        double[] sLatest = new double[Slots.COUNT];
        LocalDate[] sLatestDate = new LocalDate[Slots.COUNT];
        for (int i = 0; i < Slots.COUNT; i++) {
            sMin[i] = Double.MAX_VALUE;
            sMax[i] = -Double.MAX_VALUE;
            sLatest[i] = Double.NaN;
        }
        for (Record r : records) {
            if (r.date.isBefore(from30) || r.date.isAfter(today)) {
                continue;
            }
            int s = Slots.clamp(r.slot);
            sSum[s] += r.weight;
            sCount[s]++;
            sMin[s] = Math.min(sMin[s], r.weight);
            sMax[s] = Math.max(sMax[s], r.weight);
        }
        for (Record r : records) {
            int s = Slots.clamp(r.slot);
            sLatest[s] = r.weight;
            sLatestDate[s] = r.date;
        }
        List<SlotStat> slotStats = new ArrayList<>(Slots.COUNT);
        for (int i = 0; i < Slots.COUNT; i++) {
            if (sCount[i] > 0) {
                slotStats.add(new SlotStat(i, sSum[i] / sCount[i],
                        sMin[i], sMax[i], sCount[i], sLatest[i], sLatestDate[i]));
            } else {
                slotStats.add(new SlotStat(i, Double.NaN, Double.NaN, Double.NaN,
                        0, sLatest[i], sLatestDate[i]));
            }
        }
        this.slots = Collections.unmodifiableList(slotStats);

        // 餐前 / 餐后差值
        Map<LocalDate, double[]> daySlots = new HashMap<>();
        for (Record r : records) {
            if (r.date.isBefore(from30) || r.date.isAfter(today)) {
                continue;
            }
            double[] arr = daySlots.get(r.date);
            if (arr == null) {
                arr = new double[Slots.COUNT];
                for (int i = 0; i < Slots.COUNT; i++) {
                    arr[i] = Double.NaN;
                }
                daySlots.put(r.date, arr);
            }
            arr[Slots.clamp(r.slot)] = r.weight;
        }
        List<MealDelta> mealDeltas = new ArrayList<>(3);
        for (int meal = 0; meal < 3; meal++) {
            double acc = 0.0;
            int n = 0;
            for (double[] arr : daySlots.values()) {
                double pre = arr[meal * 2];
                double post = arr[meal * 2 + 1];
                if (!Double.isNaN(pre) && !Double.isNaN(post)) {
                    acc += post - pre;
                    n++;
                }
            }
            mealDeltas.add(new MealDelta(meal, n > 0 ? acc / n : Double.NaN, n));
        }
        this.meals = Collections.unmodifiableList(mealDeltas);

        // 日内极差
        double rangeSum = 0.0;
        int rangeCount = 0;
        for (DayPoint p : recentDays) {
            if (p.count >= 2) {
                rangeSum += (p.max - p.min);
                rangeCount++;
            }
        }
        this.withinDayRangeAvg = rangeCount > 0 ? rangeSum / rangeCount : Double.NaN;
        this.latestDayRange = (latestCnt >= 2) ? latestMaxVal - latestMinVal : Double.NaN;

        this.target = target;
        this.heightCm = heightCm;

        double bmiVal = Double.NaN;
        if (hasData && heightCm > 0) {
            double h = heightCm / 100.0;
            bmiVal = latestVal / (h * h);
        }
        this.bmi = bmiVal;

        double progress = Double.NaN;
        if (hasData && target > 0) {
            if (Math.abs(first - target) < 0.05) {
                progress = latestVal <= target ? 1.0 : 0.0;
            } else if (first > target) {
                progress = (first - latestVal) / (first - target);
            } else {
                progress = (latestVal - first) / (target - first);
            }
            progress = Math.max(0.0, Math.min(1.0, progress));
        }
        this.goalProgress = progress;

        LocalDate predict = null;
        if (hasData && target > 0 && !Double.isNaN(slope) && Math.abs(slope) > 0.002) {
            double toTargetDays = (target - latestVal) / slope;
            if (toTargetDays > 0 && toTargetDays < 3650) {
                predict = latestDt.plusDays((long) Math.ceil(toTargetDays));
            }
        }
        this.predictDate = predict;
    }

    public static Stats compute(List<Record> input, double targetKg, double heightCm) {
        List<Record> sorted = new ArrayList<>(input);
        Collections.sort(sorted);
        return new Stats(LocalDate.now(), Collections.unmodifiableList(sorted), targetKg, heightCm);
    }

    private double meanDayAvg(LocalDate from, LocalDate to) {
        double sum = 0.0;
        int n = 0;
        for (DayPoint p : days) {
            if (!p.date.isBefore(from) && !p.date.isAfter(to)) {
                sum += p.avg;
                n++;
            }
        }
        return n == 0 ? Double.NaN : sum / n;
    }

    public String bmiLabel() {
        if (Double.isNaN(bmi)) {
            return "未设置身高";
        }
        if (bmi < 18.5) {
            return "偏瘦";
        }
        if (bmi < 24.0) {
            return "正常";
        }
        if (bmi < 28.0) {
            return "超重";
        }
        return "肥胖";
    }

    public double remainingToTarget() {
        if (target <= 0 || !hasData) {
            return Double.NaN;
        }
        return latestAvg - target;
    }

    public List<String> insights() {
        List<String> out = new ArrayList<>();
        if (!hasData) {
            out.add("记录第一组体重（早餐前），日均、平行曲线与分析会自动生成。");
            return out;
        }
        if (!Double.isNaN(weekDelta)) {
            if (Math.abs(weekDelta) < 0.05) {
                out.add("本周日均 " + Fmt.kg(weekAvg) + " kg，与上周基本持平。");
            } else {
                out.add("本周日均 " + Fmt.kg(weekAvg) + " kg，较上周"
                        + (weekDelta < 0 ? "下降 " : "上升 ")
                        + Fmt.kg(Math.abs(weekDelta)) + " kg。");
            }
        }
        if (predictDate != null && target > 0) {
            out.add("按近 30 天日均速度，预计 " + Fmt.cn(predictDate)
                    + " 达成 " + Fmt.kg(target) + " kg。");
        }
        if (recordsLast7 < 42) {
            out.add("近 7 天完成 " + recordsLast7 + " / 42 次记录，今日 "
                    + todayCount + " / 6 次。");
        }
        MealDelta strongest = null;
        for (MealDelta m : meals) {
            if (m.days >= 3 && !Double.isNaN(m.avgDelta)) {
                if (strongest == null || Math.abs(m.avgDelta) > Math.abs(strongest.avgDelta)) {
                    strongest = m;
                }
            }
        }
        if (strongest != null) {
            out.add(Slots.mealName(strongest.meal) + "后较" + Slots.mealName(strongest.meal)
                    + "前平均 " + Fmt.signedKg(strongest.avgDelta) + "（" + strongest.days + " 天）。");
        }
        if (!Double.isNaN(withinDayRangeAvg) && withinDayRangeAvg >= 1.0) {
            out.add("日内极差平均 " + Fmt.kg(withinDayRangeAvg)
                    + " kg，建议固定测量条件与顺序。");
        }
        if (streakDays >= 3) {
            out.add("已连续记录 " + streakDays + " 天。");
        }
        if (out.isEmpty()) {
            out.add("数据平稳，暂无明显趋势变化，保持记录即可。");
        }
        return new ArrayList<>(out.subList(0, Math.min(4, out.size())));
    }
}
