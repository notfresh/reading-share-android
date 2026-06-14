"""源程序代码鉴别材料 PDF 生成脚本

读取项目 Java 源文件,按"主流程 + 核心底层"策略拼成前 30 页 + 后 30 页 PDF,
并生成首页 30 行 + 末页 30 行独立鉴别件。
运行:python 生成脚本.py
"""
from __future__ import annotations
import hashlib
import re
import sys
from pathlib import Path
from datetime import date

# 兼容 Python 3.8.5:reportlab 4.x 用了 md5(usedforsecurity=False),需打补丁
_orig_md5 = hashlib.md5
def _patched_md5(*args, **kwargs):
    kwargs.pop("usedforsecurity", None)
    return _orig_md5(*args, **kwargs)
hashlib.md5 = _patched_md5
hashlib.openssl_md5 = _patched_md5

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
    """单行 ≤ 80 字符;超出时行尾加 // <wrapped> 标记后换行"""
    if len(line) <= LINE_MAX:
        return line
    return line[:LINE_MAX] + " // <wrapped>"


def collect_code_lines(file_list: list) -> list:
    """返回 [(filename, line_no, line), ...] 平铺代码行"""
    out = []
    for rel in file_list:
        text = read_source(rel)
        for i, line in enumerate(text.splitlines(), start=1):
            out.append((rel, i, wrap_line(line)))
    return out


def paginate(lines: list, per_page: int) -> list:
    pages = []
    for i in range(0, len(lines), per_page):
        pages.append(lines[i:i + per_page])
    return pages


def draw_title_page(c, title: str, files: list, total_lines: int) -> None:
    c.setFont("STSong-Light", 20)
    c.drawCentredString(A4[0] / 2, A4[1] - 40 * mm, SOFTWARE_NAME)
    c.setFont("STSong-Light", 16)
    c.drawCentredString(A4[0] / 2, A4[1] - 55 * mm, title)
    c.setFont("STSong-Light", 12)
    c.drawString(25 * mm, A4[1] - 80 * mm, "著作权人:XX 有限公司")
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
    c,
    page_idx: int,
    total_pages: int,
    section_title: str,
    file_name: str,
    start_line: int,
    page_lines: list,
) -> None:
    c.setFont("STSong-Light", 9)
    c.drawString(15 * mm, A4[1] - 12 * mm, f"{SOFTWARE_NAME} - 源程序代码({section_title})")
    c.drawString(15 * mm, A4[1] - 17 * mm, f"文件:{file_name} 起始行:{start_line}")

    c.setFont("Courier", 9)
    y = A4[1] - 25 * mm
    line_h = 4.2 * mm
    for fname, lno, text in page_lines:
        c.drawString(15 * mm, y, f"{lno:4d}  {text[:LINE_MAX]}")
        y -= line_h
    while y > A4[1] - 25 * mm - PAGE_LINES * line_h:
        y -= line_h

    c.setFont("STSong-Light", 9)
    c.drawCentredString(A4[0] / 2, 12 * mm, f"第 {page_idx} 页 / 共 {total_pages} 页")
    c.showPage()


def render_section(
    out_path: Path,
    section_title: str,
    file_list: list,
) -> tuple:
    """渲染一个 section(前 30 页 / 后 30 页 / 首页 30 行 / 末页 30 行)"""
    c = canvas.Canvas(str(out_path), pagesize=A4)
    is_standalone = "首页" in section_title or "末页" in section_title

    if is_standalone:
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
        total_pages = len(pages) + 1
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

    outputs = []

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
