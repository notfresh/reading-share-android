package person.notfresh.readingshare.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ExportUtilDatabaseCopyTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void copyFile_copiesAllBytes() throws IOException {
        byte[] payload = "hello sqlite".getBytes(StandardCharsets.UTF_8);
        File src = tmp.newFile("src.db");
        try (OutputStream os = new FileOutputStream(src)) {
            os.write(payload);
        }
        File dst = new File(tmp.getRoot(), "dst.db");

        ExportUtil.copyFile(src, dst);

        assertArrayEquals(payload, Files.readAllBytes(dst.toPath()));
    }

    @Test
    public void copyFile_overwritesExisting() throws IOException {
        File src = tmp.newFile("src.db");
        try (OutputStream os = new FileOutputStream(src)) {
            os.write("new".getBytes(StandardCharsets.UTF_8));
        }
        File dst = tmp.newFile("dst.db");
        try (OutputStream os = new FileOutputStream(dst)) {
            os.write("old-old-old".getBytes(StandardCharsets.UTF_8));
        }

        ExportUtil.copyFile(src, dst);

        assertArrayEquals(
            "new".getBytes(StandardCharsets.UTF_8),
            Files.readAllBytes(dst.toPath())
        );
    }

    @Test
    public void copyFile_sourceMissingThrows() throws IOException {
        File src = new File(tmp.getRoot(), "does-not-exist.db");
        File dst = new File(tmp.getRoot(), "dst.db");

        try {
            ExportUtil.copyFile(src, dst);
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue("message should mention source file: " + e.getMessage(),
                e.getMessage() != null && e.getMessage().contains(src.getName()));
        }
    }
}