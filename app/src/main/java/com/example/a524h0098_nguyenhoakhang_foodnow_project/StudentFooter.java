package com.example.a524h0098_nguyenhoakhang_foodnow_project;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

final class StudentFooter {
    static void attach(Activity activity) {
        FrameLayout content = activity.findViewById(android.R.id.content);
        View screen = content.getChildAt(0);
        content.removeView(screen);
        LinearLayout wrapper = new LinearLayout(activity);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(screen, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        TextView footer = new TextView(activity);
        footer.setText(R.string.student_identity);
        footer.setGravity(android.view.Gravity.CENTER);
        footer.setTextSize(11);
        footer.setTextColor(activity.getColor(R.color.ui_muted));
        wrapper.setBackgroundColor(activity.getColor(R.color.ui_background));
        int padding = (int) (8 * activity.getResources().getDisplayMetrics().density);
        footer.setPadding(padding, padding, padding, padding);
        wrapper.addView(footer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(wrapper);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(wrapper, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars()
                            | androidx.core.view.WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return androidx.core.view.WindowInsetsCompat.CONSUMED;
        });
        androidx.core.view.ViewCompat.requestApplyInsets(wrapper);
    }
}
