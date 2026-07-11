package com.example.platform.security;

import com.example.platform.entity.OrganizationMembership;
import com.example.platform.service.OrganizationMembershipService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationContextFilterTest {
    @Mock
    private OrganizationMembershipService membershipService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activeMembershipAddsRequestScopedOrganizationAuthority() throws Exception {
        CurrentOrganizationContext context = new CurrentOrganizationContext();
        OrganizationContextFilter filter = new OrganizationContextFilter(membershipService, context);
        OrganizationMembership membership = new OrganizationMembership();
        membership.setUserId(7L);
        membership.setOrganizationId(10L);
        membership.setMembershipRole("OPERATOR");
        membership.setStatus("ACTIVE");
        when(membershipService.requireActiveMembership(7L, 10L)).thenReturn(membership);

        var authentication = new UsernamePasswordAuthenticationToken(7L, null,
                List.of(new SimpleGrantedAuthority("ROLE_AUTHENTICATED_USER")));
        authentication.setDetails("operator");
        SecurityContextHolder.getContext().setAuthentication(authentication);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Organization-Id", "10");
        AtomicReference<String> authority = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> authority.set(SecurityContextHolder.getContext().getAuthentication()
                        .getAuthorities().stream().map(a -> a.getAuthority())
                        .filter(a -> a.equals("ROLE_OPERATOR")).findFirst().orElse(null)));

        assertThat(authority.get()).isEqualTo("ROLE_OPERATOR");
        assertThat(context.currentOrganizationId()).isEmpty();
    }
}
