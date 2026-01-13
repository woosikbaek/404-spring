package com.example.hr_service.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import com.example.hr_service.dto.ChatMessage;

@Controller
public class ChatController {
  
  // 메시지 전송 : 클라이언트가 "/app/chat.sendMessage"로 메시지 보내면 호출
  @MessageMapping("/chat.sendMessage")
  // 리턴되는 메시지를 "/topic/public"채널을 구독중인 모든 유저에게 전달
  @SendTo("/topic/public")
  public ChatMessage sendMessage(ChatMessage chatMessage) {
    return chatMessage;
  }

  // 유저 추가: 클라이언트가 "/app/chat.addUser"로 접속 정보를 보내면 호출됨
  @MessageMapping("/chat.addUser")
  @SendTo("/topic/public")
  public ChatMessage addUser(ChatMessage chatMessage, SimpMessageHeaderAccessor headerAccessor) {
    headerAccessor.getSessionAttributes().put("username", chatMessage.getSender());
    return chatMessage;
  }
}
