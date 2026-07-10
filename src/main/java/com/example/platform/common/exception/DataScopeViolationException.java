package com.example.platform.common.exception;

import com.example.platform.common.ResultCode;

/*
 * 类功能:数据范围违规异常，当用户尝试访问未授权的组织或机房数据时抛出
 * 用于产品化数据范围校验，记录拒绝类型审计日志
 * */
public class DataScopeViolationException extends BusinessException {

    /*
     * 方法功能:构造数据范围违规异常
     * 参数:message-违规详情描述
     * 返回数据:无
     * */
    public DataScopeViolationException(String message) {
        super(ResultCode.FORBIDDEN, message);
    }

    /*
     * 方法功能:构造数据范围违规异常（带资源类型和ID）
     * 参数:resourceType-资源类型, resourceId-资源ID
     * 返回数据:无
     * */
    public DataScopeViolationException(String resourceType, String resourceId) {
        super(ResultCode.FORBIDDEN,
            String.format("无权访问 %s 资源: %s，请检查数据范围授权", resourceType, resourceId));
    }
}
