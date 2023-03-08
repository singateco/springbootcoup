package com.soldesk2.springbootcoup.game.web;

import java.util.HashMap;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Controller;

import com.google.gson.Gson;

@Controller
public class WebGameController {
    @Autowired
    Gson gson;

    @MessageMapping("/test")
    @SendTo("/topic/test")
    public Message gametest(@Payload GenericMessage<String> payload) throws Exception {
        Thread.sleep(500);

        HashMap<String, String> map = gson.fromJson(payload.getPayload(), HashMap.class);
    
        String text = "hi";
        
        return new Message(MessageType.UPDATE, text, text);
    }
}
