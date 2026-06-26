package person.notfresh.readingshare.util;

import java.util.ArrayList;
import java.util.List;

import person.notfresh.readingshare.model.LinkItem;

/**
 * 搜索语法解析器
 *
 * 支持语法：
 * - term1 term2      → 默认 AND
 * - term1 & term2    → AND
 * - term1 | term2    → OR
 * - !term            → NOT
 * - "exact phrase"   → 短语精确匹配
 * - (group)          → 分组
 *
 * 优先级：NOT > AND > OR
 */
public class SearchQueryParser {

    // ========== Token 类型 ==========

    private enum TokenType {
        TERM,       // 普通词条
        PHRASE,     // 精确短语 "..."
        AND,        // &
        OR,         // |
        NOT,        // !
        LPAREN,     // (
        RPAREN,     // )
        END         // 结束符
    }

    private static class Token {
        final TokenType type;
        final String value;

        Token(TokenType type, String value) {
            this.type = type;
            this.value = value;
        }

        @Override
        public String toString() {
            return "Token{" + type + ", \"" + value + "\"}";
        }
    }

    // ========== AST 节点 ==========

    public interface SearchNode {
        boolean matches(LinkItem item);
    }

    public static class TermNode implements SearchNode {
        private final String term;

        public TermNode(String term) {
            this.term = term;
        }

        public String term() {
            return term;
        }

        @Override
        public boolean matches(LinkItem item) {
            String t = term.toLowerCase();
            return containsIgnoreCase(item.getTitle(), t)
                    || containsIgnoreCase(item.getUrl(), t)
                    || containsAnyTag(item, t);
        }
    }

    public static class PhraseNode implements SearchNode {
        private final String phrase;

        public PhraseNode(String phrase) {
            this.phrase = phrase;
        }

        public String phrase() {
            return phrase;
        }

        @Override
        public boolean matches(LinkItem item) {
            String p = phrase.toLowerCase();
            return containsIgnoreCase(item.getTitle(), p)
                    || containsIgnoreCase(item.getUrl(), p)
                    || containsPhraseInTags(item, p);
        }
    }

    public static class NotNode implements SearchNode {
        private final SearchNode child;

        public NotNode(SearchNode child) {
            this.child = child;
        }

        public SearchNode child() {
            return child;
        }

        @Override
        public boolean matches(LinkItem item) {
            return !child.matches(item);
        }
    }

    public static class AndNode implements SearchNode {
        private final SearchNode left;
        private final SearchNode right;

        public AndNode(SearchNode left, SearchNode right) {
            this.left = left;
            this.right = right;
        }

        public SearchNode left() {
            return left;
        }

        public SearchNode right() {
            return right;
        }

        @Override
        public boolean matches(LinkItem item) {
            return left.matches(item) && right.matches(item);
        }
    }

    public static class OrNode implements SearchNode {
        private final SearchNode left;
        private final SearchNode right;

        public OrNode(SearchNode left, SearchNode right) {
            this.left = left;
            this.right = right;
        }

        public SearchNode left() {
            return left;
        }

        public SearchNode right() {
            return right;
        }

        @Override
        public boolean matches(LinkItem item) {
            return left.matches(item) || right.matches(item);
        }
    }

    // ========== 词法分析器 ==========

    private final List<Token> tokens = new ArrayList<>();
    private int pos = 0;

    private void tokenize(String query) {
        tokens.clear();
        pos = 0;

        int i = 0;
        while (i < query.length()) {
            char c = query.charAt(i);

            // 跳过空格
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            // 处理 AND 符号 &
            if (c == '&') {
                tokens.add(new Token(TokenType.AND, "AND"));
                i++;
                continue;
            }

            // 处理 OR 符号 |
            if (c == '|') {
                tokens.add(new Token(TokenType.OR, "OR"));
                i++;
                continue;
            }

            // 处理 NOT 符号 !
            if (c == '!') {
                tokens.add(new Token(TokenType.NOT, "NOT"));
                i++;
                continue;
            }

            // 处理左括号
            if (c == '(') {
                tokens.add(new Token(TokenType.LPAREN, "("));
                i++;
                continue;
            }

            // 处理右括号
            if (c == ')') {
                tokens.add(new Token(TokenType.RPAREN, ")"));
                i++;
                continue;
            }

            // 处理引号包围的短语
            if (c == '"') {
                int start = i + 1;
                int end = start;
                while (end < query.length() && query.charAt(end) != '"') {
                    end++;
                }
                String phrase = query.substring(start, end);
                if (!phrase.isEmpty()) {
                    tokens.add(new Token(TokenType.PHRASE, phrase));
                }
                i = (end < query.length()) ? end + 1 : query.length();
                continue;
            }

            // 处理普通词条
            int wordStart = i;
            while (i < query.length()) {
                char ch = query.charAt(i);
                if (Character.isWhitespace(ch) || ch == '&' || ch == '|' || ch == '!' || ch == '(' || ch == ')' || ch == '"') {
                    break;
                }
                i++;
            }
            String word = query.substring(wordStart, i);

            // 检查是否是操作符关键字（必须全词匹配，大小写敏感）
            if ("AND".equals(word.toUpperCase())) {
                tokens.add(new Token(TokenType.AND, "AND"));
            } else if ("OR".equals(word.toUpperCase())) {
                tokens.add(new Token(TokenType.OR, "OR"));
            } else if ("NOT".equals(word.toUpperCase())) {
                tokens.add(new Token(TokenType.NOT, "NOT"));
            } else if (!word.isEmpty()) {
                tokens.add(new Token(TokenType.TERM, word));
            }
        }

        tokens.add(new Token(TokenType.END, ""));
    }

    // ========== 语法分析器（递归下降）============

    private Token current() {
        if (pos < tokens.size()) {
            return tokens.get(pos);
        }
        return new Token(TokenType.END, "");
    }

    private Token consume() {
        Token t = current();
        pos++;
        return t;
    }

    private TokenType peek() {
        return current().type;
    }

    private boolean check(TokenType type) {
        return peek() == type;
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                consume();
                return true;
            }
        }
        return false;
    }

    /**
     * 语法分析入口
     * 优先级：OR > AND > NOT > 原子
     *
     * orExpr → andExpr (OR andExpr)*
     * andExpr → notExpr ((AND | SP) notExpr)*   ← 空格也作为隐式 AND
     * notExpr → NOT? atom
     * atom → TERM | PHRASE | LPAREN orExpr RPAREN
     */
    private SearchNode parseOrExpr() {
        SearchNode left = parseAndExpr();

        while (check(TokenType.OR)) {
            consume();  // 消耗 OR
            SearchNode right = parseAndExpr();
            left = new OrNode(left, right);
        }

        return left;
    }

    private SearchNode parseAndExpr() {
        SearchNode left = parseNotExpr();

        while (check(TokenType.AND) || check(TokenType.TERM) || check(TokenType.PHRASE) || check(TokenType.LPAREN)) {
            // AND token - 显式 AND
            if (check(TokenType.AND)) {
                consume();
                SearchNode right = parseNotExpr();
                left = new AndNode(left, right);
                continue;
            }
            // 隐式 AND：TERM | PHRASE | LPAREN 直接相邻（空格分隔的情况）
            if (check(TokenType.TERM) || check(TokenType.PHRASE) || check(TokenType.LPAREN)) {
                SearchNode right = parseNotExpr();
                left = new AndNode(left, right);
                continue;
            }
            break;
        }

        return left;
    }

    private SearchNode parseNotExpr() {
        if (check(TokenType.NOT)) {
            consume();  // 消耗 NOT
            SearchNode child = parseAtom();
            return new NotNode(child);
        }
        return parseAtom();
    }

    private SearchNode parseAtom() {
        Token t = current();

        if (check(TokenType.TERM)) {
            consume();
            return new TermNode(t.value);
        }

        if (check(TokenType.PHRASE)) {
            consume();
            return new PhraseNode(t.value);
        }

        if (check(TokenType.LPAREN)) {
            consume();  // 消耗 (
            SearchNode node = parseOrExpr();
            if (check(TokenType.RPAREN)) {
                consume();  // 消耗 )
            }
            return node;
        }

        // 未知 token，默认作为 TERM
        consume();
        return new TermNode(t.value);
    }

    // ========== 公共接口 ==========

    /**
     * 解析搜索词串，返回 AST 根节点
     */
    public static SearchNode parse(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new TermNode("");
        }

        SearchQueryParser parser = new SearchQueryParser();
        parser.tokenize(query.trim());

        if (parser.tokens.isEmpty()) {
            return new TermNode("");
        }

        SearchNode node = parser.parseOrExpr();
        return node;
    }

    /**
     * 匹配 LinkItem
     */
    public static boolean matches(SearchNode node, LinkItem item) {
        if (node == null) return true;
        return node.matches(item);
    }

    // ========== 辅助方法 ==========

    private static boolean containsIgnoreCase(String text, String search) {
        if (text == null || search == null) return false;
        return text.toLowerCase().contains(search);
    }

    private static boolean containsAnyTag(LinkItem item, String search) {
        for (String tag : item.getTags()) {
            if (tag.toLowerCase().contains(search)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsPhraseInTags(LinkItem item, String phrase) {
        for (String tag : item.getTags()) {
            if (tag.toLowerCase().contains(phrase)) {
                return true;
            }
        }
        return false;
    }
}
