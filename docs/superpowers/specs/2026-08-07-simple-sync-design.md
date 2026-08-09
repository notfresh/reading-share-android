# 轻量级双向同步功能设计

## 概述

实现一个简单轻量的双向同步功能：点击同步按钮时，上传本地所有链接到配置的服务器，服务器返回它没有的链接，本地检查并创建。

## 功能需求

- 用户点击同步按钮，触发双向同步
- 上传本地所有链接的 title + url 到服务器
- 服务器返回它没有的链接
- 本地用 hash(url+title) 检查唯一性，本地没有的链接创建到本地

---

## 同步协议

### 核心思路

**原则：服务器返回客户端缺少的内容，而不是服务器缺少的内容**

- 客户端上传自己本地的所有链接（含 hash）
- 服务器保存客户端发来的新链接
- 服务器返回自己拥有但该客户端没有的链接
- 客户端将服务器返回的链接创建到本地

### 数据结构

#### 链接对象
```json
{
  "title": "链接标题",
  "url": "https://example.com",
  "hash": "sha256(title::url)"
}
```

#### Hash 计算规则
```
hash = SHA256(title + "::" + url)
```
使用 `::` 分隔符避免 title=a + url=bc 和 title=ab + url=c 产生相同 hash。

### API 接口

#### 同步接口

**请求**
- URL: `/api/sync`
- Method: POST
- Content-Type: `application/json`

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

#### 状态接口

- URL: `/status`
- Method: GET

**响应**
```json
{
  "links_count": 2500,
  "links": [...]
}
```

### 同步示例

| 场景 | 服务器 | 客户端 | 同步后 |
|------|--------|--------|--------|
| 空服务器 + 有数据客户端 | 0条 | 100条 | 服务器保存100条，客户端不变 |
| 有数据服务器 + 空客户端 | 100条 | 0条 | 服务器返回100条，客户端创建100条 |
| 双方有部分重叠 | 100条 | 80条(50条相同) | 服务器保存30条新链接，客户端创建50条 |

---

## 加密验证机制

### 背景

在公网 HTTP 环境下传输数据需要加密，防止未授权访问。

### 加密算法

- **算法**: AES-256-CBC
- **密钥派生**: SHA-256(密码)
- **填充**: PKCS7
- **编码**: Base64

### 数据格式

```json
{
  "iv": "base64编码的IV",
  "data": "base64编码的加密内容"
}
```

### 密钥验证流程

```
┌─────────────────────────────────────────────────────┐
│ 请求头: Authorization: Bearer <加密的"DUXIANG">    │
│ 请求体: { "iv": "xxx", "data": "加密的内容" }     │
└─────────────────────────────────────────────────────┘
```

**步骤：**

1. 双方约定密钥：`SYNC_SECRET`
2. **客户端**：
   - 请求头 = encrypt("DUXIANG", 密钥)  // 加密后的密文
   - 请求体 = encrypt(JSON数据, 密钥)
3. **服务器**：
   - 取出请求头，用密钥解密 → 得到 "DUXIANG"
   - 验证通过 → 用密钥解密请求体
   - 处理业务 → 用密钥加密响应体返回

**验证原理：**
- 解密请求头成功 = 密钥正确 = 请求合法
- 同时完成身份验证 + 请求体解密

### 本地/局域网跳过加密

如果同步服务器地址为以下情况，可以选择不加密（明文传输）：

- `localhost`
- `127.0.0.1`
- `192.168.x.x`（局域网）



判断逻辑：
```javascript
function shouldEncrypt(url) {
  const host = new URL(url).hostname;
  return !(host === 'localhost' || 
           host === '127.0.0.1' || 
           host.startsWith('192.168.'));
}
```

配置了密钥，检测到请求有认证头部则表明进行了加密。

**服务端响应**：
- 未加密请求：返回明文 `{"links": [...]}`
- 加密请求：返回 `{"encrypted": {"iv": "xxx", "data": "xxx"}}`

### 加密后的请求体

```json
{
  "encrypted": {
    "iv": "xxx",
    "data": "xxx"
  }
}
```

### 加密后的响应体

与请求体结构保持一致：

```json
{
  "encrypted": {
    "iv": "xxx",
    "data": "xxx"  // 解密后是 JSON: { "links": [...] }
  }
}
```

---

## 网页插件端的技术设计

### 存储

链接存储到现有的 `links` 表（IndexedDB），创建时间设置为当前时间。

### 消息通道

- popup.js 发送消息到 background.js
- background.js 处理同步逻辑

### 文件变更

1. **js/simple-sync.js**（新增）
   - `syncAll()` 函数：获取本地所有链接，发起同步请求，处理响应
   - 加密/解密函数

2. **background.js**（修改）
   - 添加 `simple-sync` 消息处理，调用 simple-sync.js

3. **popup.js**（修改）
   - 同步按钮改用新的 simple-sync

4. **popup.html**（修改）
   - 添加同步密钥输入框

### 密钥派生

#### Python
```python
import hashlib
key = hashlib.sha256(password.encode('utf-8')).digest()
```

#### JavaScript
```javascript
const keyData = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(password));
const key = await crypto.subtle.importKey("raw", keyData, { name: "AES-CBC", length: 256 }, false, ["encrypt", "decrypt"]);
```

---

## 实现步骤

1. 创建 js/simple-sync.js，实现核心同步逻辑 + 加密解密
2. 在 background.js 添加消息处理
3. 修改 popup.html 添加密钥输入框
4. 修改 popup.js 的同步按钮逻辑
5. 测试同步功能

---

## 配置方式

### 服务端
```bash
export SYNC_SECRET=your_secret_key
python server.py
```

### 客户端
- 在插件设置页面输入密钥并保存
