package io.github.barakes.neighbors.limits;

import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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

    public String of(HttpServletRequest request) {
        if (!header.isBlank()) {
            String value = request.getHeader(header);
            if (value != null && LOOKS_LIKE_ADDRESS.matcher(value.trim()).matches()) {
                return value.trim();
            }
        }
        return request.getRemoteAddr();
    }
}
