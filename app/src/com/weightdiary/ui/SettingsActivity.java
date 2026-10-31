package com.weightdiary.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.weightdiary.R;
import com.weightdiary.core.Fmt;
import com.weightdiary.data.Prefs;
import com.weightdiary.data.WeightStore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;

public class SettingsActivity extends Activity {

    private static final int REQ_EXPORT = 1001;
    private static final int REQ_IMPORT = 1002;

    private EditText etHeight;
    private EditText etTarget;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Ui.edgeToEdge(this, findViewById(R.id.root));

        etHeight = findViewById(R.id.etHeight);
        etTarget = findViewById(R.id.etTarget);

        Prefs prefs = Prefs.get(this);
        double height = prefs.heightCm();
        if (height > 0) {
            etHeight.setText(String.format(Locale.US, "%.0f", height));
        }
        double target = prefs.targetKg();
        if (target > 0) {
            etTarget.setText(Fmt.kg(target));
        }

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSave).setOnClickListener(v -> save());
        findViewById(R.id.btnExport).setOnClickListener(v -> exportBackup());
        findViewById(R.id.btnImport).setOnClickListener(v -> importBackup());
        findViewById(R.id.btnClear).setOnClickListener(v -> confirmClear());
    }

    private void save() {
        String heightText = etHeight.getText().toString().trim().replace(',', '.');
        String targetText = etTarget.getText().toString().trim().replace(',', '.');

        double height = 0;
        if (!heightText.isEmpty()) {
            try {
                height = Double.parseDouble(heightText);
            } catch (NumberFormatException e) {
                Ui.toast(this, "身高格式不正确");
                return;
            }
            if (height < 100 || height > 250) {
                Ui.toast(this, "请输入 100 – 250 cm 之间的身高");
                return;
            }
        }

        double target = 0;
        if (!targetText.isEmpty()) {
            try {
                target = Double.parseDouble(targetText);
            } catch (NumberFormatException e) {
                Ui.toast(this, "目标体重格式不正确");
                return;
            }
            if (target < 20 || target > 300) {
                Ui.toast(this, "目标体重请输入 20 – 300 kg");
                return;
            }
        }

        Prefs prefs = Prefs.get(this);
        prefs.setHeightCm(height);
        prefs.setTargetKg(target);
        Ui.toast(this, "已保存");
        finish();
    }

    private void exportBackup() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "体重日记备份-" + LocalDate.now() + ".json");
        startActivityForResult(intent, REQ_EXPORT);
    }

    private void importBackup() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQ_IMPORT);
    }

    private void confirmClear() {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("清空全部记录")
                .setMessage("将删除本机保存的全部体重记录，且无法撤销。建议先导出备份。")
                .setPositiveButton("清空", (dialog, which) -> {
                    WeightStore.get(this).clear();
                    Ui.toast(this, "已清空全部记录");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQ_EXPORT) {
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) {
                    throw new IOException("无法写入所选文件");
                }
                out.write(WeightStore.get(this).exportJson().getBytes(StandardCharsets.UTF_8));
                Ui.toast(this, "已导出 " + WeightStore.get(this).count() + " 条记录");
            } catch (Exception e) {
                Ui.toast(this, "导出失败：" + e.getMessage());
            }
        } else if (requestCode == REQ_IMPORT) {
            try {
                String text = readText(uri);
                int count = WeightStore.get(this).importJson(text);
                Ui.toast(this, "已导入 " + count + " 条记录");
            } catch (Exception e) {
                Ui.toast(this, "导入失败：" + (e.getMessage() == null ? "文件格式不正确" : e.getMessage()));
            }
        }
    }

    private String readText(Uri uri) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IOException("无法读取所选文件");
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }
}
