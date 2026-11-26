package io.github.barakes.neighbors.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SecurityHeadersFilterTest {

    private MockHttpServletResponse filter(String path) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new SecurityHeadersFilter().doFilter(new MockHttpServletRequest("GET", path), response, new MockFilterChain());
        return response;
    }

    @Test
    void everyAnswerGetsTheHeaders() throws Exception {
        for (String path : new String[] {"/", "/help.html", "/api/ladder"}) {
            MockHttpServletResponse response = filter(path);
            assertEquals(SecurityHeadersFilter.CONTENT_SECURITY_POLICY, response.getHeader("Content-Security-Policy"));
            assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
            assertEquals("no-referrer", response.getHeader("Referrer-Policy"));
        }
    }

    @Test
    void scriptsOnlyFromThisSiteAndNoInlineScripts() {
        String policy = SecurityHeadersFilter.CONTENT_SECURITY_POLICY;
        assertTrue(policy.contains("script-src 'self'"));
        assertTrue(!policy.contains("unsafe-inline") && !policy.contains("unsafe-eval"));
        assertTrue(policy.contains("frame-ancestors 'none'"));
    }
}
