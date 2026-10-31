package com.weightdiary.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.weightdiary.R;
import com.weightdiary.core.Fmt;
import com.weightdiary.data.WeightStore;

import java.time.LocalDate;

public class MainActivity extends Activity {

    private static final int TAB_TODAY = 0;
    private static final int TAB_TREND = 1;
    private static final int TAB_ANALYSIS = 2;
    private static final int TAB_HISTORY = 3;

    private final Page[] pages = new Page[4];
    private final LinearLayout[] navs = new LinearLayout[4];
    private final ImageView[] icons = new ImageView[4];
    private final TextView[] labels = new TextView[4];
    private final Runnable storeListener = this::refreshAll;

    private int current = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Ui.edgeToEdge(this, findViewById(R.id.root));

        FrameLayout container = findViewById(R.id.pageContainer);
        pages[TAB_TODAY] = new TodayPage(this, container);
        pages[TAB_TREND] = new TrendPage(this, container);
        pages[TAB_ANALYSIS] = new AnalysisPage(this, container);
        pages[TAB_HISTORY] = new HistoryPage(this, container);

        navs[TAB_TODAY] = findViewById(R.id.navToday);
        navs[TAB_TREND] = findViewById(R.id.navTrend);
        navs[TAB_ANALYSIS] = findViewById(R.id.navAnalysis);
        navs[TAB_HISTORY] = findViewById(R.id.navHistory);
        icons[TAB_TODAY] = findViewById(R.id.navTodayIcon);
        icons[TAB_TREND] = findViewById(R.id.navTrendIcon);
        icons[TAB_ANALYSIS] = findViewById(R.id.navAnalysisIcon);
        icons[TAB_HISTORY] = findViewById(R.id.navHistoryIcon);
        labels[TAB_TODAY] = findViewById(R.id.navTodayLabel);
        labels[TAB_TREND] = findViewById(R.id.navTrendLabel);
        labels[TAB_ANALYSIS] = findViewById(R.id.navAnalysisLabel);
        labels[TAB_HISTORY] = findViewById(R.id.navHistoryLabel);

        for (int i = 0; i < navs.length; i++) {
            final int tab = i;
            navs[i].setOnClickListener(v -> select(tab, true));
        }

        findViewById(R.id.btnSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        updateHeaderDate();
        select(TAB_TODAY, false);
        WeightStore.get(this).addListener(storeListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateHeaderDate();
        refreshAll();
    }

    @Override
    protected void onDestroy() {
        WeightStore.get(this).removeListener(storeListener);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (current != TAB_TODAY) {
            select(TAB_TODAY, true);
        } else {
            super.onBackPressed();
        }
    }

    private void select(int tab, boolean animate) {
        if (tab == current) {
            return;
        }
        if (current >= 0) {
            pages[current].root.setVisibility(View.GONE);
        }
        View target = pages[tab].root;
        target.setVisibility(View.VISIBLE);
        if (animate) {
            target.setAlpha(0f);
            target.setTranslationY(Ui.dp(this, 12));
            target.animate().alpha(1f).translationY(0f).setDuration(240).start();
        } else {
            target.setAlpha(1f);
            target.setTranslationY(0f);
        }
        current = tab;
        pages[tab].refresh();
        updateNav();
    }

    private void refreshAll() {
        for (Page page : pages) {
            if (page != null) {
                page.refresh();
            }
        }
        updateHeaderDate();
    }

    private void updateHeaderDate() {
        TextView tv = findViewById(R.id.tvHeaderDate);
        tv.setText(Fmt.fullCn(LocalDate.now()));
    }

    private void updateNav() {
        int mint = Ui.color(this, R.color.mint);
        int tertiary = Ui.color(this, R.color.text_tertiary);
        for (int i = 0; i < navs.length; i++) {
            boolean active = i == current;
            navs[i].setBackgroundResource(active ? R.drawable.bg_nav_on : 0);
            icons[i].setColorFilter(active ? mint : tertiary);
            labels[i].setTextColor(active ? mint : tertiary);
        }
    }
}
