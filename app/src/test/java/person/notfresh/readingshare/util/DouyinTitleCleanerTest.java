package person.notfresh.readingshare.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 抖音标题清洗规则（标题优化规则一）的单测。
 *
 * <p>三个样本都是用户提供的真实抖音分享文案。</p>
 */
public class DouyinTitleCleanerTest {

    private static final String S1 =
            "1.00 复制打开抖音，看看【南巷旧人的作品】# 格斗魅力 # 暴力美学 # 硬帅天花板 "
                    + "https://v.douyin.com/lq35T16GunU/ :5pm xsE:/ 03/08 s@E.ho";

    private static final String S2 =
            "5.10 复制打开抖音，看看【姜胡说的作品】新手入门就五步 新手拍短视频想快点出成绩，五步，一... "
                    + "https://v.douyin.com/uIE9hBVWAaM/ VYz:/ :2pm 08/16 H@V.yt";

    private static final String S3 =
            "6.92 复制打开抖音，看看【安宇sensei（起号版）的作品】坚持日更一定会越来越好的"
                    + "# 个人成长 # 坚持发抖... "
                    + "https://v.douyin.com/utuChW9bdIQ/ KWM:/ :1pm C@h.OX 10/02";

    // ---------- 三个真实样本 ----------

    @Test
    public void clean_sample1_cutsAdPrefix() {
        assertEquals("【南巷旧人的作品】# 格斗魅力 # 暴力美学 # 硬帅天花板",
                DouyinTitleCleaner.clean(S1));
    }

    @Test
    public void clean_sample2_cutsAdPrefix() {
        assertEquals("【姜胡说的作品】新手入门就五步 新手拍短视频想快点出成绩，五步，一...",
                DouyinTitleCleaner.clean(S2));
    }

    @Test
    public void clean_sample3_cutsAdPrefix() {
        assertEquals("【安宇sensei（起号版）的作品】坚持日更一定会越来越好的# 个人成长 # 坚持发抖...",
                DouyinTitleCleaner.clean(S3));
    }

    // ---------- 域名判定 ----------

    @Test
    public void isDouyinUrl_acceptsRealSamples() {
        assertTrue(DouyinTitleCleaner.isDouyinUrl("https://v.douyin.com/lq35T16GunU/"));
        assertTrue(DouyinTitleCleaner.isDouyinUrl("https://www.douyin.com/video/123"));
        assertTrue(DouyinTitleCleaner.isDouyinUrl("https://douyin.com/abc"));
    }

    @Test
    public void isDouyinUrl_rejectsOtherSites() {
        assertFalse(DouyinTitleCleaner.isDouyinUrl("https://www.xiaohongshu.com/explore/1"));
        assertFalse(DouyinTitleCleaner.isDouyinUrl("https://www.bilibili.com/video/BV1"));
        assertFalse(DouyinTitleCleaner.isDouyinUrl("https://example.com/douyin"));
    }

    @Test
    public void isDouyinUrl_rejectsLookalikeHost() {
        // 防止 "douyin.com.evil.com" 这种伪装域名被误判
        assertFalse(DouyinTitleCleaner.isDouyinUrl("https://douyin.com.evil.com/x"));
        assertFalse(DouyinTitleCleaner.isDouyinUrl("https://notdouyin.com/x"));
    }

    @Test
    public void isDouyinUrl_handlesNullAndGarbage() {
        assertFalse(DouyinTitleCleaner.isDouyinUrl(null));
        assertFalse(DouyinTitleCleaner.isDouyinUrl(""));
        assertFalse(DouyinTitleCleaner.isDouyinUrl("not a url"));
    }

    // ---------- 未命中 ----------

    @Test
    public void clean_withoutAnchor_returnsNull() {
        // 另一种抖音模板：没有「看看」两个字 → 本规则不适用
        String other = "长按复制此条消息，打开抖音搜索，观看视频！ https://v.douyin.com/abc/";
        assertNull(DouyinTitleCleaner.clean(other));
    }

    @Test
    public void clean_nullText_returnsNull() {
        assertNull(DouyinTitleCleaner.clean(null));
    }

    // ---------- TitleCleaner 切入口 ----------

    @Test
    public void titleCleaner_cleansDouyinText() {
        assertEquals("【南巷旧人的作品】# 格斗魅力 # 暴力美学 # 硬帅天花板",
                TitleCleaner.clean(S1));
    }

    @Test
    public void titleCleaner_returnsNullForOtherSites() {
        String other = "看看这个网站 https://www.xiaohongshu.com/explore/1 很有意思";
        assertNull(TitleCleaner.clean(other));
    }

    @Test
    public void titleCleaner_returnsNullWhenNoUrl() {
        assertNull(TitleCleaner.clean("看看这段文字，没有链接"));
    }

    @Test
    public void extractUrl_findsFirstUrl() {
        assertEquals("https://v.douyin.com/lq35T16GunU/",
                TitleCleaner.extractUrl(S1));
        assertNull(TitleCleaner.extractUrl("没有链接的文本"));
        assertNull(TitleCleaner.extractUrl(null));
    }
}
