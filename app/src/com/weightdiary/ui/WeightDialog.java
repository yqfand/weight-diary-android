package com.weightdiary.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.weightdiary.R;
import com.weightdiary.core.Fmt;
import com.weightdiary.core.Slots;
import com.weightdiary.data.WeightStore;
import com.weightdiary.model.Record;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 录入 / 修改体重：可在弹窗内选择“第几次”（1 为早餐前）。
 */
public final class WeightDialog {

    private static final int[] CHIP_IDS = {
            R.id.slotChip0, R.id.slotChip1, R.id.slotChip2,
            R.id.slotChip3, R.id.slotChip4, R.id.slotChip5
    };

    private WeightDialog() {
    }

    public static void show(Activity activity, LocalDate date, int initialSlot) {
        final int safeInitial = Slots.clamp(initialSlot);
        final WeightStore store = WeightStore.get(activity);
        final Dialog dialog = new Dialog(activity, R.style.Theme_WeightDiary_Dialog);

        View content = LayoutInflater.from(activity).inflate(R.layout.dialog_weight, null);
        FrameLayout wrapper = new FrameLayout(activity);
        wrapper.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 12),
                Ui.dp(activity, 12), Ui.dp(activity, 24));
        wrapper.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.setContentView(wrapper);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.dimAmount = 0.55f;
            lp.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            window.setAttributes(lp);
        }

        final TextView title = content.findViewById(R.id.dialogTitle);
        final TextView sub = content.findViewById(R.id.dialogSub);
        final TextView prev = content.findViewById(R.id.dialogPrev);
        final TextView fillYesterday = content.findViewById(R.id.btnFillYesterday);
        final TextView fillLast = content.findViewById(R.id.btnFillLast);
        final EditText et = content.findViewById(R.id.etWeight);
        final View delete = content.findViewById(R.id.btnDelete);
        final TextView[] chips = new TextView[Slots.COUNT];
        for (int i = 0; i < Slots.COUNT; i++) {
            chips[i] = content.findViewById(CHIP_IDS[i]);
        }

        class Controller {
            int slot = safeInitial;

            void select(int newSlot) {
                if (newSlot == slot) {
                    return;
                }
                slot = newSlot;
                render();
            }

            void render() {
                Record existing = store.get(date, slot);
                String action = existing != null ? "修改"
                        : (date.equals(LocalDate.now()) ? "记录" : "补记");
                title.setText(action + " · 第 " + (slot + 1) + " 次 · " + Slots.name(slot));
                sub.setText(Fmt.fullCn(date) + (existing != null ? " · 已记录" : ""));

                for (int i = 0; i < Slots.COUNT; i++) {
                    boolean active = i == slot;
                    chips[i].setBackgroundResource(active
                            ? R.drawable.bg_pill_on : R.drawable.bg_pill_off);
                    chips[i].setTextColor(Ui.color(activity,
                            active ? R.color.mint : R.color.text_secondary));
                    chips[i].setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
                }

                Record samePrev = store.previousSameSlot(date, slot);
                if (samePrev != null) {
                    prev.setVisibility(View.VISIBLE);
                    long gap = ChronoUnit.DAYS.between(samePrev.date, date);
                    prev.setText("上次第 " + (slot + 1) + " 次 " + Slots.name(slot) + " "
                            + Fmt.kg(samePrev.weight) + " kg · "
                            + (gap == 1 ? "昨天" : gap + " 天前"));
                } else {
                    prev.setVisibility(View.GONE);
                }

                Record yesterdaySame = store.get(date.minusDays(1), slot);
                if (yesterdaySame != null) {
                    fillYesterday.setVisibility(View.VISIBLE);
                    fillYesterday.setText("沿用昨日 " + Fmt.kg(yesterdaySame.weight));
                } else {
                    fillYesterday.setVisibility(View.GONE);
                }

                Record last = lastMeasurement(store, date, slot);
                if (last != null) {
                    fillLast.setVisibility(View.VISIBLE);
                    fillLast.setText("沿用上次 " + Fmt.kg(last.weight));
                } else {
                    fillLast.setVisibility(View.GONE);
                }

                delete.setVisibility(existing != null ? View.VISIBLE : View.GONE);

                double fallback = defaultValue(store, date, slot);
                if (Double.isNaN(fallback)) {
                    et.setText("");
                } else {
                    et.setText(Fmt.kg(fallback));
                    et.post(et::selectAll);
                }
            }
        }

        final Controller controller = new Controller();
        for (int i = 0; i < Slots.COUNT; i++) {
            final int index = i;
            chips[i].setOnClickListener(v -> controller.select(index));
        }

        et.setHighlightColor(Color.parseColor("#5546E0A8"));
        et.requestFocus();

        View minus = content.findViewById(R.id.btnMinus);
        View plus = content.findViewById(R.id.btnPlus);
        View cancel = content.findViewById(R.id.btnCancel);
        View save = content.findViewById(R.id.btnSave);

        minus.setOnClickListener(v -> step(et, -0.1));
        plus.setOnClickListener(v -> step(et, 0.1));

        fillYesterday.setOnClickListener(v -> {
            Record r = store.get(date.minusDays(1), controller.slot);
            if (r != null) {
                setValue(et, r.weight);
            }
        });
        fillLast.setOnClickListener(v -> {
            Record r = lastMeasurement(store, date, controller.slot);
            if (r != null) {
                setValue(et, r.weight);
            }
        });

        cancel.setOnClickListener(v -> dialog.dismiss());

        delete.setOnClickListener(v -> {
            int slot = controller.slot;
            store.delete(date, slot);
            Ui.toast(activity, "已删除 " + Fmt.cn(date) + " 第 " + (slot + 1) + " 次 "
                    + Slots.name(slot));
            dialog.dismiss();
        });

        save.setOnClickListener(v -> {
            double value = parse(et.getText().toString());
            if (Double.isNaN(value) || value < 20.0 || value > 300.0) {
                Ui.toast(activity, "请输入 20.0 – 300.0 kg 之间的体重");
                return;
            }
            int slot = controller.slot;
            store.upsert(date, slot, value);
            Ui.toast(activity, "已保存第 " + (slot + 1) + " 次 " + Slots.name(slot)
                    + " " + Fmt.kg(value) + " kg");
            dialog.dismiss();
        });

        controller.render();

        dialog.show();
        if (dialog.getWindow() != null) {
            int width = activity.getResources().getDisplayMetrics().widthPixels - Ui.dp(activity, 24);
            dialog.getWindow().setLayout(Math.max(width, Ui.dp(activity, 240)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    /**
     * 当天本时段之前最近的一次测量；若没有，则取当天任意一次；再退到此前最近的一条。
     */
    private static Record lastMeasurement(WeightStore store, LocalDate date, int slot) {
        Record earlier = null;
        List<Record> day = store.day(date);
        for (Record r : day) {
            if (r.slot < slot) {
                earlier = r;
            }
        }
        if (earlier != null) {
            return earlier;
        }
        Record any = null;
        for (Record r : day) {
            if (r.slot != slot) {
                any = r;
            }
        }
        if (any != null) {
            return any;
        }
        Record latest = store.latest();
        if (latest != null && !latest.date.isAfter(date)) {
            return latest;
        }
        return null;
    }

    private static double defaultValue(WeightStore store, LocalDate date, int slot) {
        Record existing = store.get(date, slot);
        if (existing != null) {
            return existing.weight;
        }
        Record earlier = suggestEarlierSlot(store, date, slot);
        if (earlier != null) {
            return earlier.weight;
        }
        Record yesterdaySame = store.get(date.minusDays(1), slot);
        if (yesterdaySame != null) {
            return yesterdaySame.weight;
        }
        Record last = lastMeasurement(store, date, slot);
        return last != null ? last.weight : Double.NaN;
    }

    private static Record suggestEarlierSlot(WeightStore store, LocalDate date, int slot) {
        Record earlier = null;
        for (Record r : store.day(date)) {
            if (r.slot < slot) {
                earlier = r;
            }
        }
        return earlier;
    }

    private static void setValue(EditText et, double value) {
        if (Double.isNaN(value)) {
            return;
        }
        et.setText(Fmt.kg(value));
        et.setSelection(et.getText().length());
    }

    private static void step(EditText et, double delta) {
        double current = parse(et.getText().toString());
        if (Double.isNaN(current) || current <= 0) {
            current = 60.0;
        }
        double value = Math.max(20.0, Math.min(300.0, current + delta));
        et.setText(Fmt.kg(value));
        et.setSelection(et.getText().length());
    }

    private static double parse(String raw) {
        if (raw == null) {
            return Double.NaN;
        }
        String text = raw.trim().replace(',', '.');
        if (text.isEmpty() || text.equals(".")) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
