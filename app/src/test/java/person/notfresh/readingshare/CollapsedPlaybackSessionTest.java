package person.notfresh.readingshare;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class CollapsedPlaybackSessionTest {

    private final CollapsedPlaybackSession session = CollapsedPlaybackSession.getInstance();

    @After
    public void clearSession() {
        session.clear();
    }

    @Test
    public void activate_exposesTheCollapsedPageOwner() {
        CollapsedPlaybackSession.PlaybackController controller = new NoOpController();

        assertTrue(session.activate(42, "https://example.test/a", controller));

        assertTrue(session.isActive());
        assertEquals(42, session.getOwnerTaskId());
        assertEquals("https://example.test/a", session.getOwnerUrl());
        assertSame(controller, session.getOwnerController());
    }

    @Test
    public void activate_replacesThePreviousCollapsedPage() {
        session.activate(42, "https://example.test/a", new NoOpController());
        CollapsedPlaybackSession.PlaybackController controller = new NoOpController();

        assertTrue(session.activate(99, "https://example.test/b", controller));

        assertEquals(99, session.getOwnerTaskId());
        assertEquals("https://example.test/b", session.getOwnerUrl());
        assertSame(controller, session.getOwnerController());
    }

    @Test
    public void clear_removesTheCollapsedPageOwner() {
        session.activate(42, "https://example.test/a", new NoOpController());

        session.clear();

        assertFalse(session.isActive());
        assertEquals(-1, session.getOwnerTaskId());
    }

    private static final class NoOpController implements CollapsedPlaybackSession.PlaybackController {
        @Override
        public void play() {}

        @Override
        public void pause() {}

        @Override
        public void stop() {}
    }
}