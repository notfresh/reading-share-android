package person.notfresh.readingshare.util;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import person.notfresh.readingshare.adapter.LinksAdapter;
import person.notfresh.readingshare.model.LinkItem;

public class SwipeActionsHelper {
    
    private final LinksAdapter adapter;
    private ItemTouchHelper itemTouchHelper;
    private Handler handler;
    private int activePosition = -1; // 跟踪当前活动的滑动项
    private static final int BUTTONS_DISPLAY_DURATION = 3000; // 按钮显示持续时间(3秒)
    
    // 声明一个记录当前按钮宽度的变量
    private float buttonWidth = 0;
    // 标记是否正在处理关闭操作
    private boolean isClosingItem = false;
    // 标记当前是否在处理向右滑动关闭
    private boolean isRightSwipingToClose = false;
    
    public SwipeActionsHelper(LinksAdapter adapter) {
        this.adapter = adapter;
        this.itemTouchHelper = new ItemTouchHelper(createSwipeCallback());
        this.handler = new Handler(Looper.getMainLooper());
    }
    
    public void attachToRecyclerView(RecyclerView recyclerView) {
        itemTouchHelper.attachToRecyclerView(recyclerView);
        
        // 为 RecyclerView 添加触摸监听器来处理点击
        recyclerView.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            private float startX = 0;
            private boolean isTracking = false;
            
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = e.getX();
                        isTracking = activePosition != -1;
                        break;
                        
                    case MotionEvent.ACTION_MOVE:
                        // 如果正在追踪活动项，检查是否是向右滑动
                        if (isTracking && activePosition != -1) {
                            float dx = e.getX() - startX;
                            if (dx > 30) { // 向右滑动超过阈值
                                isRightSwipingToClose = true;
                                closeActiveItem();
                                return true;
                            }
                        }
                        break;
                        
                    case MotionEvent.ACTION_UP:
                        if (isRightSwipingToClose) {
                            isRightSwipingToClose = false;
                            return true;
                        }
                        
                        // 处理按钮点击
                        if (activePosition != -1) {
                            View childView = rv.findChildViewUnder(e.getX(), e.getY());
                            if (childView != null) {
                                RecyclerView.ViewHolder viewHolder = rv.getChildViewHolder(childView);
                                if (viewHolder.getAdapterPosition() == activePosition) {
                                    // 计算点击位置
                                    float x = e.getX() - childView.getLeft();
                                    float rightEdge = childView.getRight();
                                    
                                    // 判断点击的是哪个按钮
                                    if (x >= rightEdge - buttonWidth && x <= rightEdge) {
                                        // 点击了按钮区域
                                        boolean isArchiveButton = x >= rightEdge - buttonWidth/2;
                                        
                                        if (isArchiveButton) {
                                            // 点击了归档按钮
                                            Toast.makeText(rv.getContext(), "归档功能待实现", Toast.LENGTH_SHORT).show();
                                        } else {
                                            // 点击了删除按钮
                                            Toast.makeText(rv.getContext(), "删除功能待实现", Toast.LENGTH_SHORT).show();
                                        }
                                        
                                        // 重置状态
                                        closeActiveItem();
                                        return true;
                                    }
                                }
                            }
                        }
                        isTracking = false;
                        break;
                }
                return false;
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {}

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {}
        });
    }
    
    // 关闭当前活动项目
    private void closeActiveItem() {
        if (activePosition != -1) {
            isClosingItem = true;
            adapter.notifyItemChanged(activePosition);
            activePosition = -1;
            handler.removeCallbacksAndMessages(null);
            
            // 重置关闭标记
            handler.postDelayed(() -> isClosingItem = false, 200);
        }
    }
    
    private ItemTouchHelper.SimpleCallback createSwipeCallback() {
        return new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false; // 不支持拖拽
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // 如果是正在关闭，直接返回
                if (isClosingItem || isRightSwipingToClose) {
                    adapter.notifyItemChanged(viewHolder.getAdapterPosition());
                    return;
                }
                
                int position = viewHolder.getAdapterPosition();
                
                // 如果是向右滑动且当前项是活动项，则关闭它
                if (direction == ItemTouchHelper.RIGHT && position == activePosition) {
                    closeActiveItem();
                    return;
                }
                
                // 如果是向右滑动但不是活动项，恢复原状
                if (direction == ItemTouchHelper.RIGHT) {
                    adapter.notifyItemChanged(position);
                    return;
                }
                
                // 处理向左滑动: 打开操作按钮
                
                // 如果是新滑动的条目，关闭之前的条目
                if (activePosition != -1 && activePosition != position) {
                    closeActiveItem();
                    // 延迟一段时间再设置新的活动项，防止冲突
                    handler.postDelayed(() -> {
                        if (!isClosingItem) {
                            activePosition = position;
                            
                            // 设置定时器在3秒后恢复原位
                            handler.postDelayed(() -> {
                                if (activePosition == position) {
                                    adapter.notifyItemChanged(position);
                                    activePosition = -1;
                                }
                            }, BUTTONS_DISPLAY_DURATION);
                        } else {
                            // 如果仍在关闭，恢复滑动项的原始状态
                            adapter.notifyItemChanged(position);
                        }
                    }, 100);
                } else {
                    activePosition = position;
                    
                    // 设置定时器在3秒后恢复原位
                    handler.postDelayed(() -> {
                        if (activePosition == position) {
                            adapter.notifyItemChanged(position);
                            activePosition = -1;
                        }
                    }, BUTTONS_DISPLAY_DURATION);
                }
            }

            @Override
            public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
                return isRightSwipingToClose ? 0.1f : 0.3f; // 向右滑动时使用更小的阈值
            }
            
            @Override
            public int getSwipeDirs(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                // 如果正在关闭，禁止所有滑动
                if (isClosingItem || isRightSwipingToClose) {
                    return 0;
                }
                
                // 在选择模式下或对于不是链接的项目禁用滑动
                if (!(viewHolder instanceof LinksAdapter.LinkViewHolder) || adapter.isInSelectionMode()) {
                    return 0;
                }
                
                // 如果是当前活动项，只允许向右滑动关闭
                if (viewHolder.getAdapterPosition() == activePosition) {
                    return ItemTouchHelper.RIGHT;
                }
                
                // 如果已有活动项，禁止其他项滑动
                if (activePosition != -1) {
                    return 0;
                }
                
                // 非活动项只允许向左滑动打开
                return ItemTouchHelper.LEFT;
            }
            
            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, 
                                  @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, 
                                  int actionState, boolean isCurrentlyActive) {
                
                // 只处理 LinkViewHolder
                if (!(viewHolder instanceof LinksAdapter.LinkViewHolder)) {
                    super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
                    return;
                }
                
                // 如果正在关闭，不绘制任何内容
                if (isClosingItem || isRightSwipingToClose) {
                    return;
                }

                View itemView = viewHolder.itemView;
                int position = viewHolder.getAdapterPosition();
                
                // 计算按钮宽度
                if (buttonWidth == 0) {
                    buttonWidth = itemView.getWidth() / 4.0f * 2; // 两个按钮的总宽度
                }
                
                // 如果是活动项且用户向右滑动，则关闭它
                if (position == activePosition && dX > 20 && isCurrentlyActive) { // 增加判断阈值
                    isRightSwipingToClose = true;
                    closeActiveItem();
                    return;
                }
                
                // 检查是否开始滑动新的条目
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && isCurrentlyActive) {
                    if (position != activePosition && activePosition != -1) {
                        // 发现用户开始滑动新的条目，关闭之前的条目
                        closeActiveItem();
                        return; // 直接返回，稍后再处理新条目
                    }
                }

                // 判断是否为活动项目且处于展开状态
                if (position == activePosition && !isCurrentlyActive) {
                    // 保持在展开状态
                    super.onChildDraw(c, recyclerView, viewHolder, -buttonWidth, dY, actionState, isCurrentlyActive);
                    return;
                }
                
                // 限制滑动范围
                if (position == activePosition) {
                    // 活动项只能向右滑动
                    if (dX < 0) {
                        dX = 0;
                    }
                } else {
                    // 非活动项只能向左滑动
                    if (dX > 0) {
                        dX = 0;
                    }
                    
                    // 限制最大滑动范围
                    if (dX < -buttonWidth) {
                        dX = -buttonWidth;
                    }
                }

                // 只在向左滑动过程中绘制背景和按钮
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX < 0) {
                    // 绘制背景
                    Paint paint = new Paint();
                    
                    // 计算按钮区域
                    float halfButtonWidth = buttonWidth / 2;
                    
                    // 归档按钮背景
                    paint.setColor(Color.parseColor("#4CAF50"));
                    RectF archiveButtonRect = new RectF(
                            itemView.getRight() - halfButtonWidth,
                            itemView.getTop(),
                            itemView.getRight(),
                            itemView.getBottom()
                    );
                    c.drawRect(archiveButtonRect, paint);
                    
                    // 删除按钮背景
                    paint.setColor(Color.parseColor("#F44336"));
                    RectF deleteButtonRect = new RectF(
                            itemView.getRight() - buttonWidth,
                            itemView.getTop(),
                            itemView.getRight() - halfButtonWidth,
                            itemView.getBottom()
                    );
                    c.drawRect(deleteButtonRect, paint);
                    
                    // 绘制文字
                    paint.setColor(Color.WHITE);
                    paint.setTextSize(36);
                    paint.setTextAlign(Paint.Align.CENTER);
                    
                    // 归档文本
                    float archiveTextX = (archiveButtonRect.left + archiveButtonRect.right) / 2;
                    float textY = (itemView.getTop() + itemView.getBottom()) / 2 + 12;
                    c.drawText("归档", archiveTextX, textY, paint);
                    
                    // 删除文本
                    float deleteTextX = (deleteButtonRect.left + deleteButtonRect.right) / 2;
                    c.drawText("删除", deleteTextX, textY, paint);
                }
                
                // 绘制主视图偏移
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        };
    }
} 