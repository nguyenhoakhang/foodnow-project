package com.example.a524h0098_nguyenhoakhang_foodnow_project;

import android.os.Bundle;
import android.widget.FrameLayout;
import androidx.appcompat.app.AppCompatActivity;

public class ProfileActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout content = new FrameLayout(this);
        content.setId(android.R.id.primary);
        setContentView(content);
        StudentFooter.attach(this);
        if (state == null)
            getSupportFragmentManager().beginTransaction()
                    .replace(android.R.id.primary, new ProfileFragment()).commit();
    }
}
