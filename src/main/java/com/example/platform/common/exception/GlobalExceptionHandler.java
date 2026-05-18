package com.example.platform.common.exception;

import com.example.platform.common.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
