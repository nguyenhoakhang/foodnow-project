package com.example.a524h0098_nguyenhoakhang_foodnow_project;

import android.content.Context;
import android.content.res.Configuration;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class UiLayoutTest {
    @Test
    public void formsFitSmallScreensInBothThemes() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
            for (int night : new int[] { Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES }) {
                Configuration config = new Configuration(app.getResources().getConfiguration());
                config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
                config.fontScale = 1.3f;
                Context context = new ContextThemeWrapper(app.createConfigurationContext(config),
                        R.style.Theme_Project_FoodApp);
                int width = (int) (320 * context.getResources().getDisplayMetrics().density);
                int height = (int) (640 * context.getResources().getDisplayMetrics().density);
                for (int layout : new int[] { R.layout.activity_signin, R.layout.activity_register,
                        R.layout.activity_checkout, R.layout.activity_orders, R.layout.profile_editor,
                        R.layout.activity_restaurant, R.layout.sheet_cart, R.layout.fragment_restaurant }) {
                    View screen = LayoutInflater.from(context).inflate(layout, null, false);
                    screen.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                    screen.layout(0, 0, width, height);
                    checkFields(screen, context.getResources().getDisplayMetrics().density);
                }
                int background = context.getColor(R.color.ui_background);
                int text = context.getColor(R.color.ui_text);
                assertNotEquals("Text must differ from its background", background, text);
            }
        });
    }

    private void checkFields(View view, float density) {
        if (view.getVisibility() == View.GONE)
            return;
        if (view instanceof EditText) {
            assertTrue("Input has a usable touch target", view.getMeasuredHeight() >= 48 * density);
            assertTrue("Input leaves space for text", view.getMeasuredWidth() >= 160 * density);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                checkFields(group.getChildAt(i), density);
        }
    }
}
