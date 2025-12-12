package person.notfresh.readingshare.util;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import person.notfresh.readingshare.ui.document.TocOutlineItem;

/**
 * PDF目录提取器
 * 通过解析PDF文件格式来提取目录/大纲信息
 */
public class PdfOutlineExtractor {
    private static final String TAG = "PdfOutlineExtractor";

    /**
     * 从PDF文件中提取目录
     * 
     * @param pdfFile PDF文件
     * @return 目录项列表
     */
    public static List<TocOutlineItem> extractOutline(File pdfFile) {
        List<TocOutlineItem> outlineItems = new ArrayList<>();
        
        if (pdfFile == null || !pdfFile.exists()) {
            Log.w(TAG, "PDF文件不存在");
            return outlineItems;
        }

        try {
            // 读取PDF文件内容
            byte[] pdfBytes = readFileBytes(pdfFile);
            String pdfContent = new String(pdfBytes, StandardCharsets.ISO_8859_1);
            
            // 查找Outlines对象
            extractOutlinesFromContent(pdfContent, outlineItems);
            
            Log.d(TAG, "提取到 " + outlineItems.size() + " 个目录项");
            
        } catch (Exception e) {
            Log.e(TAG, "提取PDF目录失败", e);
        }
        
        return outlineItems;
    }

    /**
     * 读取文件字节
     */
    private static byte[] readFileBytes(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[(int) file.length()];
        fis.read(buffer);
        fis.close();
        return buffer;
    }

    /**
     * 从PDF内容中提取目录
     * PDF的目录信息通常存储在Outlines对象中
     */
    private static void extractOutlinesFromContent(String pdfContent, List<TocOutlineItem> outlineItems) {
        try {
            // 方法1：查找所有包含 /Title 和 /Dest 的书签对象
            // PDF书签格式：<< /Title (标题) /Dest [...] /Parent ... /Next ... /First ... /Last ... >>
            // 使用非贪婪匹配，支持多行
            Pattern bookmarkPattern = Pattern.compile(
                "<<[^>]*?/Title\\s*\\(([^)]+)\\)[^>]*?/Dest\\s*\\[([^\\]]+)\\]",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
            );
            
            Matcher bookmarkMatcher = bookmarkPattern.matcher(pdfContent);
            int count = 0;
            
            while (bookmarkMatcher.find() && count < 200) { // 限制最多200个
                try {
                    String title = bookmarkMatcher.group(1);
                    String dest = bookmarkMatcher.group(2);
                    
                    // 解析页码
                    int pageIndex = extractPageIndexFromDest(dest);
                    
                    if (pageIndex >= 0) {
                        // 清理标题
                        title = cleanTitle(title);
                        
                        // 过滤掉明显不是目录的项（如元数据）
                        if (isValidOutlineTitle(title)) {
                            // 简单估算层级（可以根据Parent/First/Last关系更精确计算）
                            int level = estimateLevel(bookmarkMatcher.start(), pdfContent);
                            
                            TocOutlineItem item = new TocOutlineItem(title, pageIndex, level);
                            outlineItems.add(item);
                            count++;
                            
                            Log.d(TAG, "找到目录项: " + title + " -> 第" + (pageIndex + 1) + "页 (层级" + level + ")");
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "解析目录项失败", e);
                }
            }
            
            // 方法2：如果方法1没找到，尝试查找所有 /Title 标记
            if (outlineItems.isEmpty()) {
                extractOutlinesSimple(pdfContent, outlineItems);
            }
            
            // 去重和排序
            deduplicateAndSort(outlineItems);
            
        } catch (Exception e) {
            Log.e(TAG, "解析PDF目录失败", e);
        }
    }

    /**
     * 验证是否是有效的目录标题
     */
    private static boolean isValidOutlineTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            return false;
        }
        
        // 过滤掉常见的元数据标题
        String lowerTitle = title.toLowerCase();
        String[] invalidTitles = {
            "metadata", "info", "xref", "trailer", "catalog",
            "root", "pages", "page", "outlines", "acroform"
        };
        
        for (String invalid : invalidTitles) {
            if (lowerTitle.equals(invalid)) {
                return false;
            }
        }
        
        // 标题长度应该在合理范围内
        return title.length() >= 1 && title.length() <= 200;
    }

    /**
     * 估算目录项的层级
     * 通过查找附近的Parent/First标记来估算
     */
    private static int estimateLevel(int position, String pdfContent) {
        try {
            // 在当前位置前后查找Parent标记
            int start = Math.max(0, position - 1000);
            int end = Math.min(pdfContent.length(), position + 1000);
            String context = pdfContent.substring(start, position);
            
            // 计算Parent标记的数量（每个Parent表示一级嵌套）
            Pattern parentPattern = Pattern.compile("/Parent");
            Matcher parentMatcher = parentPattern.matcher(context);
            int parentCount = 0;
            while (parentMatcher.find()) {
                parentCount++;
            }
            
            return Math.min(parentCount, 5); // 最多5级
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 去重和排序目录项
     */
    private static void deduplicateAndSort(List<TocOutlineItem> items) {
        if (items.isEmpty()) {
            return;
        }
        
        // 按页码排序
        items.sort((a, b) -> Integer.compare(a.getPageIndex(), b.getPageIndex()));
        
        // 简单去重（相同页码和标题的只保留一个）
        List<TocOutlineItem> unique = new ArrayList<>();
        TocOutlineItem last = null;
        
        for (TocOutlineItem item : items) {
            if (last == null || 
                !item.getTitle().equals(last.getTitle()) || 
                item.getPageIndex() != last.getPageIndex()) {
                unique.add(item);
                last = item;
            }
        }
        
        items.clear();
        items.addAll(unique);
    }

    /**
     * 从Dest字符串中提取页码
     * Dest格式可能是：
     * - [3 0 R] -> 页码是3（对象引用）
     * - [3 /XYZ null null null] -> 页码是3
     * - [0 3 R] -> 页码是3（可能是对象编号）
     * - [page 3 /XYZ ...] -> 页码是3
     * - 3 0 R -> 页码是3（无方括号）
     */
    private static int extractPageIndexFromDest(String dest) {
        if (dest == null || dest.trim().isEmpty()) {
            return -1;
        }
        
        try {
            // 移除方括号
            dest = dest.trim().replaceAll("[\\[\\]]", "").trim();
            
            // 方法1：查找 /XYZ 或 /Fit 之前的数字（通常是页码）
            Pattern xyzPattern = Pattern.compile("(\\d+)\\s+/XYZ|(\\d+)\\s+/Fit");
            Matcher xyzMatcher = xyzPattern.matcher(dest);
            if (xyzMatcher.find()) {
                String pageStr = xyzMatcher.group(1) != null ? xyzMatcher.group(1) : xyzMatcher.group(2);
                int pageNumber = Integer.parseInt(pageStr);
                if (pageNumber > 0 && pageNumber < 10000) {
                    return Math.max(0, pageNumber - 1);
                }
            }
            
            // 方法2：查找第一个合理的数字（通常是页码）
            Pattern pagePattern = Pattern.compile("\\b(\\d{1,4})\\b");
            Matcher pageMatcher = pagePattern.matcher(dest);
            
            while (pageMatcher.find()) {
                try {
                    int num = Integer.parseInt(pageMatcher.group(1));
                    // 过滤掉明显不是页码的数字（如0、很大的数字）
                    if (num > 0 && num < 10000) {
                        // PDF页码从1开始，转换为0-based索引
                        return Math.max(0, num - 1);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            
            // 方法3：如果Dest是对象引用格式（如 "3 0 R"），提取第一个数字
            Pattern refPattern = Pattern.compile("^(\\d+)\\s+\\d+\\s+R");
            Matcher refMatcher = refPattern.matcher(dest);
            if (refMatcher.find()) {
                int pageNumber = Integer.parseInt(refMatcher.group(1));
                if (pageNumber > 0 && pageNumber < 10000) {
                    return Math.max(0, pageNumber - 1);
                }
            }
            
        } catch (Exception e) {
            Log.w(TAG, "解析页码失败: " + dest, e);
        }
        
        return -1;
    }

    /**
     * 清理标题文本
     * 处理PDF转义字符并尝试修复编码问题
     */
    private static String cleanTitle(String title) {
        if (title == null) {
            return "";
        }
        
        // 移除PDF转义字符
        title = title.replace("\\(", "(")
                    .replace("\\)", ")")
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t")
                    .replace("\\\\", "\\");
        
        // 尝试修复编码问题
        title = fixEncoding(title);
        
        // 移除多余的空白
        title = title.trim();
        
        return title;
    }
    
    /**
     * 修复字符串编码问题
     * PDF中的字符串可能使用多种编码：UTF-8、UTF-16BE、PDFDocEncoding等
     */
    private static String fixEncoding(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        // 检测是否是乱码（包含常见的乱码字符）
        if (text.contains("þÿ") || text.contains("ÿþ")) {
            // 尝试UTF-16BE解码（PDF中常见的Unicode编码）
            try {
                // 移除BOM标记（þÿ = 0xFEFF）
                byte[] bytes = text.getBytes(StandardCharsets.ISO_8859_1);
                
                // 检查是否是UTF-16BE BOM (FE FF)
                if (bytes.length >= 2 && bytes[0] == (byte)0xFE && bytes[1] == (byte)0xFF) {
                    // UTF-16BE with BOM
                    String decoded = new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
                    if (isValidText(decoded)) {
                        return decoded;
                    }
                }
                
                // 检查是否是UTF-16LE BOM (FF FE)
                if (bytes.length >= 2 && bytes[0] == (byte)0xFF && bytes[1] == (byte)0xFE) {
                    // UTF-16LE with BOM
                    String decoded = new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
                    if (isValidText(decoded)) {
                        return decoded;
                    }
                }
                
                // 尝试直接作为UTF-16BE解码（无BOM）
                try {
                    String decoded = new String(bytes, StandardCharsets.UTF_16BE);
                    if (isValidText(decoded) && !decoded.contains("þ") && !decoded.contains("ÿ")) {
                        return decoded;
                    }
                } catch (Exception ignored) {
                }
                
                // 尝试UTF-8解码
                try {
                    String decoded = new String(bytes, StandardCharsets.UTF_8);
                    if (isValidText(decoded) && !decoded.contains("þ") && !decoded.contains("ÿ")) {
                        return decoded;
                    }
                } catch (Exception ignored) {
                }
                
            } catch (Exception e) {
                Log.w(TAG, "编码转换失败: " + text, e);
            }
        }
        
        // 检查是否包含乱码字符，尝试修复
        if (hasGarbledText(text)) {
            try {
                byte[] bytes = text.getBytes(StandardCharsets.ISO_8859_1);
                
                // 尝试UTF-8
                String utf8 = new String(bytes, StandardCharsets.UTF_8);
                if (isValidText(utf8) && !hasGarbledText(utf8)) {
                    return utf8;
                }
                
                // 尝试UTF-16BE
                if (bytes.length % 2 == 0) {
                    String utf16be = new String(bytes, StandardCharsets.UTF_16BE);
                    if (isValidText(utf16be) && !hasGarbledText(utf16be)) {
                        return utf16be;
                    }
                }
                
            } catch (Exception e) {
                Log.w(TAG, "编码修复失败", e);
            }
        }
        
        return text;
    }
    
    /**
     * 检查文本是否包含乱码字符
     */
    private static boolean hasGarbledText(String text) {
        if (text == null) {
            return false;
        }
        // 检查常见的乱码字符
        return text.contains("þ") || text.contains("ÿ") || 
               text.matches(".*[\\u0000-\\u001F].*") && text.length() > 10;
    }
    
    /**
     * 验证文本是否有效（不包含太多控制字符）
     */
    private static boolean isValidText(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        
        // 检查是否包含太多不可打印字符
        int controlCharCount = 0;
        for (char c : text.toCharArray()) {
            if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') {
                controlCharCount++;
            }
        }
        
        // 如果控制字符超过文本长度的10%，可能不是有效文本
        return controlCharCount < text.length() * 0.1;
    }

    /**
     * 简单的目录提取方法（备用方案）
     * 查找所有包含 /Title 的对象
     */
    private static void extractOutlinesSimple(String pdfContent, List<TocOutlineItem> outlineItems) {
        try {
            // 查找所有 /Title 标记
            Pattern titlePattern = Pattern.compile("/Title\\s*\\(([^)]+)\\)");
            Matcher titleMatcher = titlePattern.matcher(pdfContent);
            
            int count = 0;
            while (titleMatcher.find() && count < 100) { // 限制最多100个
                try {
                    String title = cleanTitle(titleMatcher.group(1));
                    
                    // 跳过一些明显不是目录的标题（如元数据）
                    if (title.length() < 2 || title.length() > 200) {
                        continue;
                    }
                    
                    // 尝试在附近查找页码
                    int start = Math.max(0, titleMatcher.start() - 500);
                    int end = Math.min(pdfContent.length(), titleMatcher.end() + 500);
                    String context = pdfContent.substring(start, end);
                    
                    int pageIndex = findPageInContext(context);
                    
                    if (pageIndex >= 0) {
                        TocOutlineItem item = new TocOutlineItem(title, pageIndex, 0);
                        outlineItems.add(item);
                        count++;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "解析标题失败", e);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "简单提取目录失败", e);
        }
    }

    /**
     * 在上下文中查找页码
     */
    private static int findPageInContext(String context) {
        // 查找 /Dest [数字 或 /Page 数字
        Pattern destPattern = Pattern.compile("/Dest\\s*\\[\\s*(\\d+)");
        Matcher destMatcher = destPattern.matcher(context);
        if (destMatcher.find()) {
            try {
                int page = Integer.parseInt(destMatcher.group(1));
                return Math.max(0, page - 1);
            } catch (NumberFormatException ignored) {
            }
        }
        
        // 查找 /Page (数字)
        Pattern pagePattern = Pattern.compile("/Page\\s*\\(?(\\d+)");
        Matcher pageMatcher = pagePattern.matcher(context);
        if (pageMatcher.find()) {
            try {
                int page = Integer.parseInt(pageMatcher.group(1));
                return Math.max(0, page - 1);
            } catch (NumberFormatException ignored) {
            }
        }
        
        return -1;
    }
}

