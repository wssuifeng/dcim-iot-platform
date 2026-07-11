package com.example.platform.service;

public interface VerificationMailSender {
    void send(String email, String code);
}
