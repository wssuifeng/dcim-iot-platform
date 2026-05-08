package com.example.platform.common.exception;

import com.example.platform.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 作用：统一拦截 Controller 层抛出的异常，返回标准 JSON 格式的错误信息
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理参数校验失败异常（@Valid 校验不通过时自动抛出）
     * 将所有字段的错误信息用分号拼接，一次性返回给前端
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST) // HTTP 状态码设为 400
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        // 提取所有字段的校验失败提示，用分号拼接
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)   // 获取 @NotNull(message="...") 里的 message
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);          // 记录警告日志
        return Result.error(400, message);            // 返回统一错误格式
    }

    /**
     * 处理通用业务异常（BusinessException）
     * 注意：假设 BusinessException 内有 getCode() 和 getMessage() 方法
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        // 原文档使用两个参数：e.getCode(), e.getMessage()
        // 如果你这里只有一个参数，请确保 Result 类有对应的 error(String) 重载方法
        // 建议改为 Result.error(e.getCode(), e.getMessage()) 更规范
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理设备未找到异常（DeviceNotFoundException）
     */
    @ExceptionHandler(DeviceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND) // 404 状态码：资源不存在
    public Result<Void> handleDeviceNotFound(DeviceNotFoundException e) {
        log.warn("设备未找到: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理设备代码已存在异常（DeviceCodeAlreadyExistsException）
     * 一般属于客户端输入冲突，也用 400 状态码
     */
    @ExceptionHandler(DeviceCodeAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleDeviceCodeAlreadyExists(DeviceCodeAlreadyExistsException e) {
        log.warn("设备代码已存在: {}", e.getMessage());
        // 同样建议确认 Result.error 的参数要求
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理设备类型未找到异常（DeviceTypeNotFound）
     */
    @ExceptionHandler(DeviceTypeNotFound.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleDeviceTypeNotFound(DeviceTypeNotFound e) {
        log.warn("设备类型id未找到: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 兜底处理：捕获所有未在前面精确匹配的异常
     * 返回模糊错误信息，防止泄露系统细节
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR) // 500 状态码
    public Result<Void> handleException(Exception e) {
        log.error("系统异常: ", e);                  // 记录完整堆栈信息
        return Result.error(500, "服务器内部错误");
    }
}