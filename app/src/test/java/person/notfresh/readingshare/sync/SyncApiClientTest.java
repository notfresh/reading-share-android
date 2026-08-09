package person.notfresh.readingshare.sync;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SyncApiClientTest {
    @Test
    public void shouldNotEncryptLocalAndLanAddresses() {
        assertFalse(new SyncApiClient("http://localhost:8080", "key").shouldEncrypt());
        assertFalse(new SyncApiClient("http://127.0.0.1", "key").shouldEncrypt());
        assertFalse(new SyncApiClient("http://192.168.1.10", "key").shouldEncrypt());
    }

    @Test
    public void shouldEncryptRemoteAndMalformedAddresses() {
        assertTrue(new SyncApiClient("https://sync.example.com", "key").shouldEncrypt());
        assertTrue(new SyncApiClient("not-a-url", "key").shouldEncrypt());
    }
}
