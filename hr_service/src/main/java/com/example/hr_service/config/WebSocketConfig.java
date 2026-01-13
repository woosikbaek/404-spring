package com.example.hr_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    // 채팅용
    registry.addEndpoint("/ws-chat")
        .setAllowedOriginPatterns("*")
        .withSockJS()
        .setClientLibraryUrl("https://cdnjs.cloudflare.com/ajax/libs/sockjs-client/1.5.1/sockjs.min.js") // 라이브러리 경로 명시
        .setInterceptors(); // 가로채기 없이 바로 통과 (비워둠)

    // 2. 근태 알림용
    registry.addEndpoint("/ws-attendance")
        .setAllowedOriginPatterns("*")
        .withSockJS();
  }

  // 브로커 설정
  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/topic");
    registry.setApplicationDestinationPrefixes("/app");
  }

}
