package person.notfresh.readingshare.util;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import person.notfresh.readingshare.adapter.LinksAdapter;
import person.notfresh.readingshare.model.LinkItem;

public class SwipeActionsHelper {
    
    private static final String TAG = "SwipeActionsHelper";
    private final LinksAdapter adapter;
    private ItemTouchHelper itemTouchHelper;
    private Handler handler;
    private int activePosition = -1; // 跟踪当前活动的滑动项
    private static final int BUTTONS_DISPLAY_DURATION = 1000; // 按钮显示持续时间(1秒)
    private String userEnv;
    // 存储当前滑动的Item
    private LinkItem currentSwipedItem;

    // 声明一个记录当前按钮宽度的变量
    private float buttonWidth = 200; // 设置一个固定值，确保按钮有足够宽度被点击
    // 标记是否正在处理关闭操作
    private boolean isClosingItem = false;
    // 标记当前是否在处理向右滑动关闭
    private boolean isRightSwipingToClose = false;
    
    public SwipeActionsHelper(LinksAdapter adapter) {
        this.adapter = adapter;
        this.itemTouchHelper = new ItemTouchHelper(createSwipeCallback());
        this.handler = new Handler(Looper.getMainLooper());
        this.userEnv = adapter.DEFAULT_USER_ENV; // 使用默认用户环境
    }

    public SwipeActionsHelper(LinksAdapter adapter, String userEnv) {
        this.adapter = adapter;
        this.itemTouchHelper = new ItemTouchHelper(createSwipeCallback());
        this.handler = new Handler(Looper.getMainLooper());
        this.userEnv = userEnv;
    }
    
    // 设置当前被滑动的Item
    public void setCurrentSwipedItem(LinkItem item) {
        this.currentSwipedItem = item;
        Log.d(TAG, "当前滑动项设置为: " + (item != null ? item.getTitle() : "null"));
    }
    
    // 获取当前被滑动的Item
    public LinkItem getCurrentSwipedItem() {
        return currentSwipedItem;
    }
    
    public void attachToRecyclerView(RecyclerView recyclerView) {
        Log.d(TAG, "attachToRecyclerView");
        itemTouchHelper.attachToRecyclerView(recyclerView);
        
        // 为 RecyclerView 添加触摸监听器来处理点击
        recyclerView.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            private float startX = 0;
            private boolean isTracking = false;
            
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                Log.d(TAG, "onInterceptTouchEvent: action=" + e.getAction() + ", activePosition=" + activePosition);
                
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = e.getX();
                        isTracking = activePosition != -1;
                        Log.d(TAG, "ACTION_DOWN: startX=" + startX + ", isTracking=" + isTracking);
                        break;
                        
                    case MotionEvent.ACTION_MOVE:
                        // 如果正在追踪活动项，检查是否是向右滑动
                        if (isTracking && activePosition != -1) {
                            float dx = e.getX() - startX;
                            Log.d(TAG, "ACTION_MOVE: dx=" + dx);
                            if (dx > 30) { // 向右滑动超过阈值
                                isRightSwipingToClose = true;
                                closeActiveItem();
                                return true;
                            }
                        }
                        break;
                        
                    case MotionEvent.ACTION_UP:
                        Log.d(TAG, "ACTION_UP: x=" + e.getX() + ", activePosition=" + activePosition);
                        
                        if (isRightSwipingToClose) {
                            isRightSwipingToClose = false;
                            return true;
                        }
                        
                        // 处理按钮点击
                        if (activePosition != -1) {
                            Log.d(TAG, "ACTION_UP with activePosition: " + activePosition);
                            
                            try {
                                // 获取当前视图
                                RecyclerView.ViewHolder viewHolder = rv.findViewHolderForAdapterPosition(activePosition);
                                
                                if (viewHolder == null) {
                                    Log.e(TAG, "ViewHolder is null for position: " + activePosition);
                                    return false;
                                }
                                
                                View activeView = viewHolder.itemView;
                                float viewRight = activeView.getRight();
                                float viewLeft = activeView.getLeft();
                                float touchX = e.getX();
                                
                                Log.d(TAG, "Button check: touchX=" + touchX + ", viewRight=" + viewRight + 
                                     ", buttonWidth=" + buttonWidth);
                                
                                // 简化判断逻辑 - 只要是活动项且点击接近右侧
                                if (touchX > viewRight - buttonWidth) {
                                    Log.d(TAG, "Button area clicked!");
                                    
                                    // 使用当前滑动的Item执行操作
                                    if (currentSwipedItem != null) {
                                        // 根据用户环境执行不同操作
                                        if ("archive".equals(userEnv)) {
                                            Toast.makeText(rv.getContext(), 
                                                "归档操作: " + currentSwipedItem.getTitle(), 
                                                Toast.LENGTH_SHORT).show();
                                            // 在这里执行归档特定操作
                                        } else {
                                            Toast.makeText(rv.getContext(), 
                                                "主环境操作: " + currentSwipedItem.getTitle(), 
                                                Toast.LENGTH_SHORT).show();
                                            // 执行主环境下的操作
                                        }
                                    } else {
                                        Toast.makeText(rv.getContext(), 
                                            "无法获取当前项目信息", 
                                            Toast.LENGTH_SHORT).show();
                                    }
                                    
                                    closeActiveItem();
                                    return true;
                                }
                            } catch (Exception ex) {
                                Log.e(TAG, "Error processing touch: " + ex.getMessage(), ex);
                            }
                        }
                        isTracking = false;
                        break;
                }
                return false;
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                Log.d(TAG, "onTouchEvent: " + e.getAction());
            }

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {}
        });
    }
    
    // 关闭当前活动项目
    private void closeActiveItem() {
        Log.d(TAG, "closeActiveItem: " + activePosition);
        if (activePosition != -1) {
            isClosingItem = true;
            int positionToClose = activePosition;
            activePosition = -1;
            
            // 清除当前滑动项引用
            currentSwipedItem = null;
            
            // 确保刷新以恢复状态
            adapter.notifyItemChanged(positionToClose);
            
            // 清除定时器
            handler.removeCallbacksAndMessages(null);
            
            // 延迟重置标记，确保视图完成更新
            handler.postDelayed(() -> {
                isClosingItem = false;
                Log.d(TAG, "Closing animation completed");
            }, 300);
        }
    }
    
    private ItemTouchHelper.SimpleCallback createSwipeCallback() {
        return new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false; // 不支持拖拽
            }

            // 控制滑动距离
            @Override
            public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
                return 0.15f; // 只需要轻微滑动即可触发
            }
            
            // 确保滑动更容易触发
            @Override 
            public float getSwipeEscapeVelocity(float defaultValue) {
                return defaultValue * 0.5f; // 降低触发滑动所需的速度
            }
            
            @Override
            public int getSwipeDirs(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                // 如果是标题或者正在选择模式，禁用滑动
                if (!(viewHolder instanceof LinksAdapter.LinkViewHolder) || adapter.isInSelectionMode()) {
                    return 0;
                }
                
                // 其他情况允许左滑
                return ItemTouchHelper.LEFT;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                Log.d(TAG, "onSwiped: position=" + position);
                
                // 标记活动位置
                activePosition = position;
                
                // 获取当前滑动的LinkItem并保存
                Object item = adapter.getItemAtPosition(position);
                if (item instanceof LinkItem) {
                    setCurrentSwipedItem((LinkItem) item);
                } else {
                    setCurrentSwipedItem(null);
                    Log.w(TAG, "滑动项不是LinkItem类型");
                }
                
                // 设置定时器延迟关闭
                handler.removeCallbacksAndMessages(null);
                handler.postDelayed(() -> {
                    Log.d(TAG, "Timer expired for position: " + position);
                    if (activePosition == position) {
                        closeActiveItem();
                    }
                }, BUTTONS_DISPLAY_DURATION);
            }
            
            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                              @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY,
                              int actionState, boolean isCurrentlyActive) {
                
                int position = viewHolder.getAdapterPosition();
                View itemView = viewHolder.itemView;
                
                // 处理活动项
                if (position == activePosition && !isClosingItem) {
                    // 如果是活动项，始终固定在最左侧
                    drawButtons(c, itemView, -buttonWidth);
                    
                    // 固定项目位置
                    super.onChildDraw(c, recyclerView, viewHolder, -buttonWidth, dY, actionState, false);
                    return;
                }
                
                // 限制最大滑动距离
                if (dX < -buttonWidth) {
                    dX = -buttonWidth;
                }
                
                // 处理正常滑动
                if (dX < 0) {
                    drawButtons(c, itemView, dX);
                }
                
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
            
            // 抽取绘制按钮的方法以避免代码重复
            private void drawButtons(Canvas c, View itemView, float dX) {
                // 根据用户环境选择不同的颜色和文本
                int backgroundColor;
                String text;
                
                if ("archive".equals(userEnv)) {
                    backgroundColor = Color.rgb(139, 0, 0); // 深红色
                    text = "恢复";
                } else {
                    backgroundColor = Color.rgb(0, 0, 140); // 深蓝色
                    text = "归档";
                }
                
                // 绘制背景
                Paint paint = new Paint();
                paint.setColor(backgroundColor);
                
                RectF background = new RectF(
                    itemView.getRight() + dX,
                    itemView.getTop(),
                    itemView.getRight(),
                    itemView.getBottom()
                );
                
                c.drawRect(background, paint);
                
                // 绘制文字
                paint.setColor(Color.WHITE);
                paint.setTextSize(36);
                
                // 计算文字位置
                float textWidth = paint.measureText(text);
                float textX = itemView.getRight() - (Math.abs(dX) / 2) - (textWidth / 2);
                float textY = itemView.getTop() + (itemView.getHeight() / 2) + 12;
                
                c.drawText(text, textX, textY, paint);
            }
            
            // 这个方法非常重要 - 返回false使得swipe动作不会复位
            @Override
            public boolean isItemViewSwipeEnabled() {
                return activePosition == -1; // 只有没有活动项时才允许新的滑动
            }
        };
    }
} 