package person.notfresh.readingshare.core;

/**
 * 同步操作的结果
 */
public class SyncResult {
    private boolean success;
    private String errorMessage;
    private String responseData;
    private int responseCode;

    private SyncResult(boolean success, String errorMessage, String responseData, int responseCode) {
        this.success = success;
        this.errorMessage = errorMessage;
        this.responseData = responseData;
        this.responseCode = responseCode;
    }

    /**
     * 创建成功的结果
     */
    public static SyncResult success(String responseData) {
        return new SyncResult(true, null, responseData, 200);
    }

    /**
     * 创建成功的结果（带响应码）
     */
    public static SyncResult success(String responseData, int responseCode) {
        return new SyncResult(true, null, responseData, responseCode);
    }

    /**
     * 创建失败的结果
     */
    public static SyncResult failure(String errorMessage) {
        return new SyncResult(false, errorMessage, null, -1);
    }

    /**
     * 创建失败的结果（带响应码）
     */
    public static SyncResult failure(String errorMessage, int responseCode) {
        return new SyncResult(false, errorMessage, null, responseCode);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getResponseData() {
        return responseData;
    }

    public int getResponseCode() {
        return responseCode;
    }
}

