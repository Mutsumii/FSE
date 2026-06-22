package account.common;

import lombok.Getter;

/**
 * 业务异常，Service 层抛出后由 GlobalExceptionHandler 统一翻译为 Result。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String detail;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.detail = errorCode.getMessage();
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
        this.detail = detail;
    }

    public BusinessException(ErrorCode errorCode, String detail, Throwable cause) {
        super(detail, cause);
        this.errorCode = errorCode;
        this.detail = detail;
    }
}
