package com.example.a524h0098_nguyenhoakhang_foodnow_project;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class MainActivity extends AppCompatActivity {

    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private ViewPagerAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        StudentFooter.attach(this);
        NotificationHelper.register(this);

        tabLayout = findViewById(R.id.tabLayout);
        viewPager = findViewById(R.id.viewPager);

        adapter = new ViewPagerAdapter(this);
        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText(R.string.ui_explore);
                    tab.setIcon(R.drawable.ic_ui_home);
                    break;
                case 1:
                    tab.setText(R.string.ui_orders);
                    tab.setIcon(R.drawable.ic_ui_orders);
                    break;
                case 2:
                    tab.setText(R.string.ui_account);
                    tab.setIcon(R.drawable.ic_ui_person);
                    break;
            }
        }).attach();
    }
}
