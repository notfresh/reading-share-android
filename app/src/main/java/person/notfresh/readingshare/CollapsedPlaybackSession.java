package person.notfresh.readingshare;

/**
 * Process-wide owner of the single page allowed to keep playing while collapsed.
 */
public final class CollapsedPlaybackSession {

    public interface PlaybackController {
        void play();
        void pause();
        void stop();
    }

    private static final CollapsedPlaybackSession INSTANCE = new CollapsedPlaybackSession();

    private int ownerTaskId = -1;
    private String ownerUrl;
    private PlaybackController ownerController;

    private CollapsedPlaybackSession() {
    }

    public static CollapsedPlaybackSession getInstance() {
        return INSTANCE;
    }

    public synchronized boolean activate(int taskId, String url, PlaybackController controller) {
        if (taskId < 0 || url == null || url.isEmpty() || controller == null) {
            return false;
        }
        ownerTaskId = taskId;
        ownerUrl = url;
        ownerController = controller;
        return true;
    }

    public synchronized boolean isActive() {
        return ownerController != null;
    }

    public synchronized int getOwnerTaskId() {
        return ownerTaskId;
    }

    public synchronized String getOwnerUrl() {
        return ownerUrl;
    }

    public synchronized PlaybackController getOwnerController() {
        return ownerController;
    }

    public synchronized void clear() {
        ownerTaskId = -1;
        ownerUrl = null;
        ownerController = null;
    }
}