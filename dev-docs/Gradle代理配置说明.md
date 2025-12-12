# Gradle 代理配置说明

## 方案一：配置代理（如果有代理服务器）

### 1. 在 `gradle.properties` 文件中添加代理配置

```properties
# HTTP/HTTPS 代理配置
systemProp.http.proxyHost=127.0.0.1
systemProp.http.proxyPort=7890
systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=7890

# 如果需要认证
# systemProp.http.proxyUser=username
# systemProp.http.proxyPassword=password
# systemProp.https.proxyUser=username
# systemProp.https.proxyPassword=password

# 不需要代理的地址（可选）
systemProp.http.nonProxyHosts=localhost|127.0.0.1|*.local
```

**注意：** 请根据你的实际代理配置修改 `proxyHost` 和 `proxyPort`。

### 2. 在 Android Studio 中配置代理

1. File → Settings → Appearance & Behavior → System Settings → HTTP Proxy
2. 选择 "Manual proxy configuration"
3. 填写代理地址和端口
4. 点击 "OK" 并重启 Android Studio

---

## 方案二：使用国内镜像源（推荐，无需代理）

### 1. 修改 `settings.gradle.kts`，添加国内镜像

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 使用阿里云镜像
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        
        // JitPack 镜像（如果可用）
        maven { url = uri("https://jitpack.io") }
        
        // 备用源
        google()
        mavenCentral()
    }
}
```

### 2. 或者使用腾讯云镜像

```kotlin
maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
```

---

## 方案三：使用本地 Maven 仓库（离线方案）

如果网络完全无法访问，可以：
1. 在有网络的环境下载依赖
2. 将依赖复制到本地 Maven 仓库
3. 配置使用本地仓库

---

## 推荐方案

**优先使用方案二（国内镜像源）**，因为：
- 不需要代理服务器
- 速度更快
- 配置简单

如果镜像源也无法访问，再考虑配置代理。

