package vn.nal.todoreport.client;

/**
 * Lỗi khi không gọi được, hoặc nhận phản hồi lỗi từ app Node.js.
 */
public class NodeApiException extends RuntimeException {

    public NodeApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
