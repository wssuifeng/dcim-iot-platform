package com.example.platform.service.impl;

import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.service.CacheService;
import com.example.platform.service.EmailVerificationService;
import com.example.platform.service.VerificationMailSender;
import com.example.platform.service.VerificationPurpose;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
public class EmailVerificationServiceImpl implements EmailVerificationService {
    private static final int CODE_TTL_SECONDS = 300;
    private static final int COOLDOWN_SECONDS = 60;
    private static final int MAX_FAILURES = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final CacheService cacheService;
    private final VerificationMailSender mailSender;
    private final byte[] hmacSecret;

    public EmailVerificationServiceImpl(
            CacheService cacheService,
            VerificationMailSender mailSender,
            @Value("${verification.hmac-secret}") String hmacSecret) {
        this.cacheService = cacheService;
        this.mailSender = mailSender;
        this.hmacSecret = hmacSecret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void sendCode(VerificationPurpose purpose, String email, String ip) {
        String normalizedEmail = normalizeEmail(email);
        String emailHash = sha256(normalizedEmail);
        acquireCooldown("verify:cooldown:ip:" + ip);
        acquireCooldown("verify:cooldown:email:" + emailHash);
        enforceHourlyQuota("verify:hour:ip:" + ip, 30);
        enforceHourlyQuota("verify:hour:email:" + emailHash, 10);

        String code = "%06d".formatted(SECURE_RANDOM.nextInt(1_000_000));
        mailSender.send(normalizedEmail, code);
        cacheService.set(codeKey(purpose, emailHash), digest(purpose, normalizedEmail, code),
                CODE_TTL_SECONDS, TimeUnit.SECONDS);
        cacheService.delete(failureKey(purpose, emailHash));
    }

    @Override
    public void verifyAndConsume(VerificationPurpose purpose, String email, String code) {
        String normalizedEmail = normalizeEmail(email);
        String emailHash = sha256(normalizedEmail);
        String codeKey = codeKey(purpose, emailHash);
        String expected = cacheService.get(codeKey, String.class);
        if (expected == null) {
            throw new BusinessException(ResultCode.VERIFICATION_INVALID, "验证码已过期或不存在");
        }

        String actual = digest(purpose, normalizedEmail, code == null ? "" : code.trim());
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8))) {
            long failures = cacheService.increment(failureKey(purpose, emailHash), 1);
            if (failures == 1) {
                cacheService.expire(failureKey(purpose, emailHash), CODE_TTL_SECONDS, TimeUnit.SECONDS);
            }
            if (failures >= MAX_FAILURES) {
                cacheService.delete(codeKey);
                throw new BusinessException(ResultCode.VERIFICATION_EXHAUSTED, "验证码尝试次数已耗尽");
            }
            throw new BusinessException(ResultCode.VERIFICATION_INVALID, "验证码错误");
        }

        cacheService.delete(codeKey);
        cacheService.delete(failureKey(purpose, emailHash));
    }

    private void acquireCooldown(String key) {
        if (!cacheService.setIfAbsent(key, 1, COOLDOWN_SECONDS, TimeUnit.SECONDS)) {
            throw new BusinessException(ResultCode.RATE_LIMITED, "60秒内只能发送一次验证码");
        }
    }

    private void enforceHourlyQuota(String key, int maximum) {
        long count = cacheService.increment(key, 1);
        if (count == 1) {
            cacheService.expire(key, 1, TimeUnit.HOURS);
        }
        if (count > maximum) {
            throw new BusinessException(ResultCode.RATE_LIMITED, "验证码发送次数超过小时上限");
        }
    }

    private String digest(VerificationPurpose purpose, String email, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(
                    (purpose.name() + ":" + email + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("无法计算验证码摘要", e);
        }
    }

    private String codeKey(VerificationPurpose purpose, String emailHash) {
        return "verify:code:" + purpose.name().toLowerCase(Locale.ROOT) + ":" + emailHash;
    }

    private String failureKey(VerificationPurpose purpose, String emailHash) {
        return "verify:fail:" + purpose.name().toLowerCase(Locale.ROOT) + ":" + emailHash;
    }

    static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "邮箱不能为空");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
