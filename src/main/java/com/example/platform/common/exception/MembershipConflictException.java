package com.example.platform.common.exception;

import com.example.platform.common.ResultCode;

public class MembershipConflictException extends BusinessException {

    public MembershipConflictException(String message) {
        super(ResultCode.MEMBERSHIP_CONFLICT, message);
    }

    public static MembershipConflictException lastAdministrator() {
        return new MembershipConflictException(
                ResultCode.LAST_ORG_ADMIN_REQUIRED, "必须先完成组织管理员交接");
    }

    private MembershipConflictException(int code, String message) {
        super(code, message);
    }
}
