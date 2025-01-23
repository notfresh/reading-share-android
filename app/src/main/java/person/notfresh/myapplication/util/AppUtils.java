package person.notfresh.myapplication.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

public class AppUtils {

    /**
     * 检查设备上是否安装了指定包名的应用
     *
     * @param context 上下文
     * @param packageName 应用的包名
     * @return 如果安装了返回 true，否则返回 false
     * 
     * TODO There is some wrong with this method, Even if the app is installed, it will return false, maybe the permission is not granted
     * The context must be the application context
     */
    public static boolean isAppInstalled(Context context, String packageName) {
        PackageManager packageManager = context.getPackageManager();
        try {
            packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            Log.d("AppUtils", packageName + " is installed.");
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            Log.d("AppUtils", packageName + " is not installed.");
            return false;
        }
    }
} 