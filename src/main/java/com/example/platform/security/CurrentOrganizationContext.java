package com.example.platform.security;

import com.example.platform.common.exception.DataScopeViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CurrentOrganizationContext {
    private final ThreadLocal<Long> organizationIdHolder = new ThreadLocal<>();

    public void setOrganizationId(Long organizationId) {
        organizationIdHolder.set(organizationId);
    }

    public Optional<Long> currentOrganizationId() {
        return Optional.ofNullable(organizationIdHolder.get());
    }

    public Long requireOrganizationId() {
        return currentOrganizationId().orElseThrow(() ->
                new DataScopeViolationException("当前请求缺少 X-Organization-Id"));
    }

    public void requireMatches(Long organizationId) {
        if (!requireOrganizationId().equals(organizationId)) {
            throw new DataScopeViolationException("请求组织与当前组织不一致");
        }
    }

    public void clear() {
        organizationIdHolder.remove();
    }
}
