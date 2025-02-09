# 版本标记

   git tag -a v1.0 -m "Release version 1.0"

## 删除tag

要删除 Git 标签，你可以使用以下步骤：

### 删除本地标签

1. **查看现有标签**：

   ```bash
   git tag
   ```
2. **删除本地标签**：

   - 使用以下命令删除本地标签：
     ```bash
     git tag -d <tagname>
     ```
   - 例如，要删除名为 `v1.0` 的标签：
     ```bash
     git tag -d v1.0
     ```

### 删除远程标签

1. **删除远程标签**：

   - 使用以下命令删除远程标签：
     ```bash
     git push origin --delete <tagname>
     ```
   - 例如，要删除远程的 `v1.0` 标签：
     ```bash
     git push origin --delete v1.0
     ```
2. **验证删除**：

   - 再次查看远程标签，确保标签已被删除：
     ```bash
     git ls-remote --tags origin
     ```

### 注意事项

- **确保正确的标签名称**：在删除标签之前，确保你输入了正确的标签名称，以避免误删。
- **权限**：确保你有权限对远程仓库进行推送操作。

通过这些步骤，你可以成功删除本地和远程的 Git 标签。

# Android 分享到功能

```
 Intent shareIntent = new Intent(Intent.ACTION_SEND);

shareIntent.setType("text/plain");

shareIntent.putExtra(Intent.EXTRA_TEXT, shareText.toString());
Intent chooserIntent = Intent.createChooser(shareIntent, "分享到");
String myPackageName = context.getPackageName();

chooserIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

chooserIntent.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, 

new ComponentName[]{new ComponentName(myPackageName, myPackageName + ".MainActivity")});


context.startActivity(chooserIntent);


Intent chooserIntent = Intent.createChooser(shareIntent, "分享到");
```

# 如何打包

在 Android Studio 中指定版本并打包应用程序通常涉及以下几个步骤：

### 1. 指定应用版本

在 Android Studio 中，应用的版本信息通常在 `build.gradle` 文件中指定。你需要编辑 `app` 模块下的 `build.gradle` 文件。

```groovy
// ... existing code ...
android {
    // ... existing code ...
    defaultConfig {
        // ... existing code ...
        versionCode 1 // 版本代码，通常用于内部版本管理
        versionName "1.0" // 版本名称，用户可见
        // ... existing code ...
    }
    // ... existing code ...
}
// ... existing code ...
```

- `versionCode` 是一个整数，每次发布新版本时都需要增加。
- `versionName` 是一个字符串，通常用于显示给用户。

### 2. 打包应用

要打包应用程序，你可以使用 Android Studio 的“生成 APK”功能。以下是步骤：

1. **打开 Android Studio** 并加载你的项目。
2. **选择“Build”菜单**，然后选择“Build Bundle(s) / APK(s)”。
3. **选择“Build APK(s)”**，这将开始构建过程。
4. 构建完成后，Android Studio 会在右下角弹出一个通知，提示你 APK 已经生成。
5. 点击通知中的“Locate”按钮，打开 APK 文件所在的目录。

### 3. 签名 APK

在发布应用之前，你需要对 APK 进行签名。以下是签名步骤：

1. **选择“Build”菜单**，然后选择“Generate Signed Bundle / APK...”
2. 选择“APK”，然后点击“Next”。
3. 如果你已经有一个密钥库，选择它并输入相关信息。如果没有，你需要创建一个新的密钥库。
4. 选择构建类型（通常是“Release”）和签名版本。
5. 点击“Finish”开始生成签名的 APK。

完成后，你将获得一个签名的 APK 文件，可以用于发布。

### 4. 发布应用

一旦你有了签名的 APK，你可以将其上传到 Google Play 商店或其他应用分发平台。

希望这些步骤能帮助你成功指定版本并打包你的 Android 应用！如果有其他问题，请随时问我。

# 版本号设置

在 Android 项目中，应用的版本号通常在 `app` 模块的 `build.gradle` 文件中设置。以下是如何设置版本号的步骤：

### 设置版本号

1. **打开 `build.gradle` 文件**：

   - 在 Android Studio 中，导航到 `app` 模块的 `build.gradle` 文件。通常在项目视图中可以看到 `app` 文件夹，展开后会看到 `build.gradle` 文件。
2. **编辑 `build.gradle` 文件**：

   - 找到 `android` 块中的 `defaultConfig` 部分。
   - 设置 `versionCode` 和 `versionName`，如下所示：

```groovy
android {
    // ... existing code ...
    defaultConfig {
        // ... existing code ...
        versionCode 1 // 版本代码，通常用于内部版本管理
        versionName "1.0" // 版本名称，用户可见
        // ... existing code ...
    }
    // ... existing code ...
}
```

- `versionCode` 是一个整数，每次发布新版本时都需要增加。
- `versionName` 是一个字符串，通常用于显示给用户。

### 示例

假设你要将应用的版本号设置为 2.0，版本代码为 2，你可以这样修改：

```groovy
android {
    // ... existing code ...
    defaultConfig {
        // ... existing code ...
        versionCode 2
        versionName "2.0"
        // ... existing code ...
    }
    // ... existing code ...
}
```

完成这些修改后，点击 Android Studio 顶部的“Sync Now”按钮以同步项目。

如果你仍然找不到 `build.gradle` 文件，可能需要检查项目结构，确保你查看的是正确的模块（通常是 `app` 模块）。如果有其他问题，请随时问我。

## build.gradle

在 Android 项目中，通常会有多个 `build.gradle` 文件，因为项目是基于 Gradle 构建系统的多模块项目结构。以下是对这两个文件的解释：

### 根目录下的 `build.gradle`

- **用途**：这个文件是项目的顶级构建文件，用于配置项目范围的构建设置。
- **内容**：通常包括全局的构建脚本配置，比如插件版本、全局依赖库版本、仓库配置等。
- **示例**：

```gradle
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:8.1.0'
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
```

### `app` 目录下的 `build.gradle`

- **用途**：这个文件是应用模块的构建文件，用于配置该模块的具体构建设置。
- **内容**：包括应用的版本信息、编译 SDK 版本、依赖库、构建类型等。
- **示例**：

```gradle
plugins {
    id 'com.android.application'
}

android {
    compileSdk 34

    defaultConfig {
        applicationId "person.notfresh.readingshare"
        minSdk 26
        targetSdk 34
        versionCode 1
        versionName "1.0"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}

dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    // 其他依赖
}
```

### 为什么有两个 `build.gradle`

- **模块化设计**：Android 项目通常是模块化的，根目录的 `build.gradle` 用于全局配置，而每个模块（如 `app`）有自己的 `build.gradle` 文件，用于模块特定的配置。
- **灵活性**：这种结构允许在项目中添加多个模块（如库模块、功能模块），每个模块可以有自己的构建配置。

这种分层的构建配置方式使得项目管理更加灵活和可扩展。

# App Scheme

Bilibili 的 URL Scheme 是一种用于在 Android 或 iOS 应用中直接打开 Bilibili 应用特定页面的方式。通过使用 URL Scheme，你可以从你的应用中直接跳转到 Bilibili 应用中的某个视频、用户主页或其他页面。

### 常见的 Bilibili URL Scheme

1. **视频页面**：通常是 `bilibili://video/{video_id}`，其中 `{video_id}` 是视频的唯一标识符。
2. **用户主页**：通常是 `bilibili://space/{user_id}`，其中 `{user_id}` 是用户的唯一标识符。
3. **直播间**：通常是 `bilibili://live/{room_id}`，其中 `{room_id}` 是直播间的唯一标识符。

### 如何使用 URL Scheme

在 Android 中，你可以使用 `Intent` 来启动 Bilibili 应用：

```java
Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("bilibili://video/123456"));
intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
if (intent.resolveActivity(getPackageManager()) != null) {
    startActivity(intent);
} else {
    // 如果 Bilibili 应用未安装，可以选择打开网页版本
    Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.bilibili.com/video/123456"));
    startActivity(webIntent);
}
```

### 注意事项

- **检查应用是否安装**：在使用 URL Scheme 之前，最好检查目标应用是否已安装，以避免崩溃。
- **权限**：确保你的应用有访问网络的权限。
- **兼容性**：不同版本的 Bilibili 应用可能支持不同的 URL Scheme，建议测试并确认。

通过使用 Bilibili 的 URL Scheme，你可以为用户提供更好的体验，直接在 Bilibili 应用中查看内容，而不是通过浏览器。

# 启动页

```
// main_activity那里，我花了很多时间去研究。

// 设置默认启动页
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        int defaultTab = prefs.getInt("default_tab", 0);
        Log.d("MainActivity", "Default tab: " + defaultTab);
        if (defaultTab == 0) {
            navController.navigate(R.id.nav_home);
        } else if (defaultTab == 1) {
            navController.navigate(R.id.nav_tags);
        } else if (defaultTab == 2) {
            navController.navigate(R.id.nav_slideshow);
        }

        checkClipboardPermission();

        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int id = item.getItemId();
                NavController navController = Navigation.findNavController(MainActivity.this, R.id.nav_host_fragment_content_main);

                if (id == R.id.nav_home) {
                    navController.navigate(R.id.nav_home);
                } else if (id == R.id.nav_tags) {
                    navController.navigate(R.id.nav_tags);
                } else if (id == R.id.nav_slideshow) {
                    navController.navigate(R.id.nav_slideshow);
                }
                // 可以添加更多的 else if 来处理其他菜单项

                DrawerLayout drawer = binding.drawerLayout;
                drawer.closeDrawer(GravityCompat.START);
                return true;
            }
        });
```

# adb

如果你在命令行中收到“无法将‘adb’项识别为 cmdlet、函数、脚本文件或可运行程序的名称”的错误信息，这通常意味着系统找不到 ADB 工具。以下是一些可能的解决方法：

### 解决方法

1. **确保 ADB 已安装**

   - ADB 是 Android SDK 的一部分。确保你已经安装了 Android SDK，并且 ADB 工具位于 SDK 的 `platform-tools` 目录中。
2. **检查环境变量**

   - 确保 ADB 的路径已添加到系统的环境变量中。以下是如何在不同操作系统上设置环境变量：

   **Windows**：

   - 打开“控制面板” > “系统和安全” > “系统”。
   - 点击“高级系统设置”。
   - 在“系统属性”窗口中，点击“环境变量”。
   - 在“系统变量”下，找到 `Path`，然后点击“编辑”。
   - 添加 ADB 的路径（例如 `C:\Users\YourUsername\AppData\Local\Android\Sdk\platform-tools`）到 `Path` 变量中。

   **macOS/Linux**：

   - 打开终端。
   - 编辑 `~/.bash_profile` 或 `~/.zshrc` 文件（取决于你使用的 shell），添加以下行：
     ```bash
     export PATH=$PATH:/Users/YourUsername/Library/Android/sdk/platform-tools
     ```
   - 保存文件并运行 `source ~/.bash_profile` 或 `source ~/.zshrc` 以应用更改。
3. **重启命令行或终端**

   - 在设置环境变量后，关闭并重新打开命令行或终端，以确保更改生效。
4. **直接使用完整路径**

   - 如果你不想修改环境变量，可以直接在命令行中使用 ADB 的完整路径。例如：
     ```bash
     /path/to/adb devices
     ```
5. **验证 ADB 是否可用**

   - 在命令行中输入 `adb version`，以验证 ADB 是否已正确安装和配置。

通过这些步骤，你应该能够解决 ADB 工具无法识别的问题。如果问题仍然存在，请确保 Android SDK 已正确安装，并且路径设置无误。

## adb检测应用是否安装

如果 `isAppInstalled` 方法返回 `false`，即使设备上安装了 Bilibili 应用，可能是由于以下原因：

### 可能的原因和解决方法

1. **包名错误**：

   - 确保包名 `tv.danmaku.bili` 是正确的。你可以通过以下方法确认包名：
     - 在设备上安装 Bilibili 应用后，使用命令行工具 `adb` 来列出所有应用的包名：
       ```bash
       adb shell pm list packages | grep bili
       ```
     - 确认输出中是否包含 `tv.danmaku.bili`。
     - 在windows的powershell下，grep可以使用 Select-String 代替。
     - 
2. **权限问题**：

   - 确保应用具有足够的权限来访问已安装的应用信息。通常情况下，`PackageManager` 不需要额外权限，但在某些设备上可能会有限制。
3. **设备兼容性**：

   - 在某些设备上，系统可能会限制对某些应用的访问。尝试在不同的设备上测试。
4. **调试日志**：

   - 添加日志以确认 `getPackageInfo` 方法的调用和异常捕获：
     ```java
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
     ```
5. **检查应用的安装位置**：

   - 确保 Bilibili 应用没有被安装到外部存储中，因为某些设备可能会在这种情况下无法正确识别应用。
6. **清理和重建项目**：

   - 在 Android Studio 中，选择 `Build > Clean Project`，然后选择 `Build > Rebuild Project`，以确保没有缓存问题。

通过这些步骤，你应该能够找出问题所在。如果问题仍然存在，请提供更多详细的日志信息，以便进一步帮助。

# 增加标签

```
public void addTagToLink(LinkItem item, String tagName){

    item.addTag(tagName);

    notifyDataSetChanged();

    linkDao.updateLinkTags(item);

    }
```

# 修改自己的包名

```gradle
plugins {
    id 'com.android.application'
}

android {
    namespace 'person.notfresh.readingshare'
    compileSdk 34

    defaultConfig {
        applicationId "person.notfresh.readingshare"

```

plugins {
id 'com.android.application'
}

不能变,但是 applicationId可以变

## 快速修改包名前缀

要进行全局修改以更新包名，请按照以下步骤操作：

### 1. 更新 `build.gradle`

确保 `namespace` 和 `applicationId` 是一致的：

```gradle
android {
    namespace 'person.notfresh.readingshare'
    defaultConfig {
        applicationId "person.notfresh.readingshare"
    }
}
```

### 2. 更新 Java 文件的包名

将所有 Java 文件的包名从 `person.notfresh.readingshare` 改为 `person.notfresh.readingshare`。

#### 示例修改

```java
// 修改前
package person.notfresh.readingshare.ui.gallery;

// 修改后
package person.notfresh.readingshare.ui.gallery;
```

### 3. 更新 `import` 语句

确保所有 `import` 语句使用新的包名。

#### 示例修改

```java
// 修改前
import person.notfresh.readingshare.R;

// 修改后
import person.notfresh.readingshare.R;
```

### 4. 重命名目录结构

在文件系统中，将 `java/person/notfresh/myapplication` 目录重命名为 `java/person/notfresh/readingshare`。

### 5. 清理并重建项目

- 在 Android Studio 中，点击 `Build` 菜单。
- 选择 `Clean Project`。
- 然后选择 `Rebuild Project`。

### 6. 检查资源文件

确保所有资源文件没有语法错误或缺失。

# 剪切板

在 Android 的剪贴板系统中，所谓的“主要的剪贴板数据”（primary clip），指的是当前剪贴板上最主要、最顶层的一条数据记录。系统的 ClipboardManager 可以在内部维护多个 ClipData 对象，但其中只有一个是“主要的”，也就是用户最近一次复制或剪切操作产生的内容。当我们调用 clipboard.hasPrimaryClip() 时，实际上就是在确认剪贴板里是否存在这样一条主要数据。

# 设计图标

想要设计一个自己心仪的图标，去网上找svg格式的图标，在res/drawable里面引入 new vector，然后在对应的位置引用即可。

# 循环列表

下面简单说明一下：

1. fragment_home.xml 是一个布局文件，通常会在对应的 Fragment（例如 “HomeFragment”） 中通过布局填充 (inflate) 的方式使用；
2. 通过把这个布局 inflate 到 Fragment 或 Activity 中，并在代码里找到 RecyclerView（如 recycler_view），然后为它设置一个 Adapter，即可把各种条目（例如“分享条目”）的数据渲染到界面里。

示例：在 HomeFragment 中，大致会这样写：

```java:app/src/main/java/com/example/app/HomeFragment.java
public class HomeFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // 将 fragment_home.xml 填充到当前的 Fragment 中
        View rootView = inflater.inflate(R.layout.fragment_home, container, false);

        // 获取 RecyclerView
        RecyclerView recyclerView = rootView.findViewById(R.id.recycler_view);

        // 设置布局管理器（例如垂直方向的 LinearLayoutManager）
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // 设置 Adapter，将“分享条目”的数据绑定到 RecyclerView
        MyShareAdapter adapter = new MyShareAdapter(getShareItemList()); // 需要自行实现 getShareItemList() 方法
        recyclerView.setAdapter(adapter);

        return rootView;
    }

    // 举个例子，表示从网络或本地获取数据后，返回一个列表
    private List<ShareItem> getShareItemList() {
        // TODO: 获取分享条目数据
        return new ArrayList<>();
    }
}
```

这样，fragment_home.xml 就会成为该 Fragment 对应的布局，而 RecyclerView 会使用 Adapter 去加载“分享条目”的数据，并且在界面上显示这些条目。你可以根据需要添加点击事件、滑动操作等，实现更多功能。

每个条目的样式通常会放在单独的布局文件中（比如 item_share.xml），然后在 RecyclerView 的 Adapter 中通过 inflate 的方式来创建和设置 ViewHolder，使得 RecyclerView 能够正确地显示每条数据对应的界面。

举个例子，你可以在 res/layout/ 目录下新增一个名为 item_share.xml 的文件，用于定义“分享条目”的外观与布局：

```xml:app/src/main/res/layout/item_share.xml
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:padding="16dp"
    android:orientation="vertical">

    <!-- 示例：文本标题 -->
    <TextView
        android:id="@+id/text_title"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textColor="@android:color/black"
        android:textSize="16sp"
        android:text="这是标题" />

    <!-- 示例：描述文字 -->
    <TextView
        android:id="@+id/text_description"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textSize="14sp"
        android:text="这里放置更多描述" />

</LinearLayout>
```

接着，在你的 Adapter 中（例如 MyShareAdapter.java），在 onCreateViewHolder(...) 方法里 inflate 上述布局，然后再在 onBindViewHolder(...) 中填充数据：

```java:app/src/main/java/com/example/app/MyShareAdapter.java
public class MyShareAdapter extends RecyclerView.Adapter<MyShareAdapter.ViewHolder> {

    private List<ShareItem> shareList;
    private Context context;

    public MyShareAdapter(List<ShareItem> shareList, Context context) {
        this.shareList = shareList;
        this.context = context;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // 在这里将 item_share.xml 填充 (inflate) 成一个 View
        View itemView = LayoutInflater.from(context)
                                      .inflate(R.layout.item_share, parent, false);
        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        // 获取数据并进行 UI 更新
        ShareItem item = shareList.get(position);
        holder.textTitle.setText(item.getTitle());
        holder.textDescription.setText(item.getDescription());
    }

    @Override
    public int getItemCount() {
        return shareList.size();
    }

    // ViewHolder 用于绑定 item_share.xml 中的控件
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textTitle;
        TextView textDescription;

        ViewHolder(View itemView) {
            super(itemView);
            textTitle = itemView.findViewById(R.id.text_title);
            textDescription = itemView.findViewById(R.id.text_description);
        }
    }
}
```

这样，每个条目的样式就在 item_share.xml 文件里定义好了，然后通过 Adapter 把对应的数据对象映射到布局中的各个控件上。根据你的项目架构，文件名或路径可能不尽相同，但它们一般都位于“layout”目录中并在 Adapter 的 onCreateViewHolder(...) 中进行填充。

# 获得xml文件定位

在Adapter.java中

```

    @NonNull
    @Override
    public RSSViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_rss_entry, parent, false);
        return new RSSViewHolder(view);
    }
```

# 嵌入式数据库版本管理

在使用自定义 LinkDbHelper（通常继承 SQLiteOpenHelper）来维护数据库时，常见的升级流程如下：

1. 在构造函数或静态字段中以 DATABASE_VERSION 来表示当前数据库版本号。每次更改数据库结构（如新增列、修改表等）就将版本号加 1。
2. 在 onUpgrade(...) 中编写详细的升级或迁移逻辑，而不是直接删库重建。
3. 如果需要兼容回退（downgrade），则可以在 onDowngrade(...) 或其他地方进行额外处理。

以下示例仅供参考（文件名、包名需根据项目实际情况调整）：

```java:app/src/main/java/person/notfresh/readingshare/db/LinkDbHelper.java
public class LinkDbHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "links.db";
    // 在数据库结构需要升级时，递增这个版本号
    public static final int DATABASE_VERSION = 2;

    public LinkDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // 创建表的示例
        db.execSQL("CREATE TABLE IF NOT EXISTS links (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "title TEXT," +
                "url TEXT," +
                "createTime LONG" +
                ");");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 如果只经历了从 1 升级到 2，可以直接针对这个版本差进行处理
        // 例如：给原有 links 表新增一个列
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE links ADD COLUMN extraInfo TEXT");
        }
        // 如果还会升级到更高的版本，可再根据 if (oldVersion < 3) ... 等类似逻辑来继续扩展
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 如果需要支持版本回退，亦可在这里实现对应逻辑
        // 可能做法：先备份重要数据，然后删除旧表，再重新创建等
        super.onDowngrade(db, oldVersion, newVersion);
    }
}
```

要点说明：
• DATABASE_VERSION 确定当前数据库版本。下次若因业务需求修改了数据库表结构，就把它加 1，并在 onUpgrade(...) 内编写相应的升级逻辑。
• onUpgrade(...) 中可以根据旧版本号（oldVersion）来判断要执行哪些操作，以实现多次迭代升级。
• onCreate(...) 在数据库第一次创建时调用，用于建表及初始化数据。
• 不要随意删除数据库文件，以免导致用户数据丢失。如确需清空部分数据，可精细化编写数据清理逻辑，以保障升级的安全性和连贯性。

## 版本号管理

在 Android SQLite 数据库中，`DATABASE_VERSION` 是一个非常重要的常量，用于管理数据库的版本控制。让我为您详细解释：

1. 作用：

   - 这个常量用来标识数据库的版本号
   - 当你需要修改数据库结构（比如添加新表、添加新列等）时，需要增加这个版本号
2. 工作机制：

   - 当应用首次安装时，Android 会调用 `onCreate()` 方法创建数据库
   - 当应用更新且 `DATABASE_VERSION` 增加时，Android 会自动调用 `onUpgrade()` 方法
   - 在这个文件中，当前数据库版本是 3，说明这个数据库已经经过了两次更新
3. 实际应用：

   ```java:app/src/main/java/person/notfresh/readingshare/db/LinkDbHelper.java
   // 从版本 1 升级到版本 2 可能添加了新的表
   // 从版本 2 升级到版本 3 可能又进行了其他修改
   private static final int DATABASE_VERSION = 3;
   ```
4. 注意事项：

   - 版本号只能增加，不能减少
   - 每次修改数据库结构时都应该增加版本号
   - 需要在 `onUpgrade()` 方法中正确处理版本升级逻辑，确保用户数据不会丢失

在您的代码中，当数据库版本从旧版本升级到版本 3 时，会执行 `onUpgrade()` 方法，重新创建所有的表结构。

## 数据库版本号

```
在 Android 中，SQLiteOpenHelper 会根据你在构造函数中传递给 super(...) 的数据库版本号来进行版本管理。系统会将该版本号写入数据库文件的内部元数据区（可以理解为在数据库文件头或类似位置有记录）。当你下次获取同名数据库并调用 getWritableDatabase() 或 getReadableDatabase() 时，SQLiteOpenHelper 会读取数据库文件内存储的原先版本号（即 oldVersion），然后与当前传入的 DATABASE_VERSION（即 newVersion）进行比较，如果不同，就会回调 onUpgrade(...) 或 onDowngrade(...) 方法。

简而言之，oldVersion 并不在你应用的代码中手动存储，而是由 Android 内部在数据库文件的元数据中进行维护并自动读取。你只需要在 LinkDbHelper 等继承 SQLiteOpenHelper 的类中设置好 DATABASE_VERSION，并在 onUpgrade(...) 内根据 oldVersion 做相应的升级逻辑即可。

```

# 华为商店

这条命令是在使用Java运行一个名为 `pepk.jar`的工具，并传递了一系列参数用于处理密钥和证书。具体来说：

- `java -jar pepk.jar`：使用Java运行环境执行 `pepk.jar`这个JAR（Java ARchive）文件。
- `--keystore sign.jks`：指定使用的密钥库文件为 `sign.jks`，这是一个存储了加密密钥和证书的文件。
- `--alias sign`：在密钥库中，用来标识特定密钥对的别名是 `sign`。
- `--output=sign.zip`：指定输出结果保存到 `sign.zip`文件中，这通常包含了加密后的数据或签名。
- `--encryptionkey=0342...C3C02`：提供了一个加密密钥，用于加密过程中。这里展示的是一个很长的十六进制字符串，实际应用中应保持秘密。
- `--include-cert`：指示工具在生成的输出文件中包含证书信息。

`pepk.jar`工具可能是Google Play使用的“Play Encrypt Private Key”工具，用于将应用程序的私钥安全地上传至Google Play开发者控制台。该过程涉及加密您的私钥并打包相关的证书，以便于安全传输和验证应用程序的身份。

# >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

# jar

java -jar pepk.jar --keystore sign.jks --alias key0 --output=sign.zip --encryptionkey=034200041E224EE22B45D19B23DB91BA9F52DE0A06513E03A5821409B34976FDEED6E0A47DBA48CC249DD93734A6C5D9A0F43461F9E140F278A5D2860846C2CF5D2C3C02 --include-cert

# >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

# RSS源

RSS源列表：

Decohack
https://www.decohack.com/feed

Decohack
https://www.decohack.com/feed

让小产品的独立变现更简单 - ezindie.com
https://www.ezindie.com/feed/rss.xml

Apptisan
https://quail.ink/apptisan/feed/atom

后互联网时代的乱弹
https://proxy.wavpub.com/pie.xml

DEX 周刊
https://quail.ink/dingyi/feed/atom

Hacker News: Newest
https://hnrss.org/newest

目前没有RSS源
https://www.cnblogs.com/tuyile006/p/3691024.html



# Intent

# >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
# Icon设置

举个例子  profileImage.setImageResource(R.drawable.default_avatar);

# 信任所有证书

这是因为服务器使用的是自签名证书或证书不被 Android 系统信任。我们有两种解决方案：
使用 HTTP 而不是 HTTPS（不推荐，仅用于测试）
添加网络安全配置来信任特定域名的证书（推荐）

这些修改会：
在网络安全配置中添加对 duxiang.ai 域名的支持
添加调试时信任用户证书的配置
在代码中添加信任所有证书的配置
请注意，在生产环境中应该使用正式的 SSL 证书，而不是信任所有证书，这种方式仅用于开发测试。

# >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
删除垃圾代码，否则会影响AI的使用


# @END
