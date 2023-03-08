package com.soldesk2.springbootcoup.game.web;

import java.util.HashMap;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Controller;

import com.google.gson.Gson;

@Controller
public class WebGameController {
    @Autowired
    Gson gson;

    @Autowired
    private SimpMessagingTemplate simpMessagingTemplate;

    @MessageMapping("/test")
    @SendTo("/topic/test")
    public Message gametest(@Payload GenericMessage<String> payload) throws Exception {
        //Thread.sleep(500);

        HashMap<String, String> map = gson.fromJson(payload.getPayload(), HashMap.class);
    
        String text = map.get("diceroll");
        int diceroll = Integer.parseInt(text);
        Random random = new Random();
        int r = random.nextInt(diceroll) + 1;
 
        text = "주사위 1~ " + diceroll + " : " + r;
        
        return new Message(MessageType.UPDATE, text, text);
    }


    @MessageMapping("/usertest")
    @SendToUser("/queue/specificTest")
    public String sendSpecific(@Payload String msg) throws Exception {
        Thread.sleep(150);

        return msg + " 안녕하세요";
    }

}
