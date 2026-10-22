package person.notfresh.readingshare.core.reader;

import org.junit.Test;

import java.util.Optional;

import person.notfresh.readingshare.model.OverrideAction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** UrlRoute 的判定契约：与 WebViewActivity.handleUrlOverride 的三档语义逐条对齐。 */
public class UrlRouteTest {

    @Test
    public void httpUrlsAlwaysLoadInWebView() {
        assertEquals(UrlRoute.Route.LOAD_IN_WEBVIEW,
                UrlRoute.decide("https://example.com/a", Optional.empty(), 2));
        assertEquals(UrlRoute.Route.LOAD_IN_WEBVIEW,
                UrlRoute.decide("http://example.com/a", Optional.of(OverrideAction.BLOCK), 0));
    }

    @Test
    public void whitelistActionOverridesGlobalMode() {
        assertEquals(UrlRoute.Route.OPEN_EXTERNAL,
                UrlRoute.decide("xhsdiscover://item/1", Optional.of(OverrideAction.OPEN), 2));
        assertEquals(UrlRoute.Route.ASK_USER,
                UrlRoute.decide("xhsdiscover://item/1", Optional.of(OverrideAction.ASK), 2));
        assertEquals(UrlRoute.Route.BLOCK,
                UrlRoute.decide("xhsdiscover://item/1", Optional.of(OverrideAction.BLOCK), 1));
    }

    @Test
    public void globalModeDecidesWhenNoRuleHit() {
        String url = "weixin://dl/business";
        assertEquals(UrlRoute.Route.ASK_USER, UrlRoute.decide(url, Optional.empty(), 0));
        assertEquals(UrlRoute.Route.OPEN_EXTERNAL, UrlRoute.decide(url, Optional.empty(), 1));
        assertEquals(UrlRoute.Route.BLOCK, UrlRoute.decide(url, Optional.empty(), 2));
        assertEquals("未知模式按拦截处理", UrlRoute.Route.BLOCK, UrlRoute.decide(url, Optional.empty(), 99));
    }

    @Test
    public void nullOrEmptyUrlBlocks() {
        assertEquals(UrlRoute.Route.BLOCK, UrlRoute.decide(null, Optional.empty(), 1));
        assertEquals(UrlRoute.Route.BLOCK, UrlRoute.decide("", Optional.empty(), 1));
    }

    @Test
    public void isHttpRecognizesOnlyHttpSchemes() {
        assertTrue(UrlRoute.isHttp("http://a.com"));
        assertTrue(UrlRoute.isHttp("https://a.com"));
        assertFalse(UrlRoute.isHttp("xhsdiscover://a"));
        assertFalse(UrlRoute.isHttp(null));
    }
}
