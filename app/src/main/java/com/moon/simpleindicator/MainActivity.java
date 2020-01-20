package com.moon.simpleindicator;

import java.util.ArrayList;
import java.util.List;

import android.graphics.Color;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnScrollChangeListener;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import com.moon.simpleindicator.view.SimpleIndicator;
import com.moon.simpleindicator.view.SimpleIndicator.IndicatorBean;

public class MainActivity extends AppCompatActivity {

    private SimpleIndicator indicator;
    private ViewPager viewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViewPager();
        initIndicator();
    }

    private void initIndicator() {

        indicator = findViewById(R.id.indicator);

        indicator.bindViewPager(viewPager);

        List<IndicatorBean> data = new ArrayList<>();

        data.add(new IndicatorBean("北京", "全聚德烤鸭"));
        data.add(new IndicatorBean("天津", "狗不理"));
        data.add(new IndicatorBean("上海", "侬好"));
        data.add(new IndicatorBean("新疆乌鲁木齐", "羊肉串"));
        data.add(new IndicatorBean("广州", ""));
        data.add(new IndicatorBean("西安", "肉夹馍"));
        data.add(new IndicatorBean("东京", "热"));

        indicator.setIndicatorData(data);
    }

    private void initViewPager() {

        viewPager = findViewById(R.id.viewPager);
        viewPager.setAdapter(new MyAdapter());
    }

    private class MyAdapter extends PagerAdapter {

        @Override
        public int getCount() {
            return 7;
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position,
                                @NonNull Object object) {
            container.removeView((View)object);
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {

            View child;

            if (position == 0) {

                ScrollView scrollView = new ScrollView(container.getContext());
                child = scrollView;

                if (VERSION.SDK_INT >= VERSION_CODES.M) {
                    scrollView.setOnScrollChangeListener(new OnScrollChangeListener() {
                        @Override
                        public void onScrollChange(View v, int scrollX, int scrollY,
                                                   int oldScrollX,
                                                   int oldScrollY) {
                            indicator.setNowHeight(200 - scrollY);
                        }
                    });

                    LinearLayout linearLayout = new LinearLayout(container.getContext());
                    linearLayout.setOrientation(LinearLayout.VERTICAL);
                    for (int index = 0; index < 50; index++) {

                        TextView textView = new TextView(container.getContext());
                        textView.setText("" + index);
                        textView.setBackgroundColor(index % 2 == 0 ? Color.GRAY : Color.WHITE);
                        textView.setGravity(Gravity.CENTER);
                        textView.setMinHeight(120);
                        linearLayout.addView(textView);
                    }

                    scrollView.addView(linearLayout);
                }

            } else {
                TextView textView = new TextView(container.getContext());
                textView.setText("" + position);
                textView.setBackgroundColor(position % 2 == 0 ? Color.GRAY : Color.WHITE);
                textView.setGravity(Gravity.CENTER);

                child = textView;
            }

            container.addView(child);

            return child;
        }
    }
}
