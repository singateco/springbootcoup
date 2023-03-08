package com.soldesk2.springbootcoup.websocket.controller;

import java.awt.List;
import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Controller;

import com.google.gson.Gson;

@Controller
public class ChatController {
    
    @Autowired
    Gson gson;

    @MessageMapping("/message")
    @SendTo("/chatroom")
    public String receiveMessage(@Header String message, @Payload GenericMessage genericMessage){
        System.out.println(message);

        return message;
    }
}
