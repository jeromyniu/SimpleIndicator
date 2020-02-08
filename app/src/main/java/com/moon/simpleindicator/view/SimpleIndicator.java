package com.moon.simpleindicator.view;

import java.util.HashMap;
import java.util.List;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;
import androidx.viewpager.widget.ViewPager.OnPageChangeListener;

public class SimpleIndicator extends View {

    private final String tag = SimpleIndicator.class.getSimpleName();

    private static int min_margin_between_item;

    //具备实现折叠功能的各种条件
    private boolean isLegalState = false;

    private int topTextSize = spToPx(getContext(), 16);
    private int topTextColor = 0xff333333;

    private int bottomTextSize = spToPx(getContext(), 11);
    private int bottomTextColor = 0xff999999;
    private int bottomTextSelectedBgColor = Color.RED;
    private int bottomTextTextColorSelected = Color.WHITE;

    //上下两行内容的间距
    private int marginV = 15;
    //选中状态下文字与边框的内边距
    private int innerPaddingInSelectedItemH = 6;
    private int innerPaddingInSelectedItemV = 3;
    //选中状态下，圆角矩形的位置
    private RectF selectedRect = new RectF();

    //选中状态，底部文字矩形背景圆角半径
    private int radiusOfRect = 5;

    //各个区域的画笔
    private Paint topTextPaint;
    private Paint bottomTextPaint;
    private Paint selectRectPaint;

    //数据源
    private List<IndicatorBean> indicatorBeans;

    //完全展开状态下的高度
    private int fullHeight;
    //折叠状态的高度
    private int foldedHeight;
    //折叠状态下底部选中状态标记高度
    private int heightOfSelectedLine;

    private HashMap<Integer, Integer> fontSizeToFontDescent = new HashMap<>();

    private VelocityTracker mVelocityTracker;

    /***** 外部ViewPager相关 ****/
    private MyPageScrollListener myPageScrollListener = new MyPageScrollListener();
    private ViewPager targetViewPager;

    public SimpleIndicator(@NonNull Context context) {
        super(context);
        init(null);
    }

    public SimpleIndicator(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public SimpleIndicator(@NonNull Context context, @Nullable AttributeSet attrs,
                           int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(AttributeSet attrs) {

        min_margin_between_item = dpToPx(getContext(), 25);
        heightOfSelectedLine = dpToPx(getContext(), 3);

        setBackgroundColor(Color.WHITE);

        final ViewConfiguration configuration = ViewConfiguration.get(getContext());
        moveSlop = configuration.getScaledTouchSlop();

        topTextPaint = new Paint();
        topTextPaint.setColor(topTextColor);
        topTextPaint.setAntiAlias(true);
        topTextPaint.setTextSize(topTextSize);
        topTextPaint.setTypeface(Typeface.DEFAULT_BOLD);

        bottomTextPaint = new Paint();
        bottomTextPaint.setColor(bottomTextColor);
        bottomTextPaint.setAntiAlias(true);
        bottomTextPaint.setTextSize(bottomTextSize);

        selectRectPaint = new Paint();
        selectRectPaint.setColor(bottomTextSelectedBgColor);
        selectRectPaint.setAntiAlias(true);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        //如果高度需要由控件自己测量
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST
            || MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED
            || MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED
            || MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.AT_MOST) {

            if (fullHeight <= 0) {
                measureSelfHeight();
            }
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), fullHeight);
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
        //Log.e(tag, "onMeasure nowHeight=" + getMeasuredHeight());
    }

    /**
     * 自测尺寸，有可能不在onMeasure方法里调用
     */
    private int measureSelfHeight() {

        int heightOfTopText = measureTopText("测试").second;
        int heightOfBottomText = measureBottomText("测试").second;

        int height = getPaddingTop() + getPaddingBottom()
            + marginV
            + heightOfTopText
            + heightOfBottomText
            //为了保证底部文字是否处于选中状态，时刻都是底部对齐
            + innerPaddingInSelectedItemV;

        //测量得出的高度才准确
        fullHeight = height;
        foldedHeight = getPaddingTop() + getPaddingBottom()
            + heightOfTopText + marginV + heightOfSelectedLine;

        return height;
    }

    private float downX, downY;
    private float lastX;

    private int moveSlop;
    //标记这个事件序列是否为点击
    private boolean isClickEvent = false;
    //标记是否为水平滑动
    private boolean isHorizontalMove = false;
    //水平方向可滚动的最大距离
    private int maxScrollX;

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {

        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:

                downX = ev.getX();
                downY = ev.getY();

                break;

            case MotionEvent.ACTION_MOVE:

                float dx = Math.abs(ev.getX() - downX);
                float dy = Math.abs(ev.getY() - downY);

                if (dx < moveSlop || dx < dy) {
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(false);
                    }
                }

                break;

            case MotionEvent.ACTION_CANCEL:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                lastX = downX;
                isClickEvent = true;
                isHorizontalMove = false;
                if (valueAnimator != null && valueAnimator.isRunning()) {
                    valueAnimator.cancel();
                }
                return true;

            case MotionEvent.ACTION_MOVE:

                float posX = event.getX();
                float posY = event.getY();

                boolean isToLeft = lastX > posX;
                int distanceX = (int)Math.abs(posX - downX);
                int distanceY = (int)Math.abs(posY - downY);
                //区分是点击事件还是滑动事件
                if (distanceX > moveSlop || distanceY > moveSlop) {
                    isClickEvent = false;

                    if (distanceX > moveSlop && distanceX >= 2 * distanceY) {
                        isHorizontalMove = true;
                    }
                }

                //执行水平方向滑动逻辑
                if (isHorizontalMove) {
                    //向右滑
                    if (!isToLeft) {
                        if (getScrollX() == 0) {
                            return true;
                        }
                        scrollTo(Math.max((int)(getScrollX() + lastX - posX), 0), 0);
                    }
                    //向左滑
                    if (isToLeft) {
                        if (getScrollX() == maxScrollX) {
                            return true;
                        }
                        scrollTo(Math.min((int)(getScrollX() + lastX - posX), maxScrollX), 0);
                    }
                }
                lastX = posX;
                break;

            case MotionEvent.ACTION_UP:

                if (isClickEvent
                    && Math.abs(event.getX() - downX) < moveSlop
                    && Math.abs(event.getY() - downY) < moveSlop) {
                    executeClick(event.getX(), event.getY());
                }

                mVelocityTracker.computeCurrentVelocity(1000);
                int velocityX = (int)mVelocityTracker.getXVelocity();
                selfScroll(velocityX);
                break;

            case MotionEvent.ACTION_CANCEL:
                break;
        }
        return true;
    }

    /**
     * 点击后重绘UI
     */
    private void executeClick(float x, float y) {
        if (indicatorBeans == null) {return;}
        int scrollX = getScrollX();
        int size = indicatorBeans.size();
        int selectedIndex = -1;
        for (int index = 0; index < size; index++) {
            IndicatorBean bean = indicatorBeans.get(index);
            if (bean == null) {continue;}
            if (x >= bean.left - scrollX && x <= bean.right - scrollX) {
                //已经是选中状态
                if (bean.isSelected) {return;}
                selectedIndex = index;
            }
        }

        if (targetViewPager != null && selectedIndex >= 0) {
            targetViewPager.setCurrentItem(selectedIndex, true);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        //Log.e(tag, "onDraw =========");

        if (indicatorBeans == null || indicatorBeans.size() == 0) {
            return;
        }

        int scrollX = getScrollX();

        int totalHeightGap = fullHeight - foldedHeight;
        int tempHeightGap = getMeasuredHeight() - foldedHeight;
        float ratio = 1.0f * tempHeightGap / totalHeightGap;
        int alphaOfBottomTextPaint = (int)(255 * ratio);
        //Log.e(tag, "ratio=" + ratio
        //    + " alphaOfBottomTextPaint=" + alphaOfBottomTextPaint);

        for (IndicatorBean bean : indicatorBeans) {

            if (bean == null) {
                continue;
            }

            //超出左侧屏幕
            if (bean.topTextLeft + bean.topTextWidth - scrollX < 0
                && bean.bottomTextLeft + bean.bottomTextWidth - scrollX < 0) {
                //Log.e(tag, "onDraw left no draw");
                continue;
            }

            //超出右侧屏幕，不再绘制
            int maxRight = getMeasuredWidth() - getPaddingRight();
            if (bean.topTextLeft - scrollX >= maxRight
                && bean.bottomTextLeft - scrollX >= maxRight) {
                //Log.e(tag, "onDraw right no draw topLeft=" + bean.topTextLeft
                //    + " bottomLeft=" + bean.bottomTextLeft);
                continue;
            }
            //顶部文字
            canvas.drawText(bean.topText, bean.topTextLeft, bean.topTextBaseLine,
                topTextPaint);
            //底部文字
            //选中状态
            if (bean.isSelected) {

                int textDescent = fontSizeToFontDescent.get(bottomTextSize) == null
                    ? 0 : fontSizeToFontDescent.get(bottomTextSize);

                if (alphaOfBottomTextPaint > 225 && !TextUtils.isEmpty(bean.bottomText)) {

                    selectedRect.left = bean.bottomTextLeft - innerPaddingInSelectedItemH;
                    selectedRect.top = bean.bottomTextBaseLine + 1.0f * textDescent / 2
                        - bean.bottomTextHeight - innerPaddingInSelectedItemV;
                    selectedRect.right = bean.bottomTextLeft + bean.bottomTextWidth
                        + innerPaddingInSelectedItemH;
                    selectedRect.bottom = bean.bottomTextBaseLine + innerPaddingInSelectedItemV
                        + 1.0f * textDescent / 2;
                    selectRectPaint.setColor(bottomTextSelectedBgColor);
                    canvas.drawRoundRect(selectedRect, radiusOfRect, radiusOfRect, selectRectPaint);

                    bottomTextPaint.setColor(bottomTextTextColorSelected);
                    bottomTextPaint.setAlpha(alphaOfBottomTextPaint - 20);
                    canvas.drawText(bean.bottomText, bean.bottomTextLeft,
                        bean.bottomTextBaseLine,
                        bottomTextPaint);
                } else {
                    //标题描述为空，底部选中状态为一条红线
                    if (TextUtils.isEmpty(bean.bottomText)) {
                        ratio = 0;
                    }
                    //表示选中的线宽度与头部文字保持一致
                    int gap = (int)((-bean.topTextLeft + bean.bottomTextLeft) * (1 - ratio));
                    selectedRect.left = bean.bottomTextLeft
                        - gap;
                    selectedRect.top = getMeasuredHeight() - getPaddingBottom()
                        - heightOfSelectedLine;
                    gap = (int)((bean.topTextLeft + bean.topTextWidth
                        - bean.bottomTextLeft - bean.bottomTextWidth) * (1 - ratio));
                    selectedRect.right = bean.bottomTextLeft + bean.bottomTextWidth
                        + gap;
                    selectedRect.bottom = getMeasuredHeight() - getPaddingBottom();
                    selectRectPaint.setColor(bottomTextSelectedBgColor);
                    canvas.drawRoundRect(selectedRect, radiusOfRect, radiusOfRect, selectRectPaint);
                }
            } else {
                //先设置颜色，再设置透明度。否则透明度会被覆盖
                bottomTextPaint.setColor(bottomTextColor);
                bottomTextPaint.setAlpha(alphaOfBottomTextPaint);
                canvas.drawText(bean.bottomText, bean.bottomTextLeft,
                    bean.bottomTextBaseLine,
                    bottomTextPaint);
            }
        }

        if (needExecuteAutoScroll) {
            autoScroll();
        }

        drawBottomDividerLine(canvas);
    }

    /**
     * 完全收起状态时绘制底部分割线
     */
    private void drawBottomDividerLine(Canvas canvas) {
        if (getMeasuredHeight() <= foldedHeight) {
            selectRectPaint.setColor(0xffe8e8e8);
            canvas.drawLine(0, getMeasuredHeight(),
                maxScrollX + getMeasuredWidth(),
                getMeasuredHeight(), selectRectPaint);
        }
    }

    /**
     * 数据源确定后和draw之前
     * 需要做的准备工作
     */
    private void prepare() {

        if (indicatorBeans == null || indicatorBeans.size() == 0) {
            isLegalState = false;
            return;
        }

        isLegalState = true;

        int topPadding = getPaddingTop();
        int leftPadding = getPaddingLeft();
        int rightPadding = getPaddingRight();

        int textTotalWidth = 0;
        //记录所有文字的最大高度
        int topTextMaxHeight = 0;
        int bottomTextMaxHeight = 0;

        //1. 确定文本尺寸
        for (IndicatorBean bean : indicatorBeans) {

            Pair<Integer, Integer> topTextSize = measureTopText(bean.topText);
            Pair<Integer, Integer> bottomTextSize = measureBottomText(bean.bottomText);

            bean.topTextWidth = topTextSize.first;
            bean.bottomTextWidth = bottomTextSize.first;

            bean.topTextHeight = topTextSize.second;
            bean.bottomTextHeight = bottomTextSize.second;

            topTextMaxHeight = Math.max(topTextMaxHeight, bean.topTextHeight);
            bottomTextMaxHeight = Math.max(bottomTextMaxHeight, bean.bottomTextHeight);

            bean.maxWidth = Math.max(topTextSize.first, bottomTextSize.first);

            textTotalWidth += bean.maxWidth;
        }

        int width = getMeasuredWidth() == 0 ? getScreenWidth(getContext())
            : getMeasuredWidth();

        int itemCount = indicatorBeans.size();
        //文本宽度+文本之间的margin之和
        int totalWidthWithMargin = textTotalWidth + (itemCount + 1) * min_margin_between_item;
        //一屏宽度是否能够摆放全部item
        boolean isOneSinglePage;
        int marginBetweenItem;
        //一屏宽度足以摆放所有的item
        if (totalWidthWithMargin <= width - leftPadding - rightPadding) {
            //每个item之间的间距，均分
            marginBetweenItem = (width - textTotalWidth - leftPadding - rightPadding)
                / (itemCount + 1);
            isOneSinglePage = true;
            maxScrollX = 0;
        } else {
            marginBetweenItem = min_margin_between_item;
            isOneSinglePage = false;
        }
        //每一个item可绘制部分最左侧的坐标
        int itemDrawLeft = marginBetweenItem + leftPadding;

        //2. 确定文本定位
        for (IndicatorBean bean : indicatorBeans) {
            //把高度改为所有文字中的最大高度，防止因文字高度不同出现基线高低不一的情况
            bean.topTextHeight = topTextMaxHeight;
            bean.bottomTextHeight = bottomTextMaxHeight;

            //此item在水平位置上的中点
            int centerH = itemDrawLeft + bean.maxWidth / 2;
            bean.topTextLeft = centerH - bean.topTextWidth / 2;
            bean.bottomTextLeft = centerH - bean.bottomTextWidth / 2;

            int textDescent = fontSizeToFontDescent.get(topTextSize) == null ? 0
                : fontSizeToFontDescent.get(topTextSize);
            bean.topTextBaseLine = topPadding + bean.topTextHeight
                - textDescent / 2;
            textDescent = fontSizeToFontDescent.get(bottomTextSize) == null ? 0
                : fontSizeToFontDescent.get(bottomTextSize);
            bean.bottomTextBaseLine = topPadding + bean.topTextHeight
                + marginV
                + bean.bottomTextHeight
                - textDescent / 2;

            //确定每个item可点击范围
            //确定左侧起始
            //第一个item左侧可点击范围应从0开始
            if (indicatorBeans.indexOf(bean) == 0) {
                bean.left = 0;
            }
            //非首个item，左侧可点击范围分界线是与上一个item的中线位置
            else {
                bean.left = itemDrawLeft - marginBetweenItem / 2;
            }

            //下一个item开始的水平位置
            itemDrawLeft += bean.maxWidth + marginBetweenItem;

            //确定右侧结束位置
            //最后一个item
            boolean isLastItem = indicatorBeans.indexOf(bean) == indicatorBeans.size() - 1;
            if (isLastItem) {
                //确定最后一个item右侧坐标
                bean.right = isOneSinglePage ? width : itemDrawLeft;
            } else {
                bean.right = itemDrawLeft - marginBetweenItem / 2;
            }

            if (!isOneSinglePage) {
                maxScrollX = Math.max(maxScrollX, itemDrawLeft - width);
            }
        }

    }

    private Pair<Integer, Integer> measureTopText(String text) {
        return measureText(text, topTextSize);
    }

    private Pair<Integer, Integer> measureBottomText(String text) {
        return measureText(text, bottomTextSize);
    }

    /**
     * 测量文本的宽高
     */
    private Pair<Integer, Integer> measureText(String str, int textSizeInPx) {

        if (TextUtils.isEmpty(str)) {return new Pair<>(0, 0);}

        TextPaint textPaint = new TextPaint();
        textPaint.setTextSize(textSizeInPx);
        textPaint.setAntiAlias(true);

        Rect bounds = new Rect();
        textPaint.getTextBounds(str, 0, str.length(), bounds);

        FontMetricsInt fontMetricsInt = textPaint.getFontMetricsInt();
        fontSizeToFontDescent.put(textSizeInPx, fontMetricsInt.descent);

        return new Pair<>(bounds.width(), bounds.height());
    }

    /**
     * 得到设备屏幕的宽度
     */
    private int getScreenWidth(Context context) {
        return context.getResources().getDisplayMetrics().widthPixels;
    }

    /**
     * sp转化为像素.
     */
    private int spToPx(Context context, float sp) {
        final float scale = context.getResources().getDisplayMetrics().scaledDensity;
        return (int)(sp * scale + 0.5f);
    }

    /**
     * 把密度转换为像素
     */
    private int dpToPx(Context context, float dp) {
        final float scale = getScreenDensity(context);
        return (int)(dp * scale + 0.5f);
    }

    /**
     * 得到设备的密度
     */
    private float getScreenDensity(Context context) {
        return context.getResources().getDisplayMetrics().density;
    }

    private void changeSelfHeight(int height) {

        if (getMeasuredHeight() == height) {
            return;
        }

        ViewGroup.LayoutParams params = getLayoutParams();
        params.height = height;
        requestLayout();
    }

    //绘制完成后是否需要执行自动滑动
    boolean needExecuteAutoScroll = false;

    private ValueAnimator autoScrollAnim;

    /**
     * 如果被选中的item未完全显示
     * 滚动一定距离把选中的item显示出来
     */
    private void autoScroll() {
        needExecuteAutoScroll = false;
        IndicatorBean selectedBean = null;
        for (IndicatorBean bean : indicatorBeans) {
            if (bean.isSelected) {
                selectedBean = bean;
            }
        }

        if (selectedBean == null) {
            return;
        }

        int scrollDistanceX = 0;
        int scrollX = getScrollX();
        //文字左侧超出左侧屏幕
        int minLeft = selectedBean.left;
        if (minLeft - scrollX < 0) {
            scrollDistanceX = minLeft - scrollX;
        }
        //文字右侧超出右侧屏幕
        int maxRight = selectedBean.right;
        if (maxRight - scrollX > getMeasuredWidth()) {
            scrollDistanceX = maxRight - scrollX - getMeasuredWidth();
        }

        if (scrollDistanceX != 0) {
            if (autoScrollAnim != null && autoScrollAnim.isRunning()) {
                autoScrollAnim.cancel();
            }

            final int finalScrollX = getScrollX() + scrollDistanceX;
            autoScrollAnim = ValueAnimator.ofInt(getScrollX(), finalScrollX)
                    .setDuration(80);
            autoScrollAnim.setInterpolator(new AccelerateDecelerateInterpolator());
            autoScrollAnim.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    int targetScrollX = (int)animation.getAnimatedValue();
                    SimpleIndicator.this.scrollTo(targetScrollX, 0);
                }
            });

            autoScrollAnim.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    //保证动画执行过程中被cancel时最终位置的的正确性
                    SimpleIndicator.this.scrollTo(finalScrollX, 0);
                }

                @Override
                public void onAnimationRepeat(Animator animation) {
                }
            });

            autoScrollAnim.start();
        }
    }

    private ValueAnimator valueAnimator;

    /**
     * 惯性滑动
     */
    private void selfScroll(int xVelocity) {
        if (Math.abs(xVelocity) < 50) {
            return;
        }

        if (valueAnimator != null && valueAnimator.isRunning()) {
            return;
        }

        xVelocity = xVelocity / 10;

        int targetScrollX;
        if (xVelocity < 0) {
            targetScrollX = Math.min(maxScrollX, getScrollX() - xVelocity);
        } else {
            targetScrollX = Math.min(0, getScrollX() + xVelocity);
        }

        valueAnimator = ValueAnimator.ofInt(getScrollX(), targetScrollX)
            .setDuration(200);
        valueAnimator.setInterpolator(new DecelerateInterpolator());
        valueAnimator.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int targetScrollX = (int)animation.getAnimatedValue();
                SimpleIndicator.this.scrollTo(targetScrollX, 0);
            }
        });
        valueAnimator.start();
    }

    /********* 外部可调用的方法 **********/

    //<editor-fold desc="外部调用方法">
    public void setNowHeight(int targetHeight) {

        if (!isLegalState) {return;}
        if (targetHeight == getMeasuredHeight()) {
            return;
        }
        if (targetHeight <= foldedHeight) {
            targetHeight = foldedHeight;
        }
        if (targetHeight >= fullHeight) {
            targetHeight = fullHeight;
        }
        changeSelfHeight(targetHeight);
    }

    public void setIndicatorData(List<IndicatorBean> beans) {

        this.indicatorBeans = beans;
        prepare();
        if (targetViewPager != null) {
            //设置数据使用 绑定的vp index 保持指示index 一致
            setSelected(targetViewPager.getCurrentItem());
        } else {
            setSelected(0);
        }
    }

    //当前选中item的下标
    private int selectedIndex = -1;

    /**
     * 设置第几个位置被选中
     */
    public void setSelected(int index) {

        if (indicatorBeans == null) {
            return;
        }

        index = Math.max(0, index);
        index = Math.min(indicatorBeans.size() - 1, index);
        this.selectedIndex = index;

        //已经是选中状态
        IndicatorBean targetBean = indicatorBeans.get(index);

        for (IndicatorBean bean : indicatorBeans) {
            if (bean == null) {continue;}
            bean.isSelected = false;
        }
        if (targetBean != null) {
            targetBean.isSelected = true;
        }

        postInvalidate();
    }

    public int getSelected() {
        return selectedIndex;
    }

    /**
     * 与外部ViewPager绑定，以监听滚动事件
     */
    public void bindViewPager(ViewPager viewPager) {

        if (viewPager == null || this.targetViewPager == viewPager) { return; }

        if (this.targetViewPager != null) {
            this.targetViewPager.removeOnPageChangeListener(myPageScrollListener);
        }

        this.targetViewPager = viewPager;
        viewPager.removeOnPageChangeListener(myPageScrollListener);
        viewPager.addOnPageChangeListener(myPageScrollListener);
    }

    /**
     * 收缩状态的高度
     */
    public int getFoldedHeight() {
        if (foldedHeight == 0) {
            measureSelfHeight();
        }
        return foldedHeight;
    }

    public void setDefaultTextColor(@ColorInt int normalColor) {
        this.topTextColor = normalColor;
        postInvalidate();
    }

    /**
     * 同步滚动位置
     */
    public void syncScrollX(int scrollX) {
        /**
         * 变量置为false是为了禁用onDraw方法后的{@link #autoScroll}方法
         * 否则
         * 选中item为第一个或最后一个时
         * {@link #autoScroll}会自动滚动到最左侧或最右侧
         * 可能与预期scrollX位置不符
         * */
        needExecuteAutoScroll = false;

        scrollTo(scrollX, 0);
    }
    //</editor-fold>

    /********** 内部类 **********/

    private class MyPageScrollListener implements OnPageChangeListener {

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

        }

        @Override
        public void onPageSelected(int position) {
            needExecuteAutoScroll = true;
            setSelected(position);
        }

        @Override
        public void onPageScrollStateChanged(int state) {

        }
    }

    public static class IndicatorBean {

        String topText;
        String bottomText;

        public IndicatorBean(String topText, String bottomText) {
            this.topText = TextUtils.isEmpty(topText) ? "" : topText;
            this.bottomText = TextUtils.isEmpty(bottomText) ? "" : bottomText;
        }

        /*** 内部才可用的成员变量 ****/

        boolean isSelected = false;

        int maxWidth;

        int topTextWidth;
        int bottomTextWidth;

        int topTextHeight;
        int bottomTextHeight;

        int topTextBaseLine;
        int bottomTextBaseLine;

        int topTextLeft;
        int bottomTextLeft;

        //item的左右边距坐标，用来确定点击范围
        int left;
        int right;
    }
}
