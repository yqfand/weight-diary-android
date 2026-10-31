package com.weightdiary.ui;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public abstract class Page {

    protected final Activity activity;
    public final View root;

    Page(Activity activity, ViewGroup parent, int layoutRes) {
        this.activity = activity;
        this.root = LayoutInflater.from(activity).inflate(layoutRes, parent, false);
        this.root.setVisibility(View.GONE);
        parent.addView(root);
    }

    public abstract void refresh();
}
