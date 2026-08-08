package person.notfresh.readingshare.sync;

import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class SyncApiClient {
    private static final String TAG = "SyncApiClient";
    private static final String AUTH_TOKEN = "DUXIANG";

    private final String serverUrl;
    private final String secretKey;

    public SyncApiClient(String serverUrl, String secretKey) {
        this.serverUrl = serverUrl;
        this.secretKey = secretKey;
    }

    public boolean shouldEncrypt() {
        // TODO: 临时返回 false 方便测试，生产环境需要启用
        return false;

        // 原来的局域网判断逻辑：
        // try {
        //     String host = new URL(serverUrl).getHost();
        //     return !(host.equals("localhost")
        //             || host.equals("127.0.0.1")
        //             || host.startsWith("192.168."));
        // } catch (Exception e) {
        //     return true;
        // }
    }

    private byte[] deriveKey(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(password.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.e(TAG, "密钥派生失败", e);
            return null;
        }
    }

    private String encryptAES(String plaintext) {
        try {
            byte[] key = deriveKey(secretKey);
            if (key == null) {
                return null;
            }

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "加密失败", e);
            return null;
        }
    }

    private String decryptAES(String encryptedData) {
        try {
            byte[] key = deriveKey(secretKey);
            if (key == null) {
                return null;
            }

            byte[] combined = Base64.decode(encryptedData, Base64.NO_WRAP);
            if (combined.length <= 16) {
                Log.e(TAG, "加密数据长度无效");
                return null;
            }

            byte[] iv = new byte[16];
            byte[] encrypted = new byte[combined.length - iv.length];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new IvParameterSpec(iv));
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "解密失败", e);
            return null;
        }
    }

    public String sync(String requestJson) {
        HttpURLConnection connection = null;
        try {
            String normalizedServerUrl = serverUrl.endsWith("/")
                    ? serverUrl.substring(0, serverUrl.length() - 1)
                    : serverUrl;
            URL url = new URL(normalizedServerUrl + "/api/sync");
            Log.d(TAG, "同步请求 URL: " + url);
            Log.d(TAG, "请求体: " + requestJson);

            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setDoOutput(true);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);

            boolean encrypt = shouldEncrypt();
            Log.d(TAG, "shouldEncrypt: " + encrypt);

            String bodyJson = requestJson;

            // 根据 shouldEncrypt 决定是否加密
            if (encrypt) {
                // 加密 Authorization 头
                String encryptedToken = encryptAES(AUTH_TOKEN);
                if (encryptedToken == null) {
                    Log.e(TAG, "加密 Authorization 头失败");
                    return null;
                }
                Log.d(TAG, "Authorization 头 (加密): Bearer " + encryptedToken);
                connection.setRequestProperty("Authorization", "Bearer " + encryptedToken);

                // 加密请求体
                String encryptedBody = encryptAES(requestJson);
                if (encryptedBody == null) {
                    Log.e(TAG, "加密请求体失败");
                    return null;
                }
                JSONObject encrypted = new JSONObject();
                encrypted.put("data", encryptedBody);
                JSONObject body = new JSONObject();
                body.put("encrypted", encrypted);
                bodyJson = body.toString();
            } else {
                // 不加密：明文发送
                Log.d(TAG, "Authorization 头 (明文): Bearer " + AUTH_TOKEN);
                connection.setRequestProperty("Authorization", "Bearer " + AUTH_TOKEN);
            }

            Log.d(TAG, "发送请求...");
            try (OutputStream output = connection.getOutputStream()) {
                byte[] input = bodyJson.getBytes(StandardCharsets.UTF_8);
                output.write(input);
                output.flush();
            }
            Log.d(TAG, "请求已发送，等待响应...");

            int responseCode = connection.getResponseCode();
            Log.d(TAG, "同步响应码: " + responseCode);
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "同步失败: " + responseCode);
                return null;
            }

            String response = readResponse(connection);
            Log.d(TAG, "响应体: " + response);
            return encrypt ? decryptResponse(response) : response;
        } catch (Exception e) {
            Log.e(TAG, "同步请求失败", e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readResponse(HttpURLConnection connection) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        } catch (Exception e) {
            Log.e(TAG, "读取响应失败", e);
            return null;
        }
    }

    private String decryptResponse(String response) {
        if (response == null) {
            return null;
        }

        try {
            JSONObject object = new JSONObject(response);
            if (object.has("encrypted")) {
                JSONObject encrypted = object.getJSONObject("encrypted");
                return decryptAES(encrypted.getString("data"));
            }
            return response;
        } catch (Exception e) {
            Log.e(TAG, "解析加密响应失败", e);
            return null;
        }
    }
}
