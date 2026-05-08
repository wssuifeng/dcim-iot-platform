package com.example.platform.common.exception;


import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    int code;
    public BusinessException(String message){
        super(message);
        this.code = 400;
    }
    public BusinessException(int code,String message){
        super(message);
        this.code = code;
    }
}
