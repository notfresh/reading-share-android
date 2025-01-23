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


# @END
