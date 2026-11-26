package io.github.barakes.neighbors.limits;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientAddressTest {

    private static MockHttpServletRequest request(String header) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("104.16.0.1");
        if (header != null) {
            request.addHeader("CF-Connecting-IP", header);
        }
        return request;
    }

    @Test
    void usesTheProxyHeader() {
        assertEquals("203.0.113.7", new ClientAddress("CF-Connecting-IP").of(request("203.0.113.7")));
    }

    @Test
    void acceptsIpv6() {
        assertEquals("2001:db8::1", new ClientAddress("CF-Connecting-IP").of(request("2001:db8::1")));
    }

    @Test
    void fallsBackWhenTheHeaderIsMissing() {
        assertEquals("104.16.0.1", new ClientAddress("CF-Connecting-IP").of(request(null)));
    }

    @Test
    void ignoresAHeaderThatIsNotAnAddress() {
        assertEquals("104.16.0.1", new ClientAddress("CF-Connecting-IP").of(request("not an address; drop table")));
    }

    @Test
    void withoutAConfiguredHeaderUsesTheConnection() {
        assertEquals("104.16.0.1", new ClientAddress("").of(request("203.0.113.7")));
    }
}
