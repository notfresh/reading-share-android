package person.notfresh.readingshare.util;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import person.notfresh.readingshare.model.LinkItem;
import person.notfresh.readingshare.util.SearchQueryParser;
import person.notfresh.readingshare.util.SearchQueryParser.AndNode;
import person.notfresh.readingshare.util.SearchQueryParser.NotNode;
import person.notfresh.readingshare.util.SearchQueryParser.OrNode;
import person.notfresh.readingshare.util.SearchQueryParser.PhraseNode;
import person.notfresh.readingshare.util.SearchQueryParser.SearchNode;
import person.notfresh.readingshare.util.SearchQueryParser.TermNode;

/**
 * SearchQueryParser 单元测试
 */
public class SearchQueryParserTest {

    // ========== 解析测试 ==========

    @Test
    public void testParse_singleTerm() {
        SearchNode node = SearchQueryParser.parse("java");
        assertTrue(node instanceof TermNode);
        assertEquals("java", ((TermNode) node).term());
    }

    @Test
    public void testParse_twoTerms_defaultAnd() {
        SearchNode node = SearchQueryParser.parse("java python");
        assertTrue(node instanceof AndNode);
        AndNode and = (AndNode) node;
        assertTrue(and.left() instanceof TermNode);
        assertTrue(and.right() instanceof TermNode);
        assertEquals("java", ((TermNode) and.left()).term());
        assertEquals("python", ((TermNode) and.right()).term());
    }

    @Test
    public void testParse_ampersandIsAnd() {
        SearchNode node = SearchQueryParser.parse("java & python");
        assertTrue(node instanceof AndNode);
        assertEquals("java", ((TermNode) ((AndNode) node).left()).term());
        assertEquals("python", ((TermNode) ((AndNode) node).right()).term());
    }

    @Test
    public void testParse_pipeIsOr() {
        SearchNode node = SearchQueryParser.parse("java | python");
        assertTrue(node instanceof OrNode);
        assertEquals("java", ((TermNode) ((OrNode) node).left()).term());
        assertEquals("python", ((TermNode) ((OrNode) node).right()).term());
    }

    @Test
    public void testParse_notPrefix() {
        SearchNode node = SearchQueryParser.parse("!java");
        assertTrue(node instanceof NotNode);
        NotNode not = (NotNode) node;
        assertTrue(not.child() instanceof TermNode);
        assertEquals("java", ((TermNode) not.child()).term());
    }

    @Test
    public void testParse_phrase() {
        SearchNode node = SearchQueryParser.parse("\"hello world\"");
        assertTrue(node instanceof PhraseNode);
        assertEquals("hello world", ((PhraseNode) node).phrase());
    }

    @Test
    public void testParse_groupWithOr() {
        SearchNode node = SearchQueryParser.parse("(java | python)");
        assertTrue(node instanceof OrNode);
        OrNode or = (OrNode) node;
        assertEquals("java", ((TermNode) or.left()).term());
        assertEquals("python", ((TermNode) or.right()).term());
    }

    @Test
    public void testParse_complexExpression() {
        // (java | python) & android
        SearchNode node = SearchQueryParser.parse("(java | python) & android");
        assertTrue(node instanceof AndNode);
        AndNode and = (AndNode) node;
        assertTrue(and.left() instanceof OrNode);
        assertTrue(and.right() instanceof TermNode);
        assertEquals("android", ((TermNode) and.right()).term());
    }

    @Test
    public void testParse_andNot() {
        // java AND NOT python
        SearchNode node = SearchQueryParser.parse("java AND NOT python");
        assertTrue(node instanceof AndNode);
        AndNode and = (AndNode) node;
        assertTrue(and.left() instanceof TermNode);
        assertTrue(and.right() instanceof NotNode);
        assertEquals("java", ((TermNode) and.left()).term());
        assertEquals("python", ((TermNode) ((NotNode) and.right()).child()).term());
    }

    @Test
    public void testParse_emptyString() {
        SearchNode node = SearchQueryParser.parse("");
        assertTrue(node instanceof TermNode);
        assertEquals("", ((TermNode) node).term());
    }

    @Test
    public void testParse_null() {
        SearchNode node = SearchQueryParser.parse(null);
        assertTrue(node instanceof TermNode);
        assertEquals("", ((TermNode) node).term());
    }

    @Test
    public void testParse_multipleOr() {
        // java | python | ruby  → 左结合：((java | python) | ruby)
        SearchNode node = SearchQueryParser.parse("java | python | ruby");
        assertTrue(node instanceof OrNode);
        OrNode or = (OrNode) node;
        // 左边是 OrNode(java, python)，右边是 TermNode("ruby")
        assertTrue(or.left() instanceof OrNode);
        assertTrue(or.right() instanceof TermNode);
        assertEquals("ruby", ((TermNode) or.right()).term());
    }

    @Test
    public void testParse_multipleAnd() {
        // java & python & android
        SearchNode node = SearchQueryParser.parse("java & python & android");
        assertTrue(node instanceof AndNode);
        AndNode and = (AndNode) node;
        assertTrue(and.left() instanceof AndNode);
        assertTrue(and.right() instanceof TermNode);
    }

    // ========== 匹配测试 ==========

    private LinkItem makeItem(String title, String url, String... tags) {
        LinkItem item = new LinkItem(title, url, "test", "", "");
        item.setTags(Arrays.asList(tags));
        return item;
    }

    @Test
    public void testMatch_term_inTitle() {
        SearchNode node = SearchQueryParser.parse("java");
        LinkItem item = makeItem("Learn Java today", "https://example.com", "programming");
        assertTrue(SearchQueryParser.matches(node, item));
    }

    @Test
    public void testMatch_term_inUrl() {
        SearchNode node = SearchQueryParser.parse("example");
        LinkItem item = makeItem("Learn Python", "https://example.com", "programming");
        assertTrue(SearchQueryParser.matches(node, item));
    }

    @Test
    public void testMatch_term_inTags() {
        SearchNode node = SearchQueryParser.parse("programming");
        LinkItem item = makeItem("Learn Python", "https://example.com", "programming", "tech");
        assertTrue(SearchQueryParser.matches(node, item));
    }

    @Test
    public void testMatch_term_notFound() {
        SearchNode node = SearchQueryParser.parse("ruby");
        LinkItem item = makeItem("Learn Java", "https://example.com", "programming");
        assertFalse(SearchQueryParser.matches(node, item));
    }

    @Test
    public void testMatch_and_bothMatch() {
        SearchNode node = SearchQueryParser.parse("java & python");
        LinkItem item = makeItem("Learn Java and Python", "https://example.com", "programming");
        assertTrue(SearchQueryParser.matches(node, item));
    }

    @Test
    public void testMatch_and_oneMissing() {
        SearchNode node = SearchQueryParser.parse("java & python");
        LinkItem item = makeItem("Learn Java", "https://example.com", "programming");
        assertFalse(SearchQueryParser.matches(node, item));
    }

    @Test
    public void testMatch_or_oneMatches() {
        SearchNode node = SearchQueryParser.parse("java | ruby");
        LinkItem item = makeItem("Learn Java", "https://example.com", "programming");
        assertTrue(SearchQueryParser.matches(node, item));
    }

    @Test
    public void testMatch_or_noneMatches() {
        SearchNode node = SearchQueryParser.parse("java | ruby");
        LinkItem item = makeItem("Learn Python", "https://example.com", "programming");
        assertFalse(SearchQueryParser.matches(node, item));
    }

    @Test
    public void testMatch_not() {
        SearchNode node = SearchQueryParser.parse("!ruby");
        LinkItem item = makeItem("Learn Java", "https://example.com", "programming");
        assertTrue(SearchQueryParser.matches(node, item));  // 不包含 ruby，所以 NOT 命中
    }

    @Test
    public void testMatch_not_false() {
        SearchNode node = SearchQueryParser.parse("!java");
        LinkItem item = makeItem("Learn Java", "https://example.com", "programming");
        assertFalse(SearchQueryParser.matches(node, item));  // 包含 java，所以 NOT 拒绝
    }

    @Test
    public void testMatch_phrase_exact() {
        SearchNode node = SearchQueryParser.parse("\"hello world\"");
        LinkItem item = makeItem("Say hello world today", "https://example.com", "greeting");
        assertTrue(SearchQueryParser.matches(node, item));
    }

    @Test
    public void testMatch_phrase_partial() {
        SearchNode node = SearchQueryParser.parse("\"hello world\"");
        LinkItem item = makeItem("Say hello", "https://example.com", "greeting");
        assertFalse(SearchQueryParser.matches(node, item));
    }

    @Test
    public void testMatch_complexOrAnd() {
        // (java | python) & programming
        SearchNode node = SearchQueryParser.parse("(java | python) & programming");
        LinkItem item1 = makeItem("Learn Java", "https://example.com", "programming");
        LinkItem item2 = makeItem("Learn Python", "https://example.com", "tech");
        LinkItem item3 = makeItem("Learn Java", "https://example.com", "tech");
        LinkItem item4 = makeItem("Learn Ruby", "https://example.com", "programming");

        assertTrue(SearchQueryParser.matches(node, item1));  // java & programming ✓
        assertFalse(SearchQueryParser.matches(node, item2)); // python & programming ✗
        assertFalse(SearchQueryParser.matches(node, item3)); // java & programming ✗
        assertFalse(SearchQueryParser.matches(node, item4)); // java & programming ✗
    }

    @Test
    public void testMatch_caseInsensitive() {
        SearchNode node = SearchQueryParser.parse("JAVA");
        LinkItem item = makeItem("Learn java today", "https://EXAMPLE.com", "PROGRAMMING");
        assertTrue(SearchQueryParser.matches(node, item));
    }

    @Test
    public void testMatch_emptyQuery() {
        SearchNode node = SearchQueryParser.parse("");
        LinkItem item = makeItem("Any", "https://example.com", "any");
        // 空查询应该匹配所有
        assertTrue(SearchQueryParser.matches(node, item));
    }
}
