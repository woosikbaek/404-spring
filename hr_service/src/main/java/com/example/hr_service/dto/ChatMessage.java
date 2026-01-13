package com.example.hr_service.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessage {
  private String sender;
  private String content;
  private MessageType type;

  public enum MessageType {
    CHAT, JOIN, LEAVE
  }
}
