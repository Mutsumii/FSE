package account.exception;

import account.common.BusinessException;
import account.common.ErrorCode;
import account.common.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * 全局异常处理：把 BusinessException 自动映射成文档中的 ERR_xxx 错误码响应，
 * 把参数校验异常映射为统一的 PARAM_INVALID。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException ex) {
        log.warn("业务异常 [{}] {}", ex.getErrorCode().getSymbol(), ex.getDetail());
        return Result.<Void>fail(ex.getErrorCode(), ex.getDetail())
                .put("symbol", ex.getErrorCode().getSymbol());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return Result.fail(ErrorCode.PARAM_INVALID, msg);
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBindException(BindException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("参数绑定失败: {}", msg);
        return Result.fail(ErrorCode.PARAM_INVALID, msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraintViolation(ConstraintViolationException ex) {
        String msg = ex.getConstraintViolations().stream()
                .map(this::formatViolation)
                .collect(Collectors.joining("; "));
        log.warn("约束校验失败: {}", msg);
        return Result.fail(ErrorCode.PARAM_INVALID, msg);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBadRequest(Exception ex) {
        log.warn("请求体或参数解析失败: {}", ex.getMessage());
        return Result.fail(ErrorCode.PARAM_INVALID, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("非法参数: {}", ex.getMessage());
        return Result.fail(ErrorCode.PARAM_INVALID, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleUnknown(Exception ex) {
        log.error("系统异常", ex);
        return Result.fail(ErrorCode.SYSTEM_ERROR, "系统内部错误: " + ex.getMessage());
    }

    private String formatFieldError(FieldError fe) {
        return fe.getField() + ": " + fe.getDefaultMessage();
    }

    private String formatViolation(ConstraintViolation<?> v) {
        return v.getPropertyPath() + ": " + v.getMessage();
    }
}
