package com.github.tvbox.osc.ui.tv.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/*
 * @项目名： GooglePlay
 * @包名： com.example.flowlayoutlibrary
 * @文件名: Flowlayout
 * @创建者: lenovo
 * @创建时间: 2016/10/6 22:18
 * @描述： TODO  */

public class FlowLayout extends ViewGroup {
    private static final String TAG = "Flowlayout";
    private List<Line> mLines = new ArrayList<>();
    private int mVerticalSpace = 16; //垂直间隙
    private Line currentLine;

    public FlowLayout(Context context) {
        super(context);
    }

    public FlowLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * 继承了ViewGroup, 添加子View的时候调用测量
     *
     * @param widthMeasureSpec
     * @param heightMeasureSpec
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // 确保数据处在初始状态
        mLines.clear();
        currentLine = null;
        //6.获取宽高信息 -- 宽度固定,高度重定义
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int usableWidth = width - getPaddingLeft() - getPaddingRight();
        //7.测量子view
        for (int i = 0; i < getChildCount(); i++) {
            View childView = getChildAt(i);
            // 先测量后才有数据
            measureChild(childView, widthMeasureSpec, heightMeasureSpec);
            //8.把 childView添加到 Line
            if (currentLine == null) {
                //首次创建Line
                currentLine = new Line(usableWidth);
                mLines.add(currentLine);
                currentLine.addView(childView);
            } else {
                if (!currentLine.addView(childView)) {
                    //添加不了则新建一个再添加
                    currentLine = new Line(usableWidth);
                    mLines.add(currentLine);
                    currentLine.addView(childView);
                }
            }
        }
        //9.决定自己宽高,高度需要知道有多少行
        int height = getPaddingTop();
        for (Line line : mLines) {
            height += (line.mHeight + mVerticalSpace);
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        //10.逐行布局，发挥line的作用 -- 布局每一个Line
        l = getPaddingLeft();
        t = getPaddingTop();
        for (Line line : mLines) {
            line.layout(l, t);
            // a.控制垂直布局
            t += (line.mHeight + mVerticalSpace);
        }
    }

    /**
     * 一行View组合成的新View
     */
    private static class Line {
        public int mHeight;
        private int mMaxWidth;
        private int mUsedWidth;
        private int mSpace = 14; //水平间隙
        private List<View> mViews = new ArrayList<>();

        //2.添加子view到集合, 返回结果 -- 测量子view宽高, 更新Line已用空间
        public Line(int maxWidth) {
            mMaxWidth = maxWidth;
        }

        //1.定义line，line需要保存子view，因此定义集合
        public boolean addView(View child) {
            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();
            // 第一次添加
            if (mViews.size() == 0) {
                mUsedWidth = childWidth;
                mHeight = Math.max(childHeight, mHeight);
                mViews.add(child);
                return true;
            }
            // 3.判断是否可用继续添加 -- 剩余宽度不能少于子控件宽度
            if (mMaxWidth - mUsedWidth - mSpace < childWidth) {
                return false;
            }
            //4.添加后需要更新信息
            mUsedWidth += (mSpace + childWidth);
            mHeight = Math.max(childHeight, mHeight);
            mViews.add(child);
            return true;
        }

        /**
         * 布局Line,即是布局mViews中的每一个View
         *
         * @param l left
         * @param t top
         */
        public void layout(int l, int t) {
            // mViews除非异常, 否则不会为空
            int space = mSpace + (mMaxWidth - mUsedWidth) / mViews.size();
            // b.控制水平布局
            for (View view : mViews) {
                int r = l + view.getMeasuredWidth();
                int b = t + view.getMeasuredHeight();
                view.layout(l, t, r, b);
                // 重定义left
                l = r + space;
            }
        }
    }
}