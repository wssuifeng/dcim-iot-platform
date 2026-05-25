package com.example.platform.common.exception;

import com.example.platform.common.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/*
 * 类功能:全局异常处理器，统一拦截Controller层抛出的异常并返回标准JSON格式的错误信息
 * */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /*
     * 方法功能:处理@RequestBody参数校验失败异常（@Validated/@Valid校验不通过时抛出）
     * 参数:e-方法参数校验异常
     * 返回数据:Result<Void>-包含所有字段错误提示的统一错误响应
     * */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return Result.error(400, message);
    }

    /*
     * 方法功能:处理路径参数/查询参数校验失败异常（@Validated标注在@RequestParam/@PathVariable上时抛出）
     * 参数:e-约束违反异常
     * 返回数据:Result<Void>-包含所有约束错误提示的统一错误响应
     * */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return Result.error(400, message);
    }

    /*
     * 方法功能:处理通用业务异常
     * 参数:e-业务异常
     * 返回数据:Result<Void>-携带业务错误码和消息的统一错误响应
     * */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /*
     * 方法功能:处理设备未找到异常
     * 参数:e-设备未找到异常
     * 返回数据:Result<Void>-404状态码的错误响应
     * */
    @ExceptionHandler(DeviceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleDeviceNotFound(DeviceNotFoundException e) {
        log.warn("设备未找到: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /*
     * 方法功能:处理设备代码已存在异常
     * 参数:e-设备代码已存在异常
     * 返回数据:Result<Void>-400状态码的错误响应
     * */
    @ExceptionHandler(DeviceCodeAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleDeviceCodeAlreadyExists(DeviceCodeAlreadyExistsException e) {
        log.warn("设备代码已存在: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /*
     * 方法功能:处理设备类型未找到异常
     * 参数:e-设备类型未找到异常
     * 返回数据:Result<Void>-404状态码的错误响应
     * */
    @ExceptionHandler(DeviceTypeNotFound.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleDeviceTypeNotFound(DeviceTypeNotFound e) {
        log.warn("设备类型未找到: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(DeviceTypeCodeAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleDeviceTypeCodeAlreadyExists(DeviceTypeCodeAlreadyExistsException e) {
        log.warn("设备类型编码已存在: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /*
     * 方法功能:处理用户未找到异常
     * 参数:e-用户未找到异常
     * 返回数据:Result<Void>-404状态码的错误响应
     * */
    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleUserNotFound(UserNotFoundException e) {
        log.warn("用户未找到: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /*
     * 方法功能:处理用户名已存在异常
     * 参数:e-用户名已存在异常
     * 返回数据:Result<Void>-400状态码的错误响应
     * */
    @ExceptionHandler(UsernameAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleUsernameAlreadyExists(UsernameAlreadyExistsException e) {
        log.warn("用户名已存在: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /*
     * 方法功能:处理账号锁定异常
     * 参数:e-账号锁定异常
     * 返回数据:Result<Void>-400状态码的错误响应
     * */
    @ExceptionHandler(AccountLockedException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleAccountLocked(AccountLockedException e) {
        log.warn("账号锁定: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /*
     * 方法功能:处理请求体缺失或JSON格式错误异常
     * 场景:用户未输入账号密码直接提交、请求体不是合法JSON、Content-Type不正确
     * 参数:e-HTTP消息不可读异常
     * 返回数据:Result<Void>-400状态码，提示用户正确输入
     * */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        String message = "请求体格式错误，请检查输入内容";
        if (e.getMessage() != null && e.getMessage().contains("Required request body is missing")) {
            message = "请输入账号和密码";
        }
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.error(400, message);
    }

    /*
     * 方法功能:处理请求方法不支持异常（如用GET访问POST接口）
     * 参数:e-请求方法不支持异常
     * 返回数据:Result<Void>-405状态码的错误响应
     * */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: {} {}", e.getMethod(), e.getMessage());
        return Result.error(405, "请求方法不允许，请使用 " + e.getSupportedHttpMethods());
    }

    /*
     * 方法功能:处理缺少必需请求参数异常
     * 参数:e-缺少请求参数异常
     * 返回数据:Result<Void>-400状态码的错误响应
     * */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数: {}", e.getParameterName());
        return Result.error(400, "缺少必需参数: " + e.getParameterName());
    }

    /*
     * 方法功能:处理Spring Security权限不足异常（@RestControllerAdvice兜底层）
     * 注:正常情况下AccessDeniedException由CustomAccessDeniedHandler处理，
     *    此处作为额外防线，防止异常绕过Security过滤器链
     * 参数:e-权限不足异常
     * 返回数据:Result<Void>-403状态码的错误响应
     * */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDenied(AccessDeniedException e) {
        log.warn("权限不足: {}", e.getMessage());
        return Result.error(403, "权限不足，无法访问该资源");
    }

    /*
     * 方法功能:处理资源不存在异常（路径匹配不到任何Controller时抛出）
     * 参数:e-资源未找到异常
     * 返回数据:Result<Void>-404状态码的错误响应
     * */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("资源不存在: {}", e.getMessage());
        return Result.error(404, "请求的资源不存在");
    }

    /*
     * 方法功能:兜底处理所有未精确匹配的异常，记录完整堆栈并返回脱敏错误信息
     * 参数:e-未捕获的异常
     * 返回数据:Result<Void>-500状态码的通用错误响应
     * */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常: ", e);
        return Result.error(500, "服务器内部错误");
    }
}
