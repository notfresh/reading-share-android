# Single Collapsed Playback Session Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep one collapsed WebView page playing in the background while independent ordinary WebView pages can open and close without affecting it.

**Architecture:** Introduce a process-wide `CollapsedPlaybackSession` that owns the collapsed page's task ID, URL and media callback. `WebViewBackgroundService` uses only that session for mini and notification controls. A normal `WebViewActivity` instance remains local: it cannot overwrite, stop, or clear the active collapsed session.

**Tech Stack:** Java, AndroidX, `WebView`, `MediaSessionCompat`, foreground service, JUnit 4.

## Global Constraints

- Only one collapsed playback session may exist.
- The collapsed WebView must remain attached to its original Activity window; never call `removeView`, `destroy`, or reload it during collapse.
- Every `WebViewActivity` uses `android:documentLaunchMode="always"` so ordinary pages and the collapsed page have separate tasks from creation.
- A normal WebView page must not stop `WebViewBackgroundService`, clear its callback, remove mini, or change the collapsed task ID while a session is active.
- Fold a new page only after explicitly replacing the existing session.

---

### Task 1: Add a pure collapsed-session owner

**Files:**
- Create: `app/src/main/java/person/notfresh/readingshare/CollapsedPlaybackSession.java`
- Create: `app/src/test/java/person/notfresh/readingshare/CollapsedPlaybackSessionTest.java`

**Interfaces:**
- Produces: `CollapsedPlaybackSession.getInstance()`.
- Produces: `boolean activate(int taskId, String url, WebViewBackgroundService.MediaCallback callback)`.
- Produces: `boolean isActive()`, `int getOwnerTaskId()`, `String getOwnerUrl()`, `MediaCallback getOwnerCallback()`, `void clear()`.
- Consumes: `WebViewBackgroundService.MediaCallback`.

- [ ] **Step 1: Write the failing tests**

```java
@Test
public void activateMakesOwnerStateAvailable() {
    CollapsedPlaybackSession session = CollapsedPlaybackSession.getInstance();
    session.clear();
    WebViewBackgroundService.MediaCallback callback = new NoOpCallback();

    assertTrue(session.activate(42, "https://example.test/a", callback));
    assertTrue(session.isActive());
    assertEquals(42, session.getOwnerTaskId());
    assertEquals("https://example.test/a", session.getOwnerUrl());
    assertSame(callback, session.getOwnerCallback());
}

@Test
public void secondActivationReplacesTheOnlyOwner() {
    CollapsedPlaybackSession session = CollapsedPlaybackSession.getInstance();
    session.clear();
    session.activate(42, "https://example.test/a", new NoOpCallback());
    WebViewBackgroundService.MediaCallback callback = new NoOpCallback();

    assertTrue(session.activate(99, "https://example.test/b", callback));
    assertEquals(99, session.getOwnerTaskId());
    assertEquals("https://example.test/b", session.getOwnerUrl());
    assertSame(callback, session.getOwnerCallback());
}

@Test
public void clearRemovesAllOwnerState() {
    CollapsedPlaybackSession session = CollapsedPlaybackSession.getInstance();
    session.activate(42, "https://example.test/a", new NoOpCallback());

    session.clear();

    assertFalse(session.isActive());
    assertEquals(-1, session.getOwnerTaskId());
    assertNull(session.getOwnerUrl());
    assertNull(session.getOwnerCallback());
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `gradlew.bat :app:testDebugUnitTest --tests person.notfresh.readingshare.CollapsedPlaybackSessionTest`

Expected: FAIL because `CollapsedPlaybackSession` does not exist.

- [ ] **Step 3: Implement the minimal session class**

```java
public final class CollapsedPlaybackSession {
    private static final CollapsedPlaybackSession INSTANCE = new CollapsedPlaybackSession();
    private int ownerTaskId = -1;
    private String ownerUrl;
    private WebViewBackgroundService.MediaCallback ownerCallback;

    public static CollapsedPlaybackSession getInstance() { return INSTANCE; }

    public synchronized boolean activate(int taskId, String url,
            WebViewBackgroundService.MediaCallback callback) {
        if (taskId < 0 || url == null || url.isEmpty() || callback == null) return false;
        ownerTaskId = taskId;
        ownerUrl = url;
        ownerCallback = callback;
        return true;
    }

    public synchronized boolean isActive() { return ownerCallback != null; }
    public synchronized int getOwnerTaskId() { return ownerTaskId; }
    public synchronized String getOwnerUrl() { return ownerUrl; }
    public synchronized WebViewBackgroundService.MediaCallback getOwnerCallback() { return ownerCallback; }
    public synchronized void clear() { ownerTaskId = -1; ownerUrl = null; ownerCallback = null; }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `gradlew.bat :app:testDebugUnitTest --tests person.notfresh.readingshare.CollapsedPlaybackSessionTest`

Expected: PASS.

### Task 2: Bind Service and mini to the session owner only

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/WebViewBackgroundService.java`
- Test: `app/src/test/java/person/notfresh/readingshare/CollapsedPlaybackSessionTest.java`

**Interfaces:**
- Consumes: `CollapsedPlaybackSession.isActive()`, `getOwnerTaskId()`, `getOwnerUrl()`, `getOwnerCallback()`.
- Produces: `static boolean hasCollapsedSession()` delegating to session state.

- [ ] **Step 1: Write the failing test for session replacement state**

```java
@Test
public void replacementLeavesOnlyTheLatestCallbackReachable() {
    CollapsedPlaybackSession session = CollapsedPlaybackSession.getInstance();
    session.clear();
    WebViewBackgroundService.MediaCallback first = new NoOpCallback();
    WebViewBackgroundService.MediaCallback second = new NoOpCallback();
    session.activate(1, "https://example.test/a", first);

    session.activate(2, "https://example.test/b", second);

    assertSame(second, session.getOwnerCallback());
    assertNotSame(first, session.getOwnerCallback());
}
```

- [ ] **Step 2: Run the focused test**

Run: `gradlew.bat :app:testDebugUnitTest --tests person.notfresh.readingshare.CollapsedPlaybackSessionTest`

Expected: PASS after Task 1; the test protects the owner-replacement contract before Service integration.

- [ ] **Step 3: Replace Service global ownership fields with session reads**

```java
public static boolean hasCollapsedSession() {
    return CollapsedPlaybackSession.getInstance().isActive();
}

private WebViewBackgroundService.MediaCallback getPlaybackCallback() {
    return CollapsedPlaybackSession.getInstance().getOwnerCallback();
}
```

Use `getPlaybackCallback()` for `ACTION_PLAY_PAUSE` and `ACTION_STOP`. In `ACTION_SHOW_MINI`, read URL and task ID from `CollapsedPlaybackSession`, not Intent extras. In `handleMiniClick`, call `moveTaskToFront(session.getOwnerTaskId(), 0)` and use `session.getOwnerUrl()` only as the task-recovery fallback. Remove `currentUrl`, `mCollapsedTaskId`, `sMediaCallback`, `setMediaCallback`, `sMiniShowing`, and `hasMini` after all callers migrate.

- [ ] **Step 4: Run unit tests and compile**

Run: `gradlew.bat :app:testDebugUnitTest --tests person.notfresh.readingshare.CollapsedPlaybackSessionTest :app:assembleDebug`

Expected: unit tests PASS and `BUILD SUCCESSFUL`.

### Task 3: Register only the collapsed Activity as the playback owner

**Files:**
- Modify: `app/src/main/java/person/notfresh/readingshare/WebViewActivity.java`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `CollapsedPlaybackSession.activate(int, String, MediaCallback)`, `isActive()`, `clear()`.
- Produces: an Activity-local `MediaCallback mediaCallback` created by `initAudio()`.

- [ ] **Step 1: Write a manual lifecycle acceptance list in the implementation comments**

Add the following comment immediately above the collapsed-session registration method:

```java
// Owner rule: ordinary pages never register or clear this session. Only the page
// currently being collapsed can activate it; only that owner returning from mini
// or an explicit replacement can clear it.
```

- [ ] **Step 2: Make `initAudio()` create a local callback without registering it globally**

```java
mediaCallback = new WebViewBackgroundService.MediaCallback() {
    @Override public void onPlayRequested() { runOnUiThread(() -> playCurrentMedia()); }
    @Override public void onPauseRequested() { runOnUiThread(() -> pauseCurrentMedia()); }
    @Override public void onStopRequested() { runOnUiThread(() -> stopCurrentMedia()); }
};
```

Add `private WebViewBackgroundService.MediaCallback mediaCallback;` and private helpers `playCurrentMedia`, `pauseCurrentMedia`, and `stopCurrentMedia` that evaluate the existing JavaScript hooks only when `webView != null`.

- [ ] **Step 3: Change collapse to explicitly activate the session before starting Service**

```java
private void collapseToMiniPlayer() {
    // Keep existing permission and null checks.
    CollapsedPlaybackSession session = CollapsedPlaybackSession.getInstance();
    if (session.isActive() && session.getOwnerCallback() != mediaCallback) {
        session.getOwnerCallback().onStopRequested();
        stopService(new Intent(this, WebViewBackgroundService.class));
        session.clear();
    }
    isCollapsedToMini = session.activate(getTaskId(), currentUrl, mediaCallback);
    Intent serviceIntent = new Intent(this, WebViewBackgroundService.class)
            .setAction(WebViewBackgroundService.ACTION_SHOW_MINI);
    startForegroundService(serviceIntent);
    moveTaskToBack(true);
}
```

Replace the current `REORDER_TO_FRONT` MainActivity behavior with `moveTaskToBack(true)`: with `documentLaunchMode="always"`, this backgrounds only A's own task and reveals the previously visible homepage task.

- [ ] **Step 4: Guard ordinary Activity lifecycle methods**

In `onPause`, start no Service for an ordinary page when `CollapsedPlaybackSession.getInstance().isActive()` is true. In `onResume` and `onDestroy`, stop Service or clear session only when this instance owns it:

```java
private boolean ownsCollapsedSession() {
    CollapsedPlaybackSession session = CollapsedPlaybackSession.getInstance();
    return session.isActive()
            && session.getOwnerTaskId() == getTaskId()
            && session.getOwnerCallback() == mediaCallback;
}
```

When the owner returns to foreground after mini click, call `session.clear()` and then stop the Service. When a non-owner X exits, release only X-local WebView/audio resources; do not stop Service or clear session.

- [ ] **Step 5: Preserve document-task behavior**

Keep this manifest configuration:

```xml
<activity
    android:name=".WebViewActivity"
    android:documentLaunchMode="always" />
```

Do not add `FLAG_ACTIVITY_NEW_DOCUMENT` to normal page intents; the manifest owns this policy.

- [ ] **Step 6: Compile the touched Android slice**

Run: `gradlew.bat :app:assembleDebug`

Expected: `BUILD SUCCESSFUL`.

### Task 4: Deploy and verify the owner-isolation workflow

**Files:**
- Modify: `docs/superpowers/specs/2026-09-19-single-collapsed-playback-session-design.md`

**Interfaces:**
- Consumes: completed session, Service, and Activity lifecycle changes.

- [ ] **Step 1: Install the Debug APK**

Run: `adb install -r app\\build\\outputs\\apk\\debug\\app-debug.apk`

Expected: `Success`.

- [ ] **Step 2: Run the manual owner-isolation acceptance test**

1. Open A, begin audio, and collapse it.
2. Verify mini remains visible and A audio continues after the A task backgrounds.
3. From the homepage open X, then close X.
4. Verify A audio and mini never stop or disappear.
5. Tap mini and verify A, not X, returns with its existing WebView state.
6. Open B, collapse B, then verify A is stopped and mini restores B only.
7. Verify notification play/pause controls A while A is the active collapsed session.

- [ ] **Step 3: Record verification status in the design document**

Change the design status to `已验证` only when every manual test above passes; otherwise record the failed numbered case and leave status as `待验证`.