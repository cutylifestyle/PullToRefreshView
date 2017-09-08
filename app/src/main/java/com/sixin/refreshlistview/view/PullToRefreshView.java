package com.sixin.refreshlistview.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.sixin.refreshlistview.R;


public class PullToRefreshView extends LinearLayout implements View.OnTouchListener {
    private static final String TAG = PullToRefreshView.class.getName();

    private static final String UP = "up";
    private static final String DOWN = "down";

    /**
     * 头部的view
     * */
    private View mHeaderView;

    /**
     * headerView中的进度条控件
     * */
    private ProgressBar mProgressBar;

    /**
     * headerView中的箭头
     * */
    private ImageView mArrow;

    /**
     * headerView中显示提示信息的textView
     * */
    private TextView mDescription;

    /**
     * 滑动临界值
     * */
    private int mTouchSlop;

    /**
     * 是否首次布局
     * */
    private boolean mLoadOnce;

    /**
     * 头部view的布局参数
     * */
    private MarginLayoutParams mHeaderViewParams;

    /**
     * 头部view的高度
     * */
    private int mHeaderHeight;

    private ListView mListView;
    private ListAdapter mAdapter;

    /**
     * 手指按下时的位置
     * */
    private float mDownY;

    /**
     * 是否可以下拉
     * */
    private boolean ableToPull;

    private RefreshListener mOnRefreshListener;


    public PullToRefreshView(Context context) {
        super(context);
    }

    public PullToRefreshView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        //设置布局方向
        setOrientation(VERTICAL);

        //初始化头部控件
        initHeader();
        addView(mHeaderView,0);

        initListView(context);

        //初始化滑动临界值
        mTouchSlop = ViewConfiguration.get(context).getScaledPagingTouchSlop();
    }

    public PullToRefreshView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    private void initHeader() {
        mHeaderView = LayoutInflater.from(getContext()).inflate(R.layout.header_view, null,true);
        mProgressBar = (ProgressBar) mHeaderView.findViewById(R.id.progressBar);
        mArrow = (ImageView) mHeaderView.findViewById(R.id.arrow);
        mDescription = (TextView) mHeaderView.findViewById(R.id.description);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {


        //设置listView的触摸事件
        setListViewTouchListener();

        //有一个问题非常的关键：就是onLayout方法会被调用两次，所以的操作应该只进行一次就行了
        moveHeaderView(changed);


        super.onLayout(changed, l, t, r, b);
    }

    public void setAdapter(ListAdapter adapter){
        mAdapter = adapter;
        mListView.setAdapter(adapter);
    }

    public void setOnClickListener(AdapterView.OnItemClickListener onItemClickListener){
        mListView.setOnItemClickListener(onItemClickListener);
    }

    public void setOnItemLongClickLietener(AdapterView.OnItemLongClickListener onItemLongClickListener){
        mListView.setOnItemLongClickListener(onItemLongClickListener);
    }

    public void setOnRefreshListener(RefreshListener onRefreshListener){
        mOnRefreshListener = onRefreshListener;
    }



    private void setListViewTouchListener() {
        mListView.setOnTouchListener(this);
    }

    private void initListView(Context context) {
        mListView = new ListView(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        addViewInLayout(mListView, 1, params);
    }

    /**
     * 移动头部view，控制头部控件在屏幕上显示的位置
     * */
    private void moveHeaderView(boolean changed){
        if(changed && !mLoadOnce){
            mLoadOnce = true;
            mHeaderHeight = mHeaderView.getMeasuredHeight();
            mHeaderViewParams = (MarginLayoutParams) mHeaderView.getLayoutParams();
            mHeaderViewParams.topMargin = -mHeaderHeight;
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
//        boolean ableToPull = adjustAbleToPull();
//        Log.d(TAG, "ableToPull:" + ableToPull);
        if(ableToPull){
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mDownY = event.getRawY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    float moveY = event.getRawY();
                    float distanceY = moveY - mDownY;
                    if(distanceY > mTouchSlop){
                        float dy = distanceY - mTouchSlop;
                        if(dy - mHeaderHeight > 0){
                            mHeaderViewParams.topMargin = 0;
                        }else{
                            mHeaderViewParams.topMargin = (int) (dy - mHeaderHeight);
                        }
                        mHeaderView.setLayoutParams(mHeaderViewParams);
                        if(mHeaderViewParams.topMargin > -mHeaderHeight/2){
                            mDescription.setText(getResources().getString(R.string.release_to_refresh));
                            mArrow.setImageResource(R.drawable.arrow_up);
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if(mHeaderViewParams.topMargin <= 0 && mHeaderViewParams.topMargin > -mHeaderHeight/2){
                       executeAnimator(DOWN,mHeaderViewParams.topMargin,0);
                    }else{
                       executeAnimator(UP,mHeaderViewParams.topMargin,-mHeaderHeight);
                    }
                    break;
            }
        }
        return false;
    }

    /**
     * 执行下拉、回拉动画
     * @param tag 参见 DOWN/UP
     * */
    private void executeAnimator(final String tag, int ... values){
        ValueAnimator animator = ObjectAnimator.ofInt(values);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if(DOWN.equals(tag)){
                    mProgressBar.setVisibility(View.VISIBLE);
                    mArrow.setVisibility(View.INVISIBLE);
                    mDescription.setText(getResources().getString(R.string.refreshing));

                    //进行一些逻辑操作，可能是网络请求，可能是修改ip地址等操作
                    mOnRefreshListener.onRefresh();

                }else if(UP.equals(tag)){
                    mProgressBar.setVisibility(View.INVISIBLE);
                    mArrow.setVisibility(View.VISIBLE);
                    mArrow.setImageResource(R.drawable.arrow_down);
                    mDescription.setText(getResources().getString(R.string.pull_to_refresh));
                }
            }
        });
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mHeaderViewParams.topMargin= (int) animation.getAnimatedValue();
                mHeaderView.setLayoutParams(mHeaderViewParams);
            }
        });
        animator.setDuration(200);
        animator.setInterpolator(new LinearInterpolator());
        animator.start();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                ableToPull = adjustAbleToPull();
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }

    /**
     * 判断是否可以下拉，显示头部控件
     * */
    private boolean adjustAbleToPull(){
        boolean ableToPull = false;
        View child = mListView.getChildAt(0);
        int position = mListView.getFirstVisiblePosition();
        //当listView第一个子控件可视并且子控件的top=0的时候，并且头部控件完全不可视的情况下，可以下拉
        //当listView中没有子控件的时候也可以下拉
        if(child != null){
            Log.d(TAG, "top:" + child.getTop());
            if(position == 0 && child.getTop() == 0 && mHeaderViewParams.topMargin == -mHeaderHeight) {
                ableToPull = true;
            }else{
                ableToPull = false;
            }
        }else if(mHeaderViewParams.topMargin == -mHeaderHeight){
            ableToPull = true;
        }
        return ableToPull;
    }

    public void completeRefresh(){
        executeAnimator(UP,mHeaderViewParams.topMargin,-mHeaderHeight);
    }

    public interface RefreshListener{
        void onRefresh();
    }

}
