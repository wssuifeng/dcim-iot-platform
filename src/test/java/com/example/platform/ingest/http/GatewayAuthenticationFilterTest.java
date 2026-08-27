package com.example.platform.ingest.http;

import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.ingest.entity.IngestCredential;
import com.example.platform.ingest.service.IngestCredentialService;
import com.example.platform.ingest.util.GatewaySignUtils;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GatewayAuthenticationFilterTest {

    @Mock
    private IngestCredentialService ingestCredentialService;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private GatewayAuthenticationFilter filter;

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void missingAuthorizationHeaderRejected() throws Exception {
        MockHttpServletRequest request = ingestRequest("");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .contains(String.valueOf(ResultCode.INGEST_UNAUTHORIZED));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void malformedAuthorizationHeaderRejected() throws Exception {
        MockHttpServletRequest request = ingestRequest("");
        request.addHeader("Authorization", "Gateway only-access-key");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void nonGatewaySchemeIgnored() throws Exception {
        MockHttpServletRequest request = ingestRequest("");
        request.addHeader("Authorization", "Bearer user-jwt-token");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void unknownAccessKeyRejected() throws Exception {
        MockHttpServletRequest request = ingestRequest("{\"items\":[]}");
        when(ingestCredentialService.authenticate("gw-unknown"))
                .thenThrow(new BusinessException(ResultCode.INGEST_UNAUTHORIZED, "网关凭证无效"));
        request.addHeader("Authorization", gatewayHeader("gw-unknown", "whatever", "{\"items\":[]}"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void wrongSignatureRejected() throws Exception {
        String body = "{\"items\":[]}";
        MockHttpServletRequest request = ingestRequest(body);
        stubCredential("gw-a", "secret-a");
        request.addHeader("Authorization", gatewayHeader("gw-a", "wrong", body));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void validSignaturePassesCredentialDownstream() throws Exception {
        String body = "{\"items\":[]}";
        MockHttpServletRequest request = ingestRequest(body);
        IngestCredential credential = stubCredential("gw-a", "secret-a");
        request.addHeader("Authorization", gatewayHeader("gw-a", "secret-a", body));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(filterChain).doFilter(any(), any());
        org.mockito.ArgumentCaptor<CachedBodyRequestWrapper> captor =
                org.mockito.ArgumentCaptor.forClass(CachedBodyRequestWrapper.class);
        verify(filterChain).doFilter(captor.capture(), any());
        assertThat(captor.getValue().getAttribute(GatewayAuthenticationFilter.ATTR_CREDENTIAL))
                .isSameAs(credential);
        assertThat(new String(captor.getValue().getBody(), StandardCharsets.UTF_8)).isEqualTo(body);
    }

    @Test
    void nonIngestPathSkipsFilter() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/devices");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void ingestPathIsFiltered() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ingest/samples");

        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    private MockHttpServletRequest ingestRequest(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ingest/samples");
        if (!body.isEmpty()) {
            request.setContent(body.getBytes(StandardCharsets.UTF_8));
        }
        return request;
    }

    private String gatewayHeader(String accessKey, String secret, String body) {
        return "Gateway " + accessKey + ":"
                + ("wrong".equals(secret) ? secret : GatewaySignUtils.signature(secret, body));
    }

    private IngestCredential stubCredential(String accessKey, String secret) {
        IngestCredential credential = new IngestCredential();
        credential.setId(1L);
        credential.setAccessKey(accessKey);
        credential.setSecret(secret);
        credential.setCloudRoomId(1L);
        when(ingestCredentialService.authenticate(accessKey)).thenReturn(credential);
        return credential;
    }
}
