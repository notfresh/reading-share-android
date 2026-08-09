# Android 端轻量级双向同步功能设计

## 概述

在 Android 端实现轻量级双向同步客户端，与网页插件端和服务端配合工作。

## 功能需求

- 用户在设置页面配置同步服务器地址和密钥
- 点击同步按钮，触发双向同步
- 上传本地所有链接的 title + url + hash 到服务器
- 服务器返回客户端缺少的链接
- 本地用 hash(url+title) 检查唯一性，创建不存在的链接

---

## 加密机制

### 密钥派生
```java
// 密钥派生: SHA256(密码)
Key = SHA256(password)
```

### 请求结构
```
请求头: Authorization: Bearer <加密的"DUXIANG">
        ↑ 用密钥加密 "DUXIANG"，服务器解密成功 = 密钥正确

请求体: { "encrypted": { "iv": "xxx", "data": "xxx" } }
        ↑ 用密钥加密 JSON 数据
```

### 局域网判断
```java
boolean shouldEncrypt(String url) {
    String host = new URL(url).getHost();
    return !(host.equals("localhost") ||
             host.equals("127.0.0.1") ||
             host.startsWith("192.168."));
}
```

- `localhost` / `127.0.0.1` / `192.168.x.x` → 明文传输
- 其他 → 加密传输

### 响应处理
- 未加密请求 → 返回 `{"links": [...]}`
- 加密请求 → 返回 `{"encrypted": {"iv": "xxx", "data": "xxx"}}`

---

## 数据结构

### 链接对象
```json
{
  "title": "链接标题",
  "url": "https://example.com",
  "hash": "sha256(title::url)"
}
```

### Hash 计算规则
```
hash = SHA256(title + "::" + url)
```
使用 `::` 分隔符避免 title=a + url=bc 和 title=ab + url=c 产生相同 hash。

---

## 架构设计

```
┌─────────────────────────────────────────────────────────┐
│                      UI 层                              │
│  SettingFragment (同步设置 + 同步按钮 + 状态显示)        │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│                   业务逻辑层                            │
│  SimpleSyncManager                                      │
│  - sync() 执行完整同步流程                              │
│  - computeHash() 计算 SHA256 hash                      │
│  - createLinkFromSync() 从服务器创建链接                │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│                   网络加密层                            │
│  SyncApiClient                                          │
│  - shouldEncrypt() 判断是否加密                        │
│  - encrypt()/decrypt() AES-256-CBC 加密解密            │
│  - sync() 发起同步请求                                  │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│                   数据持久化层                          │
│  LinkDao (现有)                                         │
└─────────────────────────────────────────────────────────┘
```

---

## 同步流程

```
1. SimpleSyncManager.sync()
   │
   ├─→ 2. LinkDao.getAllLinks() 获取本地所有链接
   │
   ├─→ 3. 计算每个链接的 hash = SHA256(title + "::" + url)
   │
   ├─→ 4. SyncApiClient.sync(localLinks)
   │       │
   │       ├─→ shouldEncrypt() 判断是否加密
   │       │
   │       ├─→ 加密请求头: Authorization = "Bearer " + AES(key, "DUXIANG")
   │       │
   │       └─→ 加密请求体: { "encrypted": { "iv": "xxx", "data": AES(key, linksJSON) } }
   │
   ├─→ 5. 解密响应，获取服务器返回的链接列表
   │
   ├─→ 6. 遍历服务器返回的链接:
   │       hash = SHA256(title + "::" + url)
   │       if (本地不存在此hash) {
   │           LinkDao.insertLink(new LinkItem(...))
   │       }
   │
   └─→ 7. 返回 SyncResult(上传数, 下载数, 状态)
```

---

## 配置存储

使用 SharedPreferences 存储：
- `sync_server_url` - 服务器地址
- `sync_secret_key` - 同步密钥

---

## 实现步骤

1. 创建 `SimpleSyncManager` - 同步核心逻辑
2. 创建 `SyncApiClient` - 网络请求 + 加密解密
3. 修改 `SettingFragment` - 添加同步配置 UI 和同步按钮
4. 测试同步功能

---

## API 接口

### 同步接口

**请求** `/api/sync` POST
```json
{
  "links": [
    { "title": "标题", "url": "https://...", "hash": "sha256值" }
  ]
}
```

**响应**
```json
{
  "links": [
    { "title": "标题", "url": "https://...", "hash": "sha256值" }
  ]
}
```

### 状态接口

**请求** `/status` GET

**响应**
```json
{
  "links_count": 2500,
  "links": [...]
}
```
