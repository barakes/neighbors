package io.github.barakes.neighbors.limits;

import java.net.InetSocketAddress;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * The address the limits count by. Taken from one header that the proxy in front of the server writes
 * itself (limits.client-ip-header), falling back to the connection's own address when the header is
 * missing or doesn't look like an address.
 */
@Component
public class ClientAddress {

    /** Only digits, a-f, dots and colons (IPv4 and IPv6), so nothing else can pass as an address. */
    private static final Pattern LOOKS_LIKE_ADDRESS = Pattern.compile("[0-9a-fA-F:.]{2,45}");

    private final String header;

    public ClientAddress(@Value("${limits.client-ip-header:}") String header) {
        this.header = header;
    }

    /** For HTTP requests. */
    public String of(HttpServletRequest request) {
        String fromHeader = fromHeader(request.getHeader(header.isBlank() ? "-" : header));
        return fromHeader != null ? fromHeader : request.getRemoteAddr();
    }

    /** For WebSocket connections: the headers of the request that opened the connection. */
    public String of(WebSocketSession session) {
        HttpHeaders headers = session.getHandshakeHeaders();
        String fromHeader = headers == null || header.isBlank() ? null : fromHeader(headers.getFirst(header));
        if (fromHeader != null) {
            return fromHeader;
        }
        InetSocketAddress remote = session.getRemoteAddress();
        return remote == null || remote.getAddress() == null ? "unknown" : remote.getAddress().getHostAddress();
    }

    private String fromHeader(String value) {
        if (header.isBlank() || value == null) {
            return null;
        }
        String trimmed = value.trim();
        return LOOKS_LIKE_ADDRESS.matcher(trimmed).matches() ? trimmed : null;
    }
}
