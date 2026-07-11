package com.example.platform.service;

public interface EmailVerificationService {
    void sendCode(VerificationPurpose purpose, String email, String ip);

    void verifyAndConsume(VerificationPurpose purpose, String email, String code);
}
