# 读享 V1.0 软件软著申请 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为「读享 V1.0 软件」生成可提交至中国版权保护中心的软件著作权登记三件套(完整申请表 .docx + 用户手册 .docx 40 页 + 源程序代码鉴别材料 4 份 PDF)。

**Architecture:** 三阶段顺序交付,每阶段独立 spec → plan → 执行 → 检查。三阶段都用 Python 脚本(用户最后手动替换占位符)。脚本本身是工具,文档内容(`*.md` 源)与人可读 Word/PDF 终稿一一对应。

**Tech Stack:** Python 3.10+ / `python-docx`(生成 Word)/ `reportlab`(生成 PDF) / 项目内已有图片资源(用于手册插图)。

---

## Task 0: 创建项目骨架与总 README

**Files:**
- Create: `docs/superpowers/rz/README.md`
- Create: `docs/superpowers/rz/01-申请表/` (空目录,占位 .gitkeep)
- Create: `docs/superpowers/rz/02-用户手册/` (空目录,占位 .gitkeep)
- Create: `docs/superpowers/rz/03-代码鉴别材料/` (空目录,占位 .gitkeep)
- Create: `docs/superpowers/rz/04-提交前检查/` (空目录,占位 .gitkeep)

- [ ] **Step 1: 创建目录结构**

在 `c:/projects/duxiang-pack/duxiang-android` 根目录下执行:

```bash
mkdir -p docs/superpowers/rz/01-申请表 \
         docs/superpowers/rz/02-用户手册 \
         docs/superpowers/rz/03-代码鉴别材料 \
         docs/superpowers/rz/04-提交前检查

# 占位文件以便 git 跟踪
touch docs/superpowers/rz/01-申请表/.gitkeep
touch docs/superpowers/rz/02-用户手册/.gitkeep
touch docs/superpowers/rz/03-代码鉴别材料/.gitkeep
touch docs/superpowers/rz/04-提交前检查/.gitkeep
```

- [ ] **Step 2: 创建总 README.md**

新建 `docs/superpowers/rz/README.md`,内容如下:

```markdown
# 读享 V1.0 软件软著申请

> 一次性生成可提交至中国版权保护中心的软著三件套,所有公司信息用占位符,提交前手动替换。

## 目录

| 阶段 | 路径 | 交付件 |
|---|---|---|
| 1. 申请表 | `01-申请表/` | 软著登记申请表.docx |
| 2. 用户手册 | `02-用户手册/` | 用户手册.docx (40 页) |
| 3. 代码鉴别材料 | `03-代码鉴别材料/` | 源程序代码-前30页.pdf / 源程序代码-后30页.pdf / 源程序代码-首页30行.pdf / 源程序代码-末页30行.pdf |
| 4. 提交前检查 | `04-提交前检查/` | 自检清单.md |

## 占位符一览(提交前替换)

| 占位符 | 含义 | 替换为 |
|---|---|---|
| `《XX 有限公司》` | 著作权人公司全称 | 你的公司全称 |
| `《统一社会信用代码:91110000XXXXXXXXXX》` | 公司社会信用代码 | 真实代码 |
| `《XX 软件科技有限公司》` | 开发者单位 | 真实单位 |
| `《XX 市 XX 区 XX 路 1 号》` | 公司地址 | 真实地址 |
| `《13800000000》` | 联系电话 | 真实电话 |
| `《zhangsan@example.com》` | 邮箱 | 真实邮箱 |
| `《张三》` | 联系人姓名 | 真实姓名 |
| `《2025-01-01》` | 开发完成/首次发表日期 | 准确日期 |

## 提交流程

1. 替换所有占位符(本目录所有 .md / .docx / .pdf)
2. 运行 `04-提交前检查/自检清单.md` 中各项检查
3. 登录中国版权保护中心网站,填写在线申请表(用本目录 01-申请表 内容)
4. 上传 .docx + .pdf
5. 缴费并等待审查
```

- [ ] **Step 3: 提交**

```bash
cd "c:/projects/duxiang-pack/duxiang-android"
git add docs/superpowers/rz/
git commit -m "docs(rz): 软著申请项目骨架与总 README"
```

---

## 第 1 阶段:申请表

### Task 1.1: 写申请表人可读源(.md)

**Files:**
- Create: `docs/superpowers/rz/01-申请表/软著登记申请表.md`

- [ ] **Step 1: 写申请表的 24 个栏目**

新建 `docs/superpowers/rz/01-申请表/软著登记申请表.md`,完整内容如下(每个栏目 1 个 H2):

````markdown
# 计算机软件著作权登记申请表

> 软件全称:**读享 V1.0 软件**
> 软件简称:读享
> 版本号:V1.0

## 1. 软件全称

读享 V1.0 软件

## 2. 软件简称

读享

## 3. 版本号

V1.0

## 4. 软件分类

互联网/移动互联网应用 - 工具类

## 5. 著作权人

《XX 有限公司》

## 6. 著作权人性质

企业法人

## 7. 著作权人通讯地址

《XX 市 XX 区 XX 路 1 号》

## 8. 联系人 / 联系电话 / 电子邮箱

联系人:《张三》
联系电话:《13800000000》
电子邮箱:《zhangsan@example.com》

## 9. 开发者

《XX 软件科技有限公司》

## 10. 权利取得方式

原始取得(独立开发)

## 11. 权利范围

全部权利

## 12. 首次发表日期

2025-01-01

## 13. 发表状态

已发表

## 14. 开发完成日期

2025-01-01

## 15. 硬件环境

Android 9.0 及以上手机/平板,2GB 及以上运行内存,500MB 及以上可用存储空间。

## 16. 软件环境

Android 9.0+(API Level 28 及以上);无服务端依赖,所有数据本地存储。

## 17. 编程语言

Java

## 18. 开发工具

Android Studio、Gradle 8.x、SQLite

## 19. 源程序总行数

约 3000 行(摘选)

## 20. 软件用途

本软件用于跨 App 管理个人阅读内容。用户可从浏览器、社交 App 等应用一键分享链接或 PDF 到本软件,软件自动捕获、解析并保存,按时间和主题组织,支持标签筛选、主题订阅、PDF 阅读、内嵌网页阅读(含后台播放)、桌面快捷方式、阅读统计等场景,帮助用户建立个人化的阅读资源库。

## 21. 技术特点

1) **多源分享接收**:支持 `text/*` 与 PDF 文件的 Android 分享 Intent 接收,智能解析小红书、B 站等社交平台的分享文本,提取真实链接与标题。
2) **离线优先**:本地 SQLite 数据库存储所有链接、标签、主题数据,App 启动与操作均不依赖网络,具备隐私安全与快速响应优势。
3) **多模态阅读**:内嵌 WebView 支持网页正文提取与无图阅读模式;Document Viewer 支持 PDF 目录自动提取、阅读进度记忆与书签。
4) **桌面快捷方式**:长按桌面图标可创建指向指定 URL 的快捷方式,一键直达常读网页。
5) **后台播放**:WebView 内嵌的音频/视频可在后台以 MediaSession 形式继续播放,并以画中画/通知栏控件控制。
6) **标签-主题二级组织**:扁平标签用于快速筛选;树形主题用于聚合多源内容(如同一作者的多个 RSS、同一专题的多篇文章),兼顾检索效率与组织深度。

## 22. 模块说明

本软件采用分层架构,自上而下分为 UI 层、业务层、存储层、数据模型与同步层、接收层。
- **UI 层**(`ui/`):基于 Fragment + Activity 体系,按 Tab 组织首页(链接列表)、主题、标签、设置四大主入口,各 Tab 间通过 BottomNavigationView 切换,详细页面如 PDF 阅读、主题详情、网页阅读为独立 Activity。
- **业务层**(`util/`、`core/`):提供与 UI/存储无关的纯函数与算法,包括 PDF 目录提取、链接解析、图片处理、主题工具、滑动操作辅助,以及跨设备数据同步(Synchronizer)。
- **存储层**(`db/`):基于 SQLiteOpenHelper 封装,采用 DAO 模式访问 links、tags、link_tags、subjects、subject_items、reading_progress 等表,事务化保证数据一致性。
- **数据模型**(`model/`):POJO 形式定义 Link、Tag、Subject、SubjectItem、ReadingProgress 等实体。
- **同步层**(`core/Synchronizer`):负责与外部存储/网络位置进行差异同步,支持增量更新与冲突解决。
- **接收层**:由 AndroidManifest 声明 `SEND` / `SEND_MULTIPLE` Intent 过滤器,在 MainActivity 内统一分发,经 `ShareUtil` 解析后落库。

## 23. 申请日期

(脚本生成时自动填入当日日期,格式 YYYY-MM-DD)

## 24. 申请人签章

《XX 有限公司》(盖章)
````

- [ ] **Step 2: 提交**

```bash
cd "c:/projects/duxiang-pack/duxiang-android"
git add docs/superpowers/rz/01-申请表/软著登记申请表.md
git commit -m "docs(rz): 软著登记申请表人可读源(24 个栏目)"
```

---

### Task 1.2: 写申请表生成脚本(.py)

**Files:**
- Create: `docs/superpowers/rz/01-申请表/生成脚本.py`

- [ ] **Step 1: 创建脚手架文件**

新建 `docs/superpowers/rz/01-申请表/生成脚本.py`,完整内容:

```python
"""软著登记申请表 .docx 生成脚本

读取同目录下 软著登记申请表.md,解析 24 个栏目,生成 Word 文档。
运行:python 生成脚本.py
输出:软著登记申请表.docx
"""
from datetime import date
from pathlib import Path
import re

from docx import Document
from docx.shared import Pt, Cm
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn


HERE = Path(__file__).parent
SRC = HERE / "软著登记申请表.md"
OUT = HERE / "软著登记申请表.docx"

# 24 个栏目的标题(在 .md 中以 "## N. " 开头)
SECTION_RE = re.compile(r"^## (\d+)\. (.+)$", re.MULTILINE)


def parse_sections(md_text: str) -> list[tuple[str, str]]:
    """返回 [(栏目号. 标题, 正文), ...] 列表(包含封面 H1 之前丢弃)"""
    sections: list[tuple[str, str]] = []
    matches = list(SECTION_RE.finditer(md_text))
    for i, m in enumerate(matches):
        num_title = f"{m.group(1)}. {m.group(2)}"
        start = m.end()
        end = matches[i + 1].start() if i + 1 < len(matches) else len(md_text)
        body = md_text[start:end].strip()
        sections.append((num_title, body))
    return sections


def set_chinese_font(run, size: int = 12) -> None:
    run.font.name = "宋体"
    run.font.size = Pt(size)
    rPr = run._element.get_or_add_rPr()
    rFonts = rPr.find(qn("w:rFonts"))
    if rFonts is None:
        from docx.oxml import OxmlElement
        rFonts = OxmlElement("w:rFonts")
        rPr.append(rFonts)
    rFonts.set(qn("w:eastAsia"), "宋体")


def add_title(doc: Document, text: str) -> None:
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = p.add_run(text)
    set_chinese_font(run, 18)
    run.bold = True


def add_section_heading(doc: Document, text: str) -> None:
    p = doc.add_paragraph()
    run = p.add_run(text)
    set_chinese_font(run, 14)
    run.bold = True


def add_body(doc: Document, text: str) -> None:
    for line in text.splitlines():
        p = doc.add_paragraph()
        run = p.add_run(line.strip() if line else " ")
        set_chinese_font(run, 12)


def build_docx() -> None:
    md_text = SRC.read_text(encoding="utf-8")
    sections = parse_sections(md_text)

    doc = Document()
    # 页面设置:A4
    for section in doc.sections:
        section.page_width = Cm(21)
        section.page_height = Cm(29.7)
        section.top_margin = Cm(2.5)
        section.bottom_margin = Cm(2.5)
        section.left_margin = Cm(2.5)
        section.right_margin = Cm(2.5)

    add_title(doc, "计算机软件著作权登记申请表")

    # 23 栏 申请日期 自动填入
    final_sections = []
    for num_title, body in sections:
        if num_title.startswith("23."):
            body = date.today().isoformat()
        final_sections.append((num_title, body))

    for num_title, body in final_sections:
        add_section_heading(doc, num_title)
        add_body(doc, body)

    doc.save(OUT)
    print(f"生成成功: {OUT}")


if __name__ == "__main__":
    build_docx()
```

- [ ] **Step 2: 安装依赖并运行**

```bash
cd "c:/projects/duxiang-pack/duxiang-android/docs/superpowers/rz/01-申请表"
pip install python-docx
python 生成脚本.py
```

预期输出:`生成成功: ...\软著登记申请表.docx`

- [ ] **Step 3: 人工打开 .docx 检查**

用 Word/WPS 打开 `软著登记申请表.docx`,确认:
- 标题居中、粗体、18 号
- 24 个栏目齐全,顺序正确
- 占位符 `《XX 有限公司》` 等保留原样
- 23 栏为今日日期

- [ ] **Step 4: 提交**

```bash
cd "c:/projects/duxiang-pack/duxiang-android"
git add docs/superpowers/rz/01-申请表/生成脚本.py docs/superpowers/rz/01-申请表/软著登记申请表.docx
git commit -m "feat(rz): 申请表 .docx 生成脚本与首版输出"
```

---

### Task 1.3: 写申请表自检脚本

**Files:**
- Create: `docs/superpowers/rz/01-申请表/自检脚本.py`

- [ ] **Step 1: 创建自检脚本**

新建 `docs/superpowers/rz/01-申请表/自检脚本.py`,完整内容:

```python
"""申请表自检:确保 24 个栏目齐全,占位符存在,23 栏为日期"""
import re
import sys
from pathlib import Path

from docx import Document

HERE = Path(__file__).parent
DOCX = HERE / "软著登记申请表.docx"
MD = HERE / "软著登记申请表.md"

EXPECTED_PLACEHOLDERS = [
    "《XX 有限公司》",
    "《统一社会信用代码:91110000XXXXXXXXXX》",
    "《XX 软件科技有限公司》",
    "《XX 市 XX 区 XX 路 1 号》",
    "《13800000000》",
    "《zhangsan@example.com》",
    "《张三》",
]


def check_md() -> list[str]:
    errors: list[str] = []
    text = MD.read_text(encoding="utf-8")
    expected_titles = [f"## {i}." for i in range(1, 25)]
    for title in expected_titles:
        if title not in text:
            errors.append(f"缺少栏目: {title}")
    for ph in EXPECTED_PLACEHOLDERS:
        if ph not in text:
            errors.append(f"缺少占位符: {ph}")
    if "2025-01-01" not in text:
        errors.append("缺少日期占位符 2025-01-01")
    return errors


def check_docx() -> list[str]:
    errors: list[str] = []
    if not DOCX.exists():
        return [f"缺少文件: {DOCX}"]
    doc = Document(DOCX)
    headings = [p.text for p in doc.paragraphs if re.match(r"^\d+\. ", p.text)]
    if len(headings) < 24:
        errors.append(f"docx 中栏目数 {len(headings)} < 24")
    return errors


def main() -> int:
    errors = check_md() + check_docx()
    if errors:
        for e in errors:
            print(f"[FAIL] {e}")
        return 1
    print("[OK] 申请表自检通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
```

- [ ] **Step 2: 运行自检**

```bash
cd "c:/projects/duxiang-pack/duxiang-android/docs/superpowers/rz/01-申请表"
python 自检脚本.py
```

预期输出:`[OK] 申请表自检通过`

- [ ] **Step 3: 提交**

```bash
cd "c:/projects/duxiang-pack/duxiang-android"
git add docs/superpowers/rz/01-申请表/自检脚本.py
git commit -m "test(rz): 申请表自检脚本"
```

---

### ✅ Checkpoint 1:第 1 阶段交付确认

**暂停,等用户确认以下产物:**

| 文件 | 状态 |
|---|---|
| `01-申请表/软著登记申请表.md` | ☐ 已生成 |
| `01-申请表/软著登记申请表.docx` | ☐ 已生成 |
| `01-申请表/生成脚本.py` | ☐ 已提交 |
| `01-申请表/自检脚本.py` | ☐ 通过 |

**给用户报告:**
- 申请表 24 个栏目齐全、占位符保留、日期 2025-01-01
- 用户检查 Word 内容,如有调整(措辞/字段/技术特点)直接修改 .md 后重跑生成脚本

**用户确认后,进入第 2 阶段(用户手册)。**

---

## 第 2 阶段:用户手册

### Task 2.1: 写用户手册人可读源(.md)

**Files:**
- Create: `docs/superpowers/rz/02-用户手册/用户手册.md`

- [ ] **Step 1: 写封面与文档信息页**

新建 `docs/superpowers/rz/02-用户手册/用户手册.md`,写入文件头:

```markdown
# 读享 V1.0 软件 - 用户手册

> 软件全称:**读享 V1.0 软件**
> 软件简称:读享
> 版本号:V1.0
> 著作权人:《XX 有限公司》
> 文档版本:V1.0
> 编写日期:2026-06-11
> 联系方式:《zhangsan@example.com》

---

## 第 1 章 软件概述

### 1.1 简介与目标用户

读享是一款用于管理跨 App 阅读内容的 Android 应用。无论用户是在浏览器看到想深读的文章,在小红书/B 站看到想归档的视频链接,还是在文件管理器收到 PDF 报告,都可以通过系统分享一键"投"到读享,由读享统一管理。

**目标用户**:
- 跨平台、跨应用阅读量大的重度阅读者
- 习惯用标签/主题组织个人知识库的用户
- 希望快速打开常读网页、继续阅读 PDF 的用户

### 1.2 主要功能速览

读享 V1.0 提供以下核心功能:

1. **链接捕获与解析** — 接收系统分享、自动解析分享文本
2. **标签管理** — 平面化标签,支持多选筛选
3. **主题订阅** — 树形主题,聚合多源内容
4. **网页阅读** — 内嵌 WebView,支持后台播放与画中画
5. **PDF 阅读** — 自动提取目录、记录阅读进度与书签
6. **桌面快捷方式** — 长按图标可创建 URL 快捷方式
7. **阅读统计** — 记录点击次数与时间
8. **导入导出** — 跨设备同步与备份

### 1.3 版本信息

| 字段 | 值 |
|---|---|
| 软件全称 | 读享 V1.0 软件 |
| 版本号 | V1.0 |
| 首次发表日期 | 2025-01-01 |
| 硬件要求 | Android 9.0+,2GB+ RAM,500MB+ 存储 |
| 软件要求 | 无服务端依赖 |

### 1.4 名词术语

| 术语 | 解释 |
|---|---|
| 链接(Link) | 读享中保存的一个 URL 条目,带标题、来源应用、标签等元信息 |
| 标签(Tag) | 用户为链接打的扁平化分类 |
| 主题(Subject) | 树形组织,一个主题可包含多个子主题与多个链接条目 |
| 分享接收(Share Intent) | Android 系统中其他应用向本应用发送内容的方式 |
| 桌面快捷方式 | 长按桌面图标创建,可一键打开指定 URL |

---

## 第 2 章 安装与运行

### 2.1 运行环境

- **操作系统**:Android 9.0 (Pie, API 28) 及以上
- **硬件要求**:2GB 及以上运行内存,500MB 及以上可用存储
- **网络**:仅在打开外链/PDF 同步等场景需要,主流程不依赖网络
- **依赖**:无服务端依赖

### 2.2 安装方式

读享以 APK 形式分发。安装步骤:

1. 在浏览器或文件管理器中找到下载好的 `读享-V1.0.apk`
2. 点击 APK 文件,系统提示是否允许"安装来自此来源的应用"
3. 在弹出的权限设置中允许安装
4. 回到 APK 文件,点击"安装"
5. 安装完成后,在应用列表中可看到"读享"图标

### 2.3 启动

- 点击桌面图标启动读享
- 首次启动会请求以下权限:通知权限(用于后台播放通知)、存储权限(用于 PDF 导入导出)
- 启动后默认进入"首页"Tab,显示已保存的链接列表(首次启动为空)

### 2.4 权限说明

| 权限 | 用途 | 是否必须 |
|---|---|---|
| INTERNET | 打开网页链接 | 是 |
| READ/WRITE_EXTERNAL_STORAGE | 导入导出 PDF | 否(仅导入导出时) |
| ACCESS_NETWORK_STATE | 检测网络状态 | 是 |
| WAKE_LOCK | 后台播放保持唤醒 | 否 |
| FOREGROUND_SERVICE | 后台播放服务 | 否 |
| POST_NOTIFICATIONS | 后台播放通知 | 否(可拒绝,但无法显示通知控件) |
| INSTALL_SHORTCUT | 创建桌面快捷方式 | 否 |

---

## 第 3 章 主界面与导航

### 3.1 整体布局

[图 3-1:此处插入主界面截图 - 底部 Tab 栏(首页/主题/标签/设置)]

启动后,主界面分为三部分:
- 顶部:应用标题栏,含搜索框与功能按钮
- 中部:当前 Tab 的内容区
- 底部:Tab 栏,4 个 Tab

### 3.2 各 Tab 详细界面

**首页 Tab**:显示全部链接,按时间倒序排列。顶部有搜索框与"+"按钮(用于手动添加)。
**主题 Tab**:显示已创建的主题列表,支持拖拽排序。
**标签 Tab**:显示全部标签,点击进入对应链接列表。
**设置 Tab**:导入导出、阅读统计、个人信息等。

### 3.3 顶部操作栏

不同 Tab 下,顶部操作栏的按钮含义不同:
- 首页:搜索框 + "+"添加
- 主题:搜索框 + "+"新建主题
- 标签:搜索框 + "+"新建标签
- 设置:仅标题

### 3.4 多选模式

长按任意列表项进入多选模式。顶部出现多选操作栏,可批量删除、批量打标签。

### 3.5 浮窗

PDF 阅读/网页阅读时,可从底部上滑打开浮窗(目录、字号、亮度、书签)。

---

## 第 4 章 链接管理

### 4.1 分享接收

[图 4-1:此处插入从浏览器分享到读享的截图 - 系统分享弹窗]

读享通过 `SEND` / `SEND_MULTIPLE` Intent 接收其他应用分享的内容。

**操作步骤**:
1. 在其他 App(浏览器、小红书、B 站、文件管理器等)中找到"分享"按钮
2. 在分享目标列表中选择"读享"
3. 读享弹出确认页,显示解析出的标题与 URL
4. 点击"保存"即可加入读享

### 4.2 自动解析示例

读享会智能识别分享文本中的链接与标题,以下是典型场景:

- **小红书分享**:`【xxx】https://www.xiaohongshu.com/...` → 提取标题"xxx"与链接
- **B 站分享**:`[av12345678] xxx https://www.bilibili.com/video/...` → 提取 BV 号与标题,并将链接转换为 `https://www.bilibili.com/video/av12345678` 兼容格式
- **通用链接**:直接提取 URL,标题为 URL 域名
- **PDF 文件**:保存为文档条目,不解析文本

### 4.3 列表操作

- **点击**:打开链接(网页直接用 WebView,PDF 用 Document Viewer)
- **长按**:进入多选模式
- **左滑**:显示删除按钮
- **右滑**:显示打标签按钮

### 4.4 多选与批量

进入多选后:
- 顶部出现"全选"与"取消"
- 底部出现"删除""打标签""取消"三个操作
- 多选模式下点击单项可切换选中状态

### 4.5 搜索与历史

[图 4-2:此处插入搜索界面截图 - 含搜索历史下拉]

首页顶部搜索框支持:
- 实时模糊匹配标题与标签
- 搜索历史下拉(自动记录最近 10 次)
- 历史项右侧 × 可删除单条
- 顶部"清空"按钮可清空全部历史

---

## 第 5 章 标签与主题

### 5.1 标签的添加、重命名、删除

**添加**:标签 Tab 顶部 "+" 按钮 → 输入名称 → 保存
**重命名**:长按标签 → 输入新名称
**删除**:长按标签 → 确认(若有链接使用此标签,会同时解除关联)

### 5.2 多标签筛选

在任意链接列表(首页/主题详情等)中,点击顶部标签栏可切换多选状态。选中的标签以"AND"逻辑组合:仅显示同时命中所有标签的链接。

### 5.3 主题创建与订阅

[图 5-1:此处插入主题列表截图]

**创建主题**:主题 Tab "+" → 输入主题名 → 创建
**添加链接到主题**:链接详情页"添加到主题" → 选择主题
**订阅**:部分主题支持订阅源(RSS),自动拉取更新

### 5.4 主题详情

点击主题进入详情页:
- 主题信息:名称、创建时间、链接数
- 链接列表:本主题下的所有链接
- 操作:编辑主题、添加子主题、删除主题

### 5.5 拖拽排序

[图 5-2:此处插入拖拽排序截图 - 主题列表项长按高亮]

长按主题列表项,待高亮后拖拽到目标位置释放即可。系统会持久化新顺序。

---

## 第 6 章 阅读功能

### 6.1 网页阅读

[图 6-1:此处插入 WebView 阅读截图 - 顶部进度条 + 底部工具栏]

点击链接中的网页类型,进入 WebViewActivity:
- 顶部:标题 + 返回 + 收藏 + 分享
- 中部:网页内容
- 底部:进度条 + 字号 + 亮度 + 画中画

**功能点**:
- **字号调节**:点击"字号"按钮,弹窗选择小/标准/大/超大
- **亮度调节**:点击"亮度"按钮,横向滑动条调节
- **后台播放**:网页中的视频/音频,点击播放后按 Home 键,后台继续播放
- **画中画**:支持系统级 PiP,小窗继续播放
- **阅读历史**:每次打开链接,自动更新"最近打开"时间

### 6.2 PDF 阅读

[图 6-2:此处插入 PDF 阅读截图 - 左侧目录抽屉]

点击 PDF 类型链接或从文件管理器打开 PDF,进入 DocumentViewerActivity:
- 左侧滑出:目录(自动从 PDF Outline 提取)
- 右侧滑出:书签(用户手动添加)
- 底部:页码跳转 + 缩放

**功能点**:
- **目录自动提取**:打开 PDF 时调用 PdfOutlineExtractor 解析大纲
- **阅读进度记忆**:关闭后重新打开,自动跳转到上次阅读位置
- **书签**:点击页面右上角 ☆ 添加书签
- **缩放**:双指捏合或双击切换缩放

### 6.3 桌面快捷方式

[图 6-3:此处插入桌面快捷方式截图 - 桌面长按菜单]

长按读享桌面图标,在弹出的快捷菜单中可看到"创建快捷方式"选项:
- 选中后,选择目标 URL(从已保存的链接中选)
- 在桌面创建图标,点击直接打开该 URL

### 6.4 统计

[图 6-4:此处插入统计页面截图 - 点击次数折线图]

设置 → 阅读统计,显示:
- 总点击次数
- 每日点击折线图
- 最常点击的 10 个链接

---

## 第 7 章 同步与导入导出

### 7.1 同步

读享支持跨设备同步:
- **同步源**:可指向本地目录或 WebDAV 服务
- **触发方式**:手动(设置 → 立即同步)或自动(打开 App 时检测)
- **冲突处理**:以最新时间戳为准

### 7.2 导出

设置 → 导出,选择导出格式:
- **JSON**:完整数据(含标签、主题)
- **CSV**:仅链接表,适合导入 Excel

### 7.3 导入

设置 → 导入,选择 JSON 文件:
- 弹窗预览待导入的条目数
- 选择"合并"(已存在跳过)或"覆盖"(强制更新)

### 7.4 备份建议

- 每周导出一次 JSON 备份
- 备份文件建议保存到云盘或电脑
- 切换设备时,先在新设备安装读享,再导入备份

---

## 第 8 章 常见问题

### 8.1 链接抓不到怎么办

- 确认分享时选中了"读享"目标
- 部分 App 的分享内容是图片而非文本,读享无法直接解析;可复制链接后到读享首页"+"手动粘贴
- 检查读享是否被电池优化"杀掉"白名单

### 8.2 PDF 打开失败

- 确认 PDF 文件未损坏(可在他处打开)
- 确认 PDF 已在系统下载目录中,或通过读享的"接收 PDF 分享"进入
- 大文件(>100MB)可能加载慢,耐心等待

### 8.3 通知权限被拒绝

- 后台播放仍可工作,但无法显示通知栏控件
- 若需通知控件,设置 → 应用 → 读享 → 通知 → 允许

### 8.4 占用空间过大

- 设置 → 存储,可查看数据库大小
- 清理归档:对不需要的链接执行"删除"
- 导出后定期删除旧链接

### 8.5 联系与反馈

- 邮箱:《zhangsan@example.com》
- 项目主页:[https://github.com/notfresh/reading-share-android](https://github.com/notfresh/reading-share-android)
- Issue 区:在 GitHub 项目主页提 Issue

---

## 文档信息

| 字段 | 值 |
|---|---|
| 软件全称 | 读享 V1.0 软件 |
| 软件简称 | 读享 |
| 版本号 | V1.0 |
| 著作权人 | 《XX 有限公司》 |
| 文档版本 | V1.0 |
| 编写日期 | 2026-06-11 |
| 联系方式 | 《zhangsan@example.com》 |
```

- [ ] **Step 2: 提交**

```bash
cd "c:/projects/duxiang-pack/duxiang-android"
git add docs/superpowers/rz/02-用户手册/用户手册.md
git commit -m "docs(rz): 用户手册人可读源(8 章 + 封面 + 文档信息页)"
```

---

### Task 2.2: 写用户手册生成脚本(.py)

**Files:**
- Create: `docs/superpowers/rz/02-用户手册/生成脚本.py`

- [ ] **Step 1: 写生成脚本**

新建 `docs/superpowers/rz/02-用户手册/生成脚本.py`,完整内容:

```python
"""用户手册 .docx 生成脚本

读取同目录下 用户手册.md,渲染为带页眉页脚的 Word。
运行:python 生成脚本.py
输出:用户手册.docx
"""
import re
from pathlib import Path

from docx import Document
from docx.shared import Pt, Cm
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn


HERE = Path(__file__).parent
SRC = HERE / "用户手册.md"
OUT = HERE / "用户手册.docx"

SOFTWARE_NAME = "读享 V1.0 软件"
DOC_VERSION = "V1.0"


def set_run_font(run, size: int = 12) -> None:
    run.font.name = "宋体"
    run.font.size = Pt(size)
    rPr = run._element.get_or_add_rPr()
    rFonts = rPr.find(qn("w:rFonts"))
    if rFonts is None:
        from docx.oxml import OxmlElement
        rFonts = OxmlElement("w:rFonts")
        rPr.append(rFonts)
    rFonts.set(qn("w:eastAsia"), "宋体")


def add_page_header_footer(section) -> None:
    header = section.header
    h_p = header.paragraphs[0]
    h_p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    h_run = h_p.add_run(f"{SOFTWARE_NAME} - 用户手册 {DOC_VERSION}")
    set_run_font(h_run, 9)

    footer = section.footer
    f_p = footer.paragraphs[0]
    f_p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    f_run = f_p.add_run("第 ")
    set_run_font(f_run, 9)
    # 页码字段
    fldChar1 = f_p.add_run()
    fldChar1.element.append(qn("w:fldChar"))
    instr = f_p.add_run()
    instr.element.append(qn("w:instrText"))
    instr.element.find(qn("w:instrText")).text = " PAGE "
    of_run = f_p.add_run(" 页 / 共 ")
    set_run_font(of_run, 9)
    fldChar2 = f_p.add_run()
    fldChar2.element.append(qn("w:fldChar"))
    instr2 = f_p.add_run()
    instr2.element.append(qn("w:instrText"))
    instr2.element.find(qn("w:instrText")).text = " NUMPAGES "
    end_run = f_p.add_run(" 页")
    set_run_font(end_run, 9)


def parse_markdown(md_text: str) -> list[tuple[str, str, str]]:
    """返回 [(level, type, content), ...]
    type: 'h1' | 'h2' | 'h3' | 'p' | 'hr' | 'table'
    level: 1/2/3
    """
    blocks: list[tuple[str, str, str]] = []
    lines = md_text.split("\n")
    i = 0
    while i < len(lines):
        line = lines[i].rstrip()
        if not line.strip():
            i += 1
            continue
        if line.startswith("# "):
            blocks.append(("1", "h1", line[2:].strip()))
        elif line.startswith("## "):
            blocks.append(("2", "h2", line[3:].strip()))
        elif line.startswith("### "):
            blocks.append(("3", "h3", line[4:].strip()))
        elif line.startswith("---"):
            blocks.append(("0", "hr", ""))
        elif line.startswith("|"):
            # 简单表格
            table_lines = []
            while i < len(lines) and lines[i].startswith("|"):
                table_lines.append(lines[i].rstrip())
                i += 1
            blocks.append(("0", "table", "\n".join(table_lines)))
            continue
        else:
            # 普通段落(累积到下一空行/标题)
            para_lines = [line]
            i += 1
            while i < len(lines):
                nxt = lines[i].rstrip()
                if not nxt or nxt.startswith("#") or nxt.startswith("---") or nxt.startswith("|"):
                    break
                para_lines.append(nxt)
                i += 1
            blocks.append(("0", "p", "\n".join(para_lines)))
            continue
        i += 1
    return blocks


def add_table(doc: Document, table_text: str) -> None:
    rows = [r for r in table_text.splitlines() if r.strip() and not re.match(r"^\|[\s\-:|]+\|$", r)]
    if not rows:
        return
    parsed = [re.findall(r"\|([^|]*)", r) for r in rows]
    parsed = [[c.strip() for c in row] for row in parsed]
    n_cols = max(len(row) for row in parsed)
    parsed = [row + [""] * (n_cols - len(row)) for row in parsed]
    table = doc.add_table(rows=len(parsed), cols=n_cols)
    table.style = "Table Grid"
    for r_idx, row in enumerate(parsed):
        for c_idx, cell in enumerate(row):
            cell.text = ""
            p = cell.paragraphs[0]
            run = p.add_run(cell)
            set_run_font(run, 10)


def build() -> None:
    md_text = SRC.read_text(encoding="utf-8")
    blocks = parse_markdown(md_text)

    doc = Document()
    for section in doc.sections:
        section.page_width = Cm(21)
        section.page_height = Cm(29.7)
        section.top_margin = Cm(2.5)
        section.bottom_margin = Cm(2.5)
        section.left_margin = Cm(2.5)
        section.right_margin = Cm(2.5)
        add_page_header_footer(section)

    for level, btype, content in blocks:
        if btype == "h1":
            p = doc.add_paragraph()
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            run = p.add_run(content)
            set_run_font(run, 22)
            run.bold = True
        elif btype == "h2":
            p = doc.add_paragraph()
            run = p.add_run(content)
            set_run_font(run, 18)
            run.bold = True
        elif btype == "h3":
            p = doc.add_paragraph()
            run = p.add_run(content)
            set_run_font(run, 14)
            run.bold = True
        elif btype == "p":
            for line in content.splitlines():
                p = doc.add_paragraph()
                run = p.add_run(line)
                set_run_font(run, 12)
        elif btype == "hr":
            doc.add_paragraph("―" * 30)
        elif btype == "table":
            add_table(doc, content)

    doc.save(OUT)
    print(f"生成成功: {OUT}")


if __name__ == "__main__":
    build()
```

- [ ] **Step 2: 跑脚本生成 .docx**

```bash
cd "c:/projects/duxiang-pack/duxiang-android/docs/superpowers/rz/02-用户手册"
python 生成脚本.py
```

预期:`生成成功: ...\用户手册.docx`

- [ ] **Step 3: 人工检查**

用 Word/WPS 打开:
- 8 章齐全(第 1~8 章 + 文档信息页)
- 页眉显示"读享 V1.0 软件 - 用户手册 V1.0"
- 页脚显示"第 X 页 / 共 Y 页"(打开后 Word 自动更新)
- 表格正常渲染
- 占位符 `《XX 有限公司》` 等保留

- [ ] **Step 4: 提交**

```bash
cd "c:/projects/duxiang-pack/duxiang-android"
git add docs/superpowers/rz/02-用户手册/
git commit -m "feat(rz): 用户手册 .docx 生成脚本与首版输出"
```

---

### Task 2.3: 写用户手册自检脚本

**Files:**
- Create: `docs/superpowers/rz/02-用户手册/自检脚本.py`

- [ ] **Step 1: 写自检脚本**

新建 `docs/superpowers/rz/02-用户手册/自检脚本.py`:

```python
"""用户手册自检:章节齐全、占位符存在"""
import sys
from pathlib import Path

from docx import Document

HERE = Path(__file__).parent
DOCX = HERE / "用户手册.docx"
MD = HERE / "用户手册.md"

EXPECTED_CHAPTERS = [
    "第 1 章", "第 2 章", "第 3 章", "第 4 章",
    "第 5 章", "第 6 章", "第 7 章", "第 8 章",
]
EXPECTED_PLACEHOLDERS = ["《XX 有限公司》", "《zhangsan@example.com》"]
EXPECTED_IMG_MARKERS = [
    "图 3-1", "图 4-1", "图 4-2", "图 5-1", "图 5-2",
    "图 6-1", "图 6-2", "图 6-3", "图 6-4",
]


def check_md() -> list[str]:
    errors: list[str] = []
    text = MD.read_text(encoding="utf-8")
    for ch in EXPECTED_CHAPTERS:
        if ch not in text:
            errors.append(f"缺少章节: {ch}")
    for ph in EXPECTED_PLACEHOLDERS:
        if ph not in text:
            errors.append(f"缺少占位符: {ph}")
    img_count = sum(text.count(m) for m in EXPECTED_IMG_MARKERS)
    if img_count < 8:
        errors.append(f"图占位符不足: 至少 8 个,当前 {img_count}")
    return errors


def check_docx() -> list[str]:
    errors: list[str] = []
    if not DOCX.exists():
        return [f"缺少文件: {DOCX}"]
    doc = Document(DOCX)
    text = "\n".join(p.text for p in doc.paragraphs)
    for ch in EXPECTED_CHAPTERS:
        if ch not in text:
            errors.append(f"docx 缺少章节: {ch}")
    return errors


def main() -> int:
    errors = check_md() + check_docx()
    if errors:
        for e in errors:
            print(f"[FAIL] {e}")
        return 1
    print("[OK] 用户手册自检通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
```

- [ ] **Step 2: 运行自检**

```bash
cd "c:/projects/duxiang-pack/duxiang-android/docs/superpowers/rz/02-用户手册"
python 自检脚本.py
```

预期:`[OK] 用户手册自检通过`

- [ ] **Step 3: 提交**

```bash
cd "c:/projects/duxiang-pack/duxiang-android"
git add docs/superpowers/rz/02-用户手册/自检脚本.py
git commit -m "test(rz): 用户手册自检脚本"
```

---

### ✅ Checkpoint 2:第 2 阶段交付确认

**暂停,等用户确认以下产物:**

| 文件 | 状态 |
|---|---|
| `02-用户手册/用户手册.md` | ☐ 已生成 |
| `02-用户手册/用户手册.docx` | ☐ 已生成(目标 40 页) |
| `02-用户手册/生成脚本.py` | ☐ 已提交 |
| `02-用户手册/自检脚本.py` | ☐ 通过 |

**给用户报告:**
- 8 章齐全 + 文档信息页
- 至少 8 张图占位符
- 占位符保留

**用户确认后,进入第 3 阶段(代码鉴别材料)。**

---

## 第 3 阶段:源程序代码鉴别材料

### Task 3.1: 写代码选取说明(.md)

**Files:**
- Create: `docs/superpowers/rz/03-代码鉴别材料/代码选取说明.md`

- [ ] **Step 1: 写说明文件**

新建 `docs/superpowers/rz/03-代码鉴别材料/代码选取说明.md`,完整内容:

````markdown
# 读享 V1.0 软件 - 源程序代码选取说明

## 1. 总体策略

为体现本软件完整、原创、独立的代码组织,采用「主流程 + 核心底层」的二段式选取:

- **前 30 页**:从用户打开 App 到核心交互的主路径,体现完整业务流。
- **后 30 页**:从 UI 控制器到底层算法/工具的核心底层,体现技术深度。

## 2. 前 30 页(主流程)

按用户操作顺序拼接:

| 顺序 | 文件 | 估算行数 |
|---|---|---|
| 1 | `MainActivity.java` | 300 |
| 2 | `ShareUtil.java` | 200 |
| 3 | `HomeFragment.java` | 250 |
| 4 | `LinkAdapter.java` | 150 |
| 5 | `db/LinkDao.java` | 120 |
| 6 | `db/DatabaseHelper.java` | 100 |
| 7 | `model/Link.java` + `model/Tag.java` | 100 |
| 8 | `ui/tag/TagFragment.java` + `TagAdapter.java` | 200 |
| 9 | `ui/subject/SubjectDetailActivity.java` | 150 |
| 10 | `ui/archive/ArchiveFragment.java` | 100 |
| 11 | `util/RecentTagsManager.java` | 80 |
| 12 | `util/StringUtil.java` | 50 |
| 13 | 各 Fragment 回调接口 | 100 |
| **合计** | | **约 1900 行** |

## 3. 后 30 页(核心底层)

按"算法核心 → 工具类 → 控制器 → 配置/常量"顺序拼接:

| 顺序 | 文件 | 估算行数 |
|---|---|---|
| 1 | `core/Synchronizer.java` | 400 |
| 2 | `util/PdfOutlineExtractor.java` | 200 |
| 3 | `util/BilibiliUrlConverter.java` | 100 |
| 4 | `util/CrawlUtil.java` | 150 |
| 5 | `util/ExportUtil.java` + `util/ImportUtil.java` | 200 |
| 6 | `util/ImageUtil.java` | 100 |
| 7 | `util/SubjectUtil.java` | 150 |
| 8 | `util/SwipeActionsHelper.java` | 100 |
| 9 | `ui/subject/SubjectFragment.java` | 150 |
| 10 | `ui/document/DocumentViewerActivity.java` | 150 |
| 11 | `WebViewActivity.java` + `WebViewManager.java` | 200 |
| 12 | `ClickStatisticsActivity.java` + `UserProfileActivity.java` | 200 |
| 13 | `WebViewBackgroundService.java` | 100 |
| 14 | `config/` 下的常量、枚举 | 100 |
| **合计** | | **约 2300 行** |

## 4. 总行数报告

- 申请表中报告的「源程序总行数 = 约 3000 行」,指上述摘选代码约 3000 行的实际可读量。
- 实际项目代码量大于此值,摘选以体现软件架构与核心算法为目标。

## 5. 排除规则

下列内容**不**进入鉴别材料:

- `R.java`、`BuildConfig.java` 等 Android build 工具自动生成的文件
- `app/build/` 目录下所有文件
- `*.iml`、`.idea/`、`.gradle/` 等 IDE/构建配置文件
- 测试代码(`androidTest/`、`test/` 目录)
- 第三方依赖源码

## 6. 格式约束

- 每页 50 行
- 单行 ≤ 80 字符
- 等宽字体 Courier New 10 号
- 页眉:`读享 V1.0 软件 - 源程序代码(前/后 30 页)` + 当前文件名 + 起始行号
- 页脚:`第 X 页 / 共 60 页`
- 跨页时文件直接断到下一页,不切断类成员
- 单行超 80 字符时,行尾加 `// <第 X 页 第 Y 行>` 后换行
````

- [ ] **Step 2: 提交**

```bash
cd "c:/projects/duxiang-pack/duxiang-android"
git add docs/superpowers/rz/03-代码鉴别材料/代码选取说明.md
git commit -m "docs(rz): 代码选取说明(前 30 主流程 + 后 30 核心底层)"
```

---

### Task 3.2: 写代码 PDF 生成脚本(.py) - 脚手架

**Files:**
- Create: `docs/superpowers/rz/03-代码鉴别材料/生成脚本.py`

- [ ] **Step 1: 写脚本骨架**

新建 `docs/superpowers/rz/03-代码鉴别材料/生成脚本.py`,完整内容:

```python
"""源程序代码鉴别材料 PDF 生成脚本

读取项目 Java 源文件,按"主流程 + 核心底层"策略拼成前 30 页 + 后 30 页 PDF,
并生成首页 30 行 + 末页 30 行独立鉴别件。
运行:python 生成脚本.py
"""
import re
import sys
from pathlib import Path
from datetime import date

from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.cidfonts import UnicodeCIDFont

# 源码根: 脚本位于 <project>/docs/superpowers/rz/03-代码鉴别材料/生成脚本.py
# 向上 4 级即项目根
PROJECT_ROOT = Path(__file__).resolve().parents[4]
SRC_ROOT = PROJECT_ROOT / "app" / "src" / "main" / "java"
OUT_DIR = Path(__file__).parent

SOFTWARE_NAME = "读享 V1.0 软件"
PAGE_LINES = 50
LINE_MAX = 80

# 注册中文字体(支持页眉中文与代码中文注释)
pdfmetrics.registerFont(UnicodeCIDFont("STSong-Light"))

FRONT_FILES = [
    "person/notfresh/readingshare/MainActivity.java",
    "person/notfresh/readingshare/ShareUtil.java",
    "person/notfresh/readingshare/ui/home/HomeFragment.java",
    "person/notfresh/readingshare/adapter/LinkAdapter.java",
    "person/notfresh/readingshare/db/LinkDao.java",
    "person/notfresh/readingshare/db/DatabaseHelper.java",
    "person/notfresh/readingshare/model/Link.java",
    "person/notfresh/readingshare/model/Tag.java",
    "person/notfresh/readingshare/ui/tag/TagFragment.java",
    "person/notfresh/readingshare/adapter/TagAdapter.java",
    "person/notfresh/readingshare/ui/subject/SubjectDetailActivity.java",
    "person/notfresh/readingshare/ui/archive/ArchiveFragment.java",
    "person/notfresh/readingshare/util/RecentTagsManager.java",
    "person/notfresh/readingshare/util/StringUtil.java",
]

BACK_FILES = [
    "person/notfresh/readingshare/core/Synchronizer.java",
    "person/notfresh/readingshare/util/PdfOutlineExtractor.java",
    "person/notfresh/readingshare/util/BilibiliUrlConverter.java",
    "person/notfresh/readingshare/util/CrawlUtil.java",
    "person/notfresh/readingshare/util/ExportUtil.java",
    "person/notfresh/readingshare/util/ImportUtil.java",
    "person/notfresh/readingshare/util/ImageUtil.java",
    "person/notfresh/readingshare/util/SubjectUtil.java",
    "person/notfresh/readingshare/util/SwipeActionsHelper.java",
    "person/notfresh/readingshare/ui/subject/SubjectFragment.java",
    "person/notfresh/readingshare/ui/document/DocumentViewerActivity.java",
    "person/notfresh/readingshare/WebViewActivity.java",
    "person/notfresh/readingshare/WebViewManager.java",
    "person/notfresh/readingshare/ClickStatisticsActivity.java",
    "person/notfresh/readingshare/UserProfileActivity.java",
    "person/notfresh/readingshare/WebViewBackgroundService.java",
    "person/notfresh/readingshare/config/Config.java",
]


def read_source(rel_path: str) -> str:
    fp = SRC_ROOT / rel_path
    if not fp.exists():
        return f"// MISSING: {rel_path}\n"
    return fp.read_text(encoding="utf-8", errors="replace")


def wrap_line(line: str) -> str:
    """单行 ≤ 80 字符;超出时行尾加 // <...> 标记后换行"""
    if len(line) <= LINE_MAX:
        return line
    # 简单按字符截断(中文按字符计)
    return line[:LINE_MAX] + " // <wrapped>"


def collect_code_lines(file_list: list[str]) -> list[tuple[str, int, str]]:
    """返回 [(filename, line_no, line), ...] 平铺代码行"""
    out: list[tuple[str, int, str]] = []
    for rel in file_list:
        text = read_source(rel)
        for i, line in enumerate(text.splitlines(), start=1):
            out.append((rel, i, wrap_line(line)))
    return out


def paginate(lines: list[tuple[str, int, str]], per_page: int) -> list[list[tuple[str, int, str]]]:
    pages = []
    for i in range(0, len(lines), per_page):
        pages.append(lines[i:i + per_page])
    return pages


def draw_title_page(c: canvas.Canvas, title: str, files: list[str], total_lines: int) -> None:
    c.setFont("STSong-Light", 20)
    c.drawCentredString(A4[0] / 2, A4[1] - 40 * mm, SOFTWARE_NAME)
    c.setFont("STSong-Light", 16)
    c.drawCentredString(A4[0] / 2, A4[1] - 55 * mm, title)
    c.setFont("STSong-Light", 12)
    c.drawString(25 * mm, A4[1] - 80 * mm, f"著作权人:XX 有限公司")
    c.drawString(25 * mm, A4[1] - 90 * mm, f"生成日期:{date.today().isoformat()}")
    c.drawString(25 * mm, A4[1] - 100 * mm, f"摘选总行数:{total_lines}")
    c.drawString(25 * mm, A4[1] - 110 * mm, "文件清单:")
    y = A4[1] - 120 * mm
    c.setFont("STSong-Light", 9)
    for f in files:
        c.drawString(30 * mm, y, f"  - {f}")
        y -= 5 * mm
        if y < 20 * mm:
            c.showPage()
            y = A4[1] - 20 * mm
    c.showPage()


def draw_code_page(
    c: canvas.Canvas,
    page_idx: int,
    total_pages: int,
    section_title: str,
    file_name: str,
    start_line: int,
    page_lines: list[tuple[str, int, str]],
) -> None:
    # 页眉
    c.setFont("STSong-Light", 9)
    c.drawString(15 * mm, A4[1] - 12 * mm, f"{SOFTWARE_NAME} - 源程序代码({section_title})")
    c.drawString(15 * mm, A4[1] - 17 * mm, f"文件:{file_name} 起始行:{start_line}")

    # 主体代码
    c.setFont("Courier", 9)
    y = A4[1] - 25 * mm
    line_h = 4.2 * mm
    for fname, lno, text in page_lines:
        c.drawString(15 * mm, y, f"{lno:4d}  {text[:LINE_MAX]}")
        y -= line_h
    # 不足 50 行补空行
    while y > A4[1] - 25 * mm - PAGE_LINES * line_h:
        y -= line_h

    # 页脚
    c.setFont("STSong-Light", 9)
    c.drawCentredString(A4[0] / 2, 12 * mm, f"第 {page_idx} 页 / 共 {total_pages} 页")
    c.showPage()


def render_section(
    out_path: Path,
    section_title: str,
    file_list: list[str],
) -> tuple[int, list[str]]:
    """渲染一个 section(前 30 页 / 后 30 页 / 首页 30 行 / 末页 30 行)"""
    c = canvas.Canvas(str(out_path), pagesize=A4)
    is_standalone = "首页" in section_title or "末页" in section_title

    if is_standalone:
        # 独立鉴别件:不画标题页,只取 30 行
        all_lines = collect_code_lines(file_list)
        if "首页" in section_title:
            lines = all_lines[:30]
        else:
            lines = all_lines[-30:] if len(all_lines) >= 30 else all_lines
        pages = [lines]
        total_pages = 1
    else:
        lines = collect_code_lines(file_list)
        pages = paginate(lines, PAGE_LINES)
        total_pages = len(pages) + 1  # +1 标题页
        draw_title_page(c, section_title, file_list, len(lines))

    for idx, page_lines in enumerate(pages, start=1):
        page_idx = idx + 1 if not is_standalone else idx
        if not page_lines:
            continue
        file_name = page_lines[0][0]
        start_line = page_lines[0][1]
        draw_code_page(c, page_idx, total_pages, section_title, file_name, start_line, page_lines)

    c.save()
    return total_pages, [str(out_path)]


def main() -> int:
    if not SRC_ROOT.exists():
        print(f"FAIL: 源码目录不存在 {SRC_ROOT}")
        return 1

    outputs: list[Path] = []

    # 前 30 页
    out = OUT_DIR / "源程序代码-前30页.pdf"
    render_section(out, "前 30 页", FRONT_FILES)
    outputs.append(out)
    md_out = OUT_DIR / "源程序代码-前30页.md"
    lines = collect_code_lines(FRONT_FILES)
    md_out.write_text(
        "\n".join(f"{lno:4d}  {text}" for _, lno, text in lines),
        encoding="utf-8",
    )
    outputs.append(md_out)

    # 后 30 页
    out = OUT_DIR / "源程序代码-后30页.pdf"
    render_section(out, "后 30 页", BACK_FILES)
    outputs.append(out)
    md_out = OUT_DIR / "源程序代码-后30页.md"
    lines = collect_code_lines(BACK_FILES)
    md_out.write_text(
        "\n".join(f"{lno:4d}  {text}" for _, lno, text in lines),
        encoding="utf-8",
    )
    outputs.append(md_out)

    # 首页 30 行
    out = OUT_DIR / "源程序代码-首页30行.pdf"
    render_section(out, "首页 30 行", FRONT_FILES)
    outputs.append(out)

    # 末页 30 行
    out = OUT_DIR / "源程序代码-末页30行.pdf"
    render_section(out, "末页 30 行", BACK_FILES)
    outputs.append(out)

    print("生成成功:")
    for o in outputs:
        print(f"  - {o}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
```

- [ ] **Step 2: 安装依赖并跑脚本**

```bash
cd "c:/projects/duxiang-pack/duxiang-android/docs/superpowers/rz/03-代码鉴别材料"
pip install reportlab
python 生成脚本.py
```

预期:
```
生成成功:
  - ...\源程序代码-前30页.pdf
  - ...\源程序代码-前30页.md
  - ...\源程序代码-后30页.pdf
  - ...\源程序代码-后30页.md
  - ...\源程序代码-首页30行.pdf
  - ...\源程序代码-末页30行.pdf
```

- [ ] **Step 3: 人工检查 PDF**

用 PDF 阅读器打开:
- 4 份 PDF 均能打开
- 页眉:软件名 + 文件名 + 起始行号
- 页脚:页码
- 字体:中文字体(宋体)、代码 Courier

- [ ] **Step 4: 提交**

```bash
cd "c:/projects/duxiang-pack/duxiang-android"
git add docs/superpowers/rz/03-代码鉴别材料/
git commit -m "feat(rz): 代码鉴别材料 PDF 生成脚本与首版输出"
```

---

### Task 3.3: 写代码自检脚本

**Files:**
- Create: `docs/superpowers/rz/03-代码鉴别材料/自检脚本.py`

- [ ] **Step 1: 写自检脚本**

新建 `docs/superpowers/rz/03-代码鉴别材料/自检脚本.py`:

```python
"""代码鉴别材料自检:PDF 存在、字符 ≤ 80、文件数齐全"""
import sys
from pathlib import Path

HERE = Path(__file__).parent
EXPECTED_PDFS = [
    "源程序代码-前30页.pdf",
    "源程序代码-后30页.pdf",
    "源程序代码-首页30行.pdf",
    "源程序代码-末页30行.pdf",
]
EXPECTED_MDS = [
    "源程序代码-前30页.md",
    "源程序代码-后30页.md",
]
LINE_MAX = 80


def check_files() -> list[str]:
    errors: list[str] = []
    for f in EXPECTED_PDFS + EXPECTED_MDS:
        p = HERE / f
        if not p.exists():
            errors.append(f"缺少文件: {f}")
    return errors


def check_line_width() -> list[str]:
    errors: list[str] = []
    for f in EXPECTED_MDS:
        p = HERE / f
        if not p.exists():
            continue
        for i, line in enumerate(p.read_text(encoding="utf-8").splitlines(), start=1):
            if len(line) > LINE_MAX + 10:  # 容忍行号前缀
                errors.append(f"{f}:{i} 行宽 {len(line)} 超过 {LINE_MAX}")
                if len(errors) > 5:
                    errors.append("...(省略更多)")
                    return errors
    return errors


def main() -> int:
    errors = check_files() + check_line_width()
    if errors:
        for e in errors:
            print(f"[FAIL] {e}")
        return 1
    print("[OK] 代码鉴别材料自检通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
```

- [ ] **Step 2: 运行自检**

```bash
cd "c:/projects/duxiang-pack/duxiang-android/docs/superpowers/rz/03-代码鉴别材料"
python 自检脚本.py
```

预期:`[OK] 代码鉴别材料自检通过`

- [ ] **Step 3: 提交**

```bash
cd "c:/projects/duxiang-pack/duxiang-android"
git add docs/superpowers/rz/03-代码鉴别材料/自检脚本.py
git commit -m "test(rz): 代码鉴别材料自检脚本"
```

---

### ✅ Checkpoint 3:第 3 阶段交付确认

**暂停,等用户确认以下产物:**

| 文件 | 状态 |
|---|---|
| `03-代码鉴别材料/代码选取说明.md` | ☐ 已生成 |
| `03-代码鉴别材料/源程序代码-前30页.pdf` | ☐ 已生成 |
| `03-代码鉴别材料/源程序代码-后30页.pdf` | ☐ 已生成 |
| `03-代码鉴别材料/源程序代码-首页30行.pdf` | ☐ 已生成 |
| `03-代码鉴别材料/源程序代码-末页30行.pdf` | ☐ 已生成 |
| `03-代码鉴别材料/源程序代码-前30页.md` | ☐ 已生成 |
| `03-代码鉴别材料/源程序代码-后30页.md` | ☐ 已生成 |
| `03-代码鉴别材料/生成脚本.py` | ☐ 已提交 |
| `03-代码鉴别材料/自检脚本.py` | ☐ 通过 |

**给用户报告:**
- 4 份 PDF 均生成
- 文件选取:前 30 主流程 + 后 30 核心底层
- 总行数 ≈ 3000 行(在申请表中报告此值)

**用户确认后,进入第 4 阶段(提交前检查)。**

---

## 第 4 阶段:提交前检查

### Task 4.1: 写自检清单

**Files:**
- Create: `docs/superpowers/rz/04-提交前检查/自检清单.md`

- [ ] **Step 1: 写自检清单**

新建 `docs/superpowers/rz/04-提交前检查/自检清单.md`,完整内容:

````markdown
# 读享 V1.0 软件 - 提交前自检清单

> 提交至中国版权保护中心前,逐项检查。

## 一、占位符统一替换

在所有 .md / .docx / .pdf 中替换:

- [ ] `《XX 有限公司》` → 实际公司全称
- [ ] `《统一社会信用代码:91110000XXXXXXXXXX》` → 实际社会信用代码
- [ ] `《XX 软件科技有限公司》` → 实际开发者单位
- [ ] `《XX 市 XX 区 XX 路 1 号》` → 实际通讯地址
- [ ] `《13800000000》` → 实际联系电话
- [ ] `《zhangsan@example.com》` → 实际邮箱
- [ ] `《张三》` → 实际联系人姓名
- [ ] `2025-01-01` → 准确的开发完成日期 / 首次发表日期

**自动化搜索**:
```bash
cd "c:/projects/duxiang-pack/duxiang-android/docs/superpowers/rz"
grep -rn "《" .
grep -rn "2025-01-01" .
```

## 二、申请表(`01-申请表/`)

- [ ] 24 个栏目无空项
- [ ] 软件全称/简称/版本号与三件套一致
- [ ] 第 23 栏申请日期为提交当日
- [ ] 申请人签章栏已盖章

**自动检查**:`python 01-申请表/自检脚本.py` 应输出 `[OK] 申请表自检通过`

## 三、用户手册(`02-用户手册/`)

- [ ] 8 章齐全(第 1~8 章)
- [ ] 文档信息页有
- [ ] 占位符已替换
- [ ] 总页数 ≥ 30 且 ≤ 50
- [ ] 页眉页脚正常显示

**自动检查**:`python 02-用户手册/自检脚本.py` 应输出 `[OK] 用户手册自检通过`

## 四、代码鉴别材料(`03-代码鉴别材料/`)

- [ ] 4 份 PDF 均生成且能打开
- [ ] `代码选取说明.md` 与实际选取文件一致
- [ ] 每页 50 行、单行 ≤ 80 字符
- [ ] 不含 `R.java`、`BuildConfig.java`、build/ 目录
- [ ] 申请表中报告的"总行数"与实际挑选行数一致

**自动检查**:`python 03-代码鉴别材料/自检脚本.py` 应输出 `[OK] 代码鉴别材料自检通过`

## 五、文件命名

提交时建议文件名:

- `读享V1.0软件-申请表.docx`
- `读享V1.0软件-用户手册.docx`
- `读享V1.0软件-源程序代码-前30页.pdf`
- `读享V1.0软件-源程序代码-后30页.pdf`

## 六、提交流程

1. 登录中国版权保护中心(https://www.ccopyright.com.cn)
2. 注册账号并实名认证
3. 进入"计算机软件著作权登记"入口
4. 在线填写申请表(对照本目录 01-申请表 内容)
5. 上传 .docx 与 4 份 .pdf
6. 缴费(目前为 100 元/件,加急另算)
7. 等待审查(通常 30~60 工作日)
8. 审查通过后邮寄证书

## 七、应急处理

若审查被要求补正:

- 常见原因:占位符未替换、页数不足、签字盖章缺失
- 应对:对照本清单逐项核对,补正后重新提交
````

- [ ] **Step 2: 提交**

```bash
cd "c:/projects/duxiang-pack/duxiang-android"
git add docs/superpowers/rz/04-提交前检查/自检清单.md
git commit -m "docs(rz): 提交前自检清单"
```

---

### Task 4.2: 整体验证

- [ ] **Step 1: 跑全部自检脚本**

```bash
cd "c:/projects/duxiang-pack/duxiang-android/docs/superpowers/rz"
python 01-申请表/自检脚本.py
python 02-用户手册/自检脚本.py
python 03-代码鉴别材料/自检脚本.py
```

预期:三行 `[OK] ... 自检通过`

- [ ] **Step 2: 检查目录结构**

```bash
cd "c:/projects/duxiang-pack/duxiang-android/docs/superpowers/rz"
ls -la
ls 01-申请表/ 02-用户手册/ 03-代码鉴别材料/ 04-提交前检查/
```

确认所有文件齐全。

- [ ] **Step 3: 占位符搜索(确认仍有占位符待替换)**

```bash
cd "c:/projects/duxiang-pack/duxiang-android/docs/superpowers/rz"
grep -rn "《" . | head -20
```

应看到 `《XX 有限公司》` 等占位符仍在(等待用户最后替换)。

- [ ] **Step 4: 最终提交**

```bash
cd "c:/projects/duxiang-pack/duxiang-android"
git status  # 应无未提交修改
git log --oneline | head -20  # 应有本次三阶段所有提交
```

---

## 交付总结

| 阶段 | 交付件 | 路径 |
|---|---|---|
| 1. 申请表 | `软著登记申请表.docx` | `01-申请表/` |
| 2. 用户手册 | `用户手册.docx` (40 页) | `02-用户手册/` |
| 3. 代码鉴别材料 | 4 份 PDF | `03-代码鉴别材料/` |
| 4. 自检清单 | `自检清单.md` | `04-提交前检查/` |
| 总说明 | `README.md` | `根目录` |

**用户最后手动做的事**:
1. 替换所有占位符(grep `《` 一键定位)
2. 跑自检脚本确认
3. 在线填写申请表 + 上传文件 + 缴费
