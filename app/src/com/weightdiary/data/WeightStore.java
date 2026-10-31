package com.weightdiary.data;

import android.content.Context;

import com.weightdiary.core.Slots;
import com.weightdiary.model.Record;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 本地 JSON 存储，按「日期 + 时段」保存每天最多 6 条记录。
 * 旧版本（每天一条、无 slot 字段）会自动迁移为「早餐前」。
 */
public final class WeightStore {

    private static final String FILE_NAME = "records.json";

    private static WeightStore instance;

    private final File file;
    private final List<Record> records = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();

    private WeightStore(Context context) {
        this.file = new File(context.getFilesDir(), FILE_NAME);
        load();
    }

    public static synchronized WeightStore get(Context context) {
        if (instance == null) {
            instance = new WeightStore(context.getApplicationContext());
        }
        return instance;
    }

    public List<Record> all() {
        return new ArrayList<>(records);
    }

    public int count() {
        return records.size();
    }

    /**
     * 某一天的全部记录（按时间顺序）。
     */
    public List<Record> day(LocalDate date) {
        List<Record> out = new ArrayList<>();
        for (Record r : records) {
            if (r.date.equals(date)) {
                out.add(r);
            } else if (r.date.isAfter(date)) {
                break;
            }
        }
        return out;
    }

    public Record get(LocalDate date, int slot) {
        for (int i = records.size() - 1; i >= 0; i--) {
            Record r = records.get(i);
            if (r.date.equals(date) && r.slot == slot) {
                return r;
            }
            if (r.date.isBefore(date)) {
                break;
            }
        }
        return null;
    }

    public Record latest() {
        return records.isEmpty() ? null : records.get(records.size() - 1);
    }

    /**
     * 指定日期之前、同一时段最近的一条记录。
     */
    public Record previousSameSlot(LocalDate date, int slot) {
        for (int i = records.size() - 1; i >= 0; i--) {
            Record r = records.get(i);
            if (r.date.isBefore(date) && r.slot == slot) {
                return r;
            }
        }
        return null;
    }

    public void upsert(LocalDate date, int slot, double weight) {
        final int safeSlot = Slots.clamp(slot);
        records.removeIf(r -> r.date.equals(date) && r.slot == safeSlot);
        records.add(new Record(date, safeSlot, weight));
        Collections.sort(records);
        save();
        notifyChanged();
    }

    public void delete(LocalDate date, int slot) {
        final int safeSlot = Slots.clamp(slot);
        boolean removed = records.removeIf(r -> r.date.equals(date) && r.slot == safeSlot);
        if (removed) {
            save();
            notifyChanged();
        }
    }

    public void replaceAll(List<Record> newRecords) {
        records.clear();
        records.addAll(newRecords);
        Collections.sort(records);
        save();
        notifyChanged();
    }

    public void clear() {
        if (records.isEmpty()) {
            return;
        }
        records.clear();
        save();
        notifyChanged();
    }

    public void addListener(Runnable listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void notifyChanged() {
        for (Runnable r : new ArrayList<>(listeners)) {
            r.run();
        }
    }

    public String exportJson() {
        try {
            JSONObject root = new JSONObject();
            root.put("version", 2);
            root.put("app", "weight-diary");
            JSONArray arr = new JSONArray();
            for (Record r : records) {
                JSONObject item = new JSONObject();
                item.put("date", r.date.toString());
                item.put("slot", r.slot);
                item.put("weight", r.weight);
                arr.put(item);
            }
            root.put("records", arr);
            return root.toString(2);
        } catch (Exception e) {
            return "{\"version\":2,\"records\":[]}";
        }
    }

    /**
     * 支持 {"records":[...]} 或纯数组；slot 可缺省（视为早餐前）。
     *
     * @return 导入的记录数量
     */
    public int importJson(String text) throws Exception {
        String trimmed = text == null ? "" : text.trim();
        JSONArray arr;
        if (trimmed.startsWith("[")) {
            arr = new JSONArray(trimmed);
        } else {
            JSONObject obj = new JSONObject(trimmed);
            arr = obj.getJSONArray("records");
        }
        List<Record> parsed = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject item = arr.getJSONObject(i);
            String dateText = item.optString("date", item.optString("d", ""));
            int slot = item.optInt("slot", item.optInt("s", 0));
            double weight = item.optDouble("weight", item.optDouble("w", Double.NaN));
            if (dateText.isEmpty() || Double.isNaN(weight) || weight <= 0 || weight >= 1000) {
                continue;
            }
            try {
                parsed.add(new Record(LocalDate.parse(dateText), Slots.clamp(slot), weight));
            } catch (Exception ignored) {
                // 跳过非法日期
            }
        }
        if (parsed.isEmpty()) {
            throw new IllegalStateException("没有可导入的有效记录");
        }
        Collections.sort(parsed);
        replaceAll(parsed);
        return parsed.size();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            String content = sb.toString().trim();
            if (content.isEmpty()) {
                return;
            }
            JSONArray arr = new JSONObject(content).optJSONArray("records");
            if (arr == null) {
                return;
            }
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.getJSONObject(i);
                String dateText = item.optString("date", "");
                int slot = item.optInt("slot", 0);
                double weight = item.optDouble("weight", Double.NaN);
                if (dateText.isEmpty() || Double.isNaN(weight)) {
                    continue;
                }
                try {
                    records.add(new Record(LocalDate.parse(dateText), Slots.clamp(slot), weight));
                } catch (Exception ignored) {
                    // 跳过损坏记录
                }
            }
            Collections.sort(records);
        } catch (Exception ignored) {
            // 读取失败时保留空列表，不影响启动
        }
    }

    private void save() {
        File tmp = new File(file.getParentFile(), FILE_NAME + ".tmp");
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            writer.write(exportJson());
        } catch (Exception e) {
            return;
        }
        if (!tmp.renameTo(file)) {
            // 重命名失败时退化为覆盖写入
            try (OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(file), StandardCharsets.UTF_8)) {
                writer.write(exportJson());
            } catch (Exception ignored) {
                // 保留旧文件
            }
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }
}
