package com.example.platform.controller;

import com.example.platform.common.Result;
import com.example.platform.dto.PlatformSetupDTO;
import com.example.platform.dto.PlatformSetupStatusVO;
import com.example.platform.dto.RegistrationCodeDTO;
import com.example.platform.service.PlatformSetupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/setup")
@RequiredArgsConstructor
public class PlatformSetupController {
    private final PlatformSetupService setupService;

    @GetMapping("/status")
    public Result<PlatformSetupStatusVO> status() {
        return Result.success(new PlatformSetupStatusVO(setupService.isSetupRequired()));
    }

    @PostMapping("/verification-codes")
    public Result<Void> sendCode(@Valid @RequestBody RegistrationCodeDTO dto,
                                 HttpServletRequest request) {
        setupService.sendVerificationCode(dto.getEmail(), clientIp(request));
        return Result.success();
    }

    @PostMapping("/platform-admin")
    public Result<Void> initialize(@Valid @RequestBody PlatformSetupDTO dto,
                                   HttpServletRequest request) {
        setupService.initialize(dto, clientIp(request));
        return Result.success();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
