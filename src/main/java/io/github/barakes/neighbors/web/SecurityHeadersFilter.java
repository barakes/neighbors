package io.github.barakes.neighbors.web;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Security headers on every answer, pages and API alike.
 * <p>
 * The main one, Content-Security-Policy, tells the browser to run only scripts and styles that come
 * from this site, as files: no inline scripts, nothing from anywhere else. Even if someone found a way to
 * inject code into a page, the browser would refuse to run it. A second layer, after textContent.
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self'",
            "img-src 'self' data:",
            "connect-src 'self'",
            "manifest-src 'self'",
            "worker-src 'self'",
            "object-src 'none'",
            "base-uri 'none'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        // Don't guess file types: a file served as text is never run as a script.
        response.setHeader("X-Content-Type-Options", "nosniff");
        // Links to help lines don't tell them which app the person came from.
        response.setHeader("Referrer-Policy", "no-referrer");
        // The app never needs these; nothing on the page can ask for them.
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        // HTTPS only, for a year. Browsers ignore this on plain http (like localhost).
        response.setHeader("Strict-Transport-Security", "max-age=31536000");
        chain.doFilter(request, response);
    }
}
