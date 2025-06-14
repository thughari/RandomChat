package com.thughari.randomchat.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

import com.thughari.randomchat.handler.SignalingHandler;

import java.util.Map;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

	@Autowired
    private SignalingHandler signalingHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(signalingHandler, "/ws")
                .setAllowedOrigins("*")
                .addInterceptors(new HandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(ServerHttpRequest request, 
                            ServerHttpResponse response, 
                            WebSocketHandler wsHandler, 
                            Map<String, Object> attributes) {
                        ServerHttpResponse res = response;
                        res.getHeaders().setCacheControl("no-cache, no-store, must-revalidate");
                        return true;
                    }

					@Override
					public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
							WebSocketHandler wsHandler, Exception exception) {
						// TODO Auto-generated method stub
						
					}
                });
    }
}