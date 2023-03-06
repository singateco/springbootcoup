package com.soldesk2.springbootcoup.websocket.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;

import com.soldesk2.springbootcoup.websocket.model.Message;

public class ChatController {
    
    @MessageMapping("/message")
    @SendTo("/chatroom")
    public Message receiveMessage(@Payload Message message){
        return message;
    }
}
