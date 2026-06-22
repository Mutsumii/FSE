package account.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一返回体。
 *
 * 文档约定响应中"始终包含 code（int）和 message（string）"，
 * 同时多数接口又会在同级返回 fund_acc_no / status / available_balance 等业务字段。
 * 因此 Result 既提供泛型 data 用于通用场景，也提供 extras 用于把
 * 文档要求的同级业务字段直接平铺到顶层 JSON（保证字段命名与文档 100% 一致）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {

    private int code;
    private String message;
    private T data;

    /** 直接平铺到顶层 JSON 的业务字段。Controller 通过 put(key,value) 写入。 */
    @com.fasterxml.jackson.annotation.JsonAnyGetter
    private Map<String, Object> extras;

    public Result<T> put(String key, Object value) {
        if (this.extras == null) {
            this.extras = new LinkedHashMap<>();
        }
        this.extras.put(key, value);
        return this;
    }

    public static <T> Result<T> success() {
        return new Result<>(0, "成功", null, null);
    }

    public static <T> Result<T> success(String message) {
        return new Result<>(0, message, null, null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(0, "成功", data, null);
    }

    public static <T> Result<T> success(String message, T data) {
        return new Result<>(0, message, data, null);
    }

    public static <T> Result<T> ok() {
        return success();
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null, null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null, null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode, String overrideMessage) {
        return new Result<>(errorCode.getCode(), overrideMessage, null, null);
    }
}
