package person.notfresh.readingshare.util;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import android.graphics.Bitmap;

import person.notfresh.readingshare.R;
import person.notfresh.readingshare.WebShortcutActivity;
import person.notfresh.readingshare.ui.document.DocumentViewerActivity;
import person.notfresh.readingshare.ui.subject.SubjectDetailActivity;

/**
 * 桌面快捷方式创建工具类
 * 使用 ShortcutManager（API 26+）创建固定快捷方式
 */
public class ShortcutUtil {

    /**
     * 创建桌面快捷方式
     * @param context 上下文
     * @param title 快捷方式名称
     * @param url 要打开的URL
     * @return 是否创建成功
     */
    public static boolean createShortcut(Context context, String title, String url) {
        try {
            // 优先尝试使用 ShortcutManager（API 26+），微信就是这样做的
            // 即使在小手机上，如果用户授予了权限，ShortcutManager 也可以工作
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                    android.util.Log.d("ShortcutUtil", "Trying ShortcutManager first");
                    boolean modernSuccess = tryCreateShortcutModern(context, title, url);
                    if (modernSuccess) {
                        // 如果成功，返回true（系统会显示确认对话框）
                        return true;
                    }
                    android.util.Log.d("ShortcutUtil", "ShortcutManager failed, fallback to INSTALL_SHORTCUT");
                } else {
                    android.util.Log.d("ShortcutUtil", "ShortcutManager not supported, use INSTALL_SHORTCUT");
                }
            }
            
            // 降级到 INSTALL_SHORTCUT 广播方式
            android.util.Log.d("ShortcutUtil", "Using INSTALL_SHORTCUT method");
            return createShortcutLegacy(context, title, url);
            
        } catch (Exception e) {
            android.util.Log.e("ShortcutUtil", "Failed to create shortcut", e);
            Toast.makeText(context, "创建快捷方式失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            return false;
        }
    }
    

    /**
     * 使用 ShortcutManager 创建快捷方式（现代方式）
     * @param iconBitmap 可选，favicon 图标 Bitmap，如果为 null 则使用默认图标
     */
    private static boolean tryCreateShortcutModern(Context context, String title, String url, Bitmap iconBitmap) {
        try {
            // 生成快捷方式ID（使用URL的hash值）
            String shortcutId = "shortcut_" + url.hashCode();
            android.util.Log.d("ShortcutUtil", "Creating shortcut: " + title + ", ID: " + shortcutId);

            // 创建Intent，用于点击快捷方式时启动WebShortcutActivity
            Intent shortcutIntent = new Intent(context, WebShortcutActivity.class);
            shortcutIntent.setAction(Intent.ACTION_VIEW);
            shortcutIntent.putExtra("url", url);
            shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

            // 构建图标
            IconCompat icon;
            if (iconBitmap != null) {
                icon = IconCompat.createWithBitmap(iconBitmap);
                android.util.Log.d("ShortcutUtil", "Using favicon bitmap icon");
            } else {
                icon = IconCompat.createWithResource(context, R.mipmap.ic_launcher);
                android.util.Log.d("ShortcutUtil", "Using default icon");
            }

            // 构建快捷方式信息
            ShortcutInfoCompat shortcutInfo = new ShortcutInfoCompat.Builder(context, shortcutId)
                    .setShortLabel(title)
                    .setLongLabel(title)
                    .setIcon(icon)
                    .setIntent(shortcutIntent)
                    .build();

            // 请求创建固定快捷方式
            boolean success = ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null);
            
            android.util.Log.d("ShortcutUtil", "requestPinShortcut returned: " + success);

            if (success) {
                android.util.Log.d("ShortcutUtil", "Shortcut creation request sent, ID: " + shortcutId);
                android.util.Log.d("ShortcutUtil", "System will show confirmation dialog (like WeChat does)");
                // 不显示Toast，让系统对话框显示（如果系统显示的话）
                // 如果系统没有显示对话框，用户会看到 INSTALL_SHORTCUT 的提示
                return true;
            } else {
                android.util.Log.w("ShortcutUtil", "Shortcut creation request failed");
                return false;
            }
        } catch (Exception e) {
            android.util.Log.e("ShortcutUtil", "Failed to create shortcut with ShortcutManager", e);
            return false;
        }
    }
    
    /**
     * 使用 ShortcutManager 创建快捷方式（现代方式）- 兼容旧接口
     */
    private static boolean tryCreateShortcutModern(Context context, String title, String url) {
        return tryCreateShortcutModern(context, title, url, null);
    }


    /**
     * 使用 INSTALL_SHORTCUT 广播创建快捷方式（传统方式，兼容性更好）
     * @param iconBitmap 可选，favicon 图标 Bitmap，如果为 null 则使用默认图标
     */
    private static boolean createShortcutLegacy(Context context, String title, String url, Bitmap iconBitmap) {
        try {
            android.util.Log.d("ShortcutUtil", "Using legacy INSTALL_SHORTCUT method");
            android.util.Log.d("ShortcutUtil", "Title: " + title + ", URL: " + url);
            
            // 创建Intent，用于点击快捷方式时启动WebShortcutActivity
            Intent shortcutIntent = new Intent(context, WebShortcutActivity.class);
            shortcutIntent.setAction(Intent.ACTION_VIEW);
            shortcutIntent.putExtra("url", url);
            shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

            // 创建快捷方式Intent
            Intent addIntent = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");
            addIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
            addIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, title);
            
            // 优先使用传入的 favicon Bitmap
            if (iconBitmap != null) {
                addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON, iconBitmap);
                android.util.Log.d("ShortcutUtil", "Using favicon bitmap icon, size: " + iconBitmap.getWidth() + "x" + iconBitmap.getHeight());
            }
            
            // 尝试使用图标资源ID（更可靠，某些启动器可能不支持Bitmap图标）
            Intent.ShortcutIconResource iconResource = 
                Intent.ShortcutIconResource.fromContext(context, R.mipmap.ic_launcher);
            addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource);
            android.util.Log.d("ShortcutUtil", "Icon resource: " + iconResource.packageName + "/" + iconResource.resourceName);
            
            // 如果没有传入 favicon，尝试使用默认图标的 Bitmap
            if (iconBitmap == null) {
                try {
                    android.graphics.drawable.Drawable drawable = androidx.core.content.ContextCompat.getDrawable(context, R.mipmap.ic_launcher);
                    if (drawable != null && drawable instanceof android.graphics.drawable.BitmapDrawable) {
                        android.graphics.drawable.BitmapDrawable bitmapDrawable = (android.graphics.drawable.BitmapDrawable) drawable;
                        android.graphics.Bitmap bitmap = bitmapDrawable.getBitmap();
                        if (bitmap != null) {
                            addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON, bitmap);
                            android.util.Log.d("ShortcutUtil", "Default bitmap icon set, size: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                        }
                    }
                } catch (Exception e) {
                    android.util.Log.w("ShortcutUtil", "Failed to set bitmap icon, using resource ID only", e);
                }
            }
            
            // 设置不允许重复创建（可选）
            addIntent.putExtra("duplicate", false);
            
            // 发送广播
            context.sendBroadcast(addIntent);
            
            android.util.Log.d("ShortcutUtil", "INSTALL_SHORTCUT broadcast sent successfully");
            android.util.Log.d("ShortcutUtil", "Note: Some ROMs (Xiaomi, Huawei) may block this broadcast");
            android.util.Log.d("ShortcutUtil", "If shortcut not appears, user may need to manually add it or grant permission");
            
            Toast.makeText(context, "已添加快捷方式\n如果未显示，某些设备需要手动添加或授权", Toast.LENGTH_LONG).show();
            return true;
        } catch (Exception e) {
            android.util.Log.e("ShortcutUtil", "Failed to create shortcut with INSTALL_SHORTCUT", e);
            android.util.Log.e("ShortcutUtil", "Exception: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
            Toast.makeText(context, "创建快捷方式失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            return false;
        }
    }
    
    /**
     * 使用 INSTALL_SHORTCUT 广播创建快捷方式（传统方式，兼容性更好）- 兼容旧接口
     */
    private static boolean createShortcutLegacy(Context context, String title, String url) {
        return createShortcutLegacy(context, title, url, null);
    }
    
    /**
     * 创建桌面快捷方式（支持 favicon）
     * @param context 上下文
     * @param title 快捷方式名称
     * @param url 要打开的URL
     * @param iconBitmap 可选，favicon 图标 Bitmap，如果为 null 则使用默认图标
     * @return 是否创建成功
     */
    public static boolean createShortcut(Context context, String title, String url, Bitmap iconBitmap) {
        try {
            // 优先尝试使用 ShortcutManager（API 26+），微信就是这样做的
            // 即使在小手机上，如果用户授予了权限，ShortcutManager 也可以工作
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                    android.util.Log.d("ShortcutUtil", "Trying ShortcutManager first");
                    boolean modernSuccess = tryCreateShortcutModern(context, title, url, iconBitmap);
                    if (modernSuccess) {
                        // 如果成功，返回true（系统会显示确认对话框）
                        return true;
                    }
                    android.util.Log.d("ShortcutUtil", "ShortcutManager failed, fallback to INSTALL_SHORTCUT");
                } else {
                    android.util.Log.d("ShortcutUtil", "ShortcutManager not supported, use INSTALL_SHORTCUT");
                }
            }
            
            // 降级到 INSTALL_SHORTCUT 广播方式
            android.util.Log.d("ShortcutUtil", "Using INSTALL_SHORTCUT method");
            return createShortcutLegacy(context, title, url, iconBitmap);
            
        } catch (Exception e) {
            android.util.Log.e("ShortcutUtil", "Failed to create shortcut", e);
            Toast.makeText(context, "创建快捷方式失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    /**
     * 创建主题桌面快捷方式
     * @param context 上下文
     * @param title 快捷方式名称（主题标题）
     * @param subjectId 主题ID
     * @param iconBitmap 可选，自定义图标 Bitmap，如果为 null 则使用默认图标
     * @return 是否创建成功
     */
    public static boolean createSubjectShortcut(Context context, String title, long subjectId, android.graphics.Bitmap iconBitmap) {
        try {
            // 优先尝试使用 ShortcutManager（API 26+）
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                    android.util.Log.d("ShortcutUtil", "Trying ShortcutManager for subject shortcut");
                    boolean modernSuccess = tryCreateSubjectShortcutModern(context, title, subjectId, iconBitmap);
                    if (modernSuccess) {
                        return true;
                    }
                    android.util.Log.d("ShortcutUtil", "ShortcutManager failed, fallback to INSTALL_SHORTCUT");
                } else {
                    android.util.Log.d("ShortcutUtil", "ShortcutManager not supported, use INSTALL_SHORTCUT");
                }
            }
            
            // 降级到 INSTALL_SHORTCUT 广播方式
            android.util.Log.d("ShortcutUtil", "Using INSTALL_SHORTCUT method for subject");
            return createSubjectShortcutLegacy(context, title, subjectId, iconBitmap);
            
        } catch (Exception e) {
            android.util.Log.e("ShortcutUtil", "Failed to create subject shortcut", e);
            Toast.makeText(context, "创建快捷方式失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    /**
     * 创建主题桌面快捷方式（兼容旧接口，使用默认图标）
     */
    public static boolean createSubjectShortcut(Context context, String title, long subjectId) {
        return createSubjectShortcut(context, title, subjectId, null);
    }

    /**
     * 使用 ShortcutManager 创建主题快捷方式（现代方式）
     * @param iconBitmap 可选，自定义图标 Bitmap，如果为 null 则使用默认图标
     */
    private static boolean tryCreateSubjectShortcutModern(Context context, String title, long subjectId, android.graphics.Bitmap iconBitmap) {
        try {
            // 生成快捷方式ID（使用主题ID）
            String shortcutId = "subject_" + subjectId;
            android.util.Log.d("ShortcutUtil", "Creating subject shortcut: " + title + ", ID: " + shortcutId);

            // 创建Intent，用于点击快捷方式时启动SubjectDetailActivity
            Intent shortcutIntent = new Intent(context, SubjectDetailActivity.class);
            shortcutIntent.setAction(Intent.ACTION_VIEW); // 设置 action，与 URL 快捷方式保持一致
            shortcutIntent.putExtra(SubjectDetailActivity.EXTRA_SUBJECT_ID, subjectId);
            shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

            // 构建图标
            IconCompat icon;
            if (iconBitmap != null) {
                icon = IconCompat.createWithBitmap(iconBitmap);
                android.util.Log.d("ShortcutUtil", "Using custom bitmap icon for subject");
            } else {
                icon = IconCompat.createWithResource(context, R.mipmap.ic_launcher);
                android.util.Log.d("ShortcutUtil", "Using default icon for subject");
            }

            // 构建快捷方式信息
            ShortcutInfoCompat shortcutInfo = new ShortcutInfoCompat.Builder(context, shortcutId)
                    .setShortLabel(title)
                    .setLongLabel(title)
                    .setIcon(icon)
                    .setIntent(shortcutIntent)
                    .build();

            // 请求创建固定快捷方式
            boolean success = ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null);
            
            android.util.Log.d("ShortcutUtil", "requestPinShortcut returned: " + success);

            if (success) {
                android.util.Log.d("ShortcutUtil", "Subject shortcut creation request sent, ID: " + shortcutId);
                return true;
            } else {
                android.util.Log.w("ShortcutUtil", "Subject shortcut creation request failed");
                return false;
            }
        } catch (Exception e) {
            android.util.Log.e("ShortcutUtil", "Failed to create subject shortcut with ShortcutManager", e);
            return false;
        }
    }

    /**
     * 使用 INSTALL_SHORTCUT 广播创建主题快捷方式（传统方式）
     * @param iconBitmap 可选，自定义图标 Bitmap，如果为 null 则使用默认图标
     */
    private static boolean createSubjectShortcutLegacy(Context context, String title, long subjectId, android.graphics.Bitmap iconBitmap) {
        try {
            android.util.Log.d("ShortcutUtil", "Using legacy INSTALL_SHORTCUT method for subject");
            android.util.Log.d("ShortcutUtil", "Title: " + title + ", SubjectId: " + subjectId);
            
            // 创建Intent，用于点击快捷方式时启动SubjectDetailActivity
            Intent shortcutIntent = new Intent(context, SubjectDetailActivity.class);
            shortcutIntent.setAction(Intent.ACTION_VIEW); // 设置 action，与 URL 快捷方式保持一致
            shortcutIntent.putExtra(SubjectDetailActivity.EXTRA_SUBJECT_ID, subjectId);
            shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

            // 创建快捷方式Intent
            Intent addIntent = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");
            addIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
            addIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, title);
            
            // 优先使用传入的自定义图标 Bitmap
            if (iconBitmap != null) {
                addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON, iconBitmap);
                android.util.Log.d("ShortcutUtil", "Using custom bitmap icon for subject, size: " + iconBitmap.getWidth() + "x" + iconBitmap.getHeight());
            }
            
            // 使用图标资源ID（更可靠，某些启动器可能不支持Bitmap图标）
            Intent.ShortcutIconResource iconResource = 
                Intent.ShortcutIconResource.fromContext(context, R.mipmap.ic_launcher);
            addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource);
            android.util.Log.d("ShortcutUtil", "Icon resource: " + iconResource.packageName + "/" + iconResource.resourceName);
            
            // 如果没有传入自定义图标，尝试使用默认图标的 Bitmap
            if (iconBitmap == null) {
                try {
                    android.graphics.drawable.Drawable drawable = androidx.core.content.ContextCompat.getDrawable(context, R.mipmap.ic_launcher);
                    if (drawable != null && drawable instanceof android.graphics.drawable.BitmapDrawable) {
                        android.graphics.drawable.BitmapDrawable bitmapDrawable = (android.graphics.drawable.BitmapDrawable) drawable;
                        android.graphics.Bitmap bitmap = bitmapDrawable.getBitmap();
                        if (bitmap != null) {
                            addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON, bitmap);
                            android.util.Log.d("ShortcutUtil", "Default bitmap icon set, size: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                        }
                    }
                } catch (Exception e) {
                    android.util.Log.w("ShortcutUtil", "Failed to set bitmap icon, using resource ID only", e);
                }
            }
            
            // 设置不允许重复创建
            addIntent.putExtra("duplicate", false);
            
            // 发送广播
            context.sendBroadcast(addIntent);
            
            android.util.Log.d("ShortcutUtil", "INSTALL_SHORTCUT broadcast sent successfully for subject");
            android.util.Log.d("ShortcutUtil", "Note: Some ROMs (Xiaomi, Huawei) may block this broadcast");
            
            Toast.makeText(context, "已添加快捷方式\n如果未显示，某些设备需要手动添加或授权", Toast.LENGTH_LONG).show();
            return true;
        } catch (Exception e) {
            android.util.Log.e("ShortcutUtil", "Failed to create subject shortcut with INSTALL_SHORTCUT", e);
            android.util.Log.e("ShortcutUtil", "Exception: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
            Toast.makeText(context, "创建快捷方式失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    /**
     * 创建文档桌面快捷方式
     * @param context 上下文
     * @param title 快捷方式名称(文档标题)
     * @param documentId 文档 ID,点击快捷方式后传给 DocumentViewerActivity
     * @param iconBitmap 可选,自定义图标 Bitmap,如果为 null 则使用默认图标
     * @return 是否创建成功
     */
    public static boolean createDocumentShortcut(Context context, String title, long documentId, Bitmap iconBitmap) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                    boolean modernSuccess = tryCreateDocumentShortcutModern(context, title, documentId, iconBitmap);
                    if (modernSuccess) {
                        return true;
                    }
                }
            }
            return createDocumentShortcutLegacy(context, title, documentId, iconBitmap);
        } catch (Exception e) {
            android.util.Log.e("ShortcutUtil", "Failed to create document shortcut", e);
            Toast.makeText(context, "创建快捷方式失败:" + e.getMessage(), Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    /**
     * 创建文档桌面快捷方式(兼容旧接口,使用默认图标)
     */
    public static boolean createDocumentShortcut(Context context, String title, long documentId) {
        return createDocumentShortcut(context, title, documentId, null);
    }

    private static boolean tryCreateDocumentShortcutModern(Context context, String title, long documentId, Bitmap iconBitmap) {
        try {
            String shortcutId = "document_" + documentId;
            Intent shortcutIntent = new Intent(context, DocumentViewerActivity.class);
            shortcutIntent.setAction(Intent.ACTION_VIEW);
            shortcutIntent.putExtra("document_id", documentId);
            shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

            IconCompat icon;
            if (iconBitmap != null) {
                icon = IconCompat.createWithBitmap(iconBitmap);
            } else {
                icon = IconCompat.createWithResource(context, R.mipmap.ic_launcher);
            }

            ShortcutInfoCompat info = new ShortcutInfoCompat.Builder(context, shortcutId)
                    .setShortLabel(title)
                    .setLongLabel(title)
                    .setIcon(icon)
                    .setIntent(shortcutIntent)
                    .build();

            return ShortcutManagerCompat.requestPinShortcut(context, info, null);
        } catch (Exception e) {
            android.util.Log.e("ShortcutUtil", "Modern document shortcut failed", e);
            return false;
        }
    }

    private static boolean createDocumentShortcutLegacy(Context context, String title, long documentId, Bitmap iconBitmap) {
        Intent shortcutIntent = new Intent(context, DocumentViewerActivity.class);
        shortcutIntent.setAction(Intent.ACTION_VIEW);
        shortcutIntent.putExtra("document_id", documentId);
        shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        Intent intent = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, title);
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        if (iconBitmap != null) {
            intent.putExtra(Intent.EXTRA_SHORTCUT_ICON, iconBitmap);
        } else {
            Intent.ShortcutIconResource iconResource = Intent.ShortcutIconResource.fromContext(context, R.mipmap.ic_launcher);
            intent.putExtra(Intent.EXTRA_SHORTCUT_ICON, iconResource);
        }
        context.sendBroadcast(intent);
        return true;
    }
}

