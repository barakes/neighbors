package io.github.barakes.neighbors.presence;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** The page connects to /ws/presence. Only pages from this same site may connect (the default). */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final PresenceHandler presenceHandler;

    public WebSocketConfig(PresenceHandler presenceHandler) {
        this.presenceHandler = presenceHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(presenceHandler, "/ws/presence");
    }
}
