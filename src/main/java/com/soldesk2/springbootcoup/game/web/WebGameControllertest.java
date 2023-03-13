package com.soldesk2.springbootcoup.game.web;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import java.util.Random;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.support.NativeMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import com.google.gson.Gson;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

@Controller
public class WebGameControllertest {
    private final Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(this.getClass());
    @Autowired
    Gson gson;

    @Autowired
    private SimpMessagingTemplate simpMessagingTemplate;

    public WebGameControllertest() {
        // 로거 설정
        this.logger.setLevel(Level.DEBUG);
    }

    
    @MessageMapping("/headertest")
    @SendToUser("/topic/headertest")
    public String headerTest(org.springframework.messaging.Message<?> message) {
        logger.debug("Message: {}", message);
        logger.debug("Using StompHeaderAccessor to get header...");
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        Principal user = accessor.getUser();
        String username = Optional.ofNullable(user).map(Principal::getName).orElse("anonymous");

        logger.debug("User: {}", username);
        
        ArrayList<String> nativeheader = (ArrayList<String>) accessor.getNativeHeader("header1");
        String nativeheaderString = Optional.ofNullable(nativeheader).map(ArrayList::toString).orElse("No native Header");

        logger.debug("NativeHeader: {}", nativeheaderString);



        return "Header test complete. User : " + username + ", NativeHeader : " + nativeheaderString;
    }

    // TODO 테스트용 : 삭제
    @MessageMapping("/test")
    @SendTo("/topic/test")
    public Message gametest(@Payload GenericMessage<String> payload) throws Exception {
        // Thread.sleep(500);
        logger.debug("Request");
        HashMap<String, String> map = gson.fromJson(payload.getPayload(), HashMap.class);

        String text = map.get("diceroll");
        int diceroll = Integer.parseInt(text);
        Random random = new Random();
        int r = random.nextInt(diceroll) + 1;

        text = "주사위 1~ " + diceroll + " : " + r;

        return new Message(MessageType.UPDATE, text, text);
    }

    // TODO 테스트용 : 삭제
    @MessageMapping("/usertest")
    @SendToUser("/queue/specificTest")
    public String sendSpecific(@Payload GenericMessage<String> msg) throws Exception {
        Thread.sleep(150);

        NativeMessageHeaderAccessor a = (NativeMessageHeaderAccessor) MessageHeaderAccessor.getAccessor(msg);

        for (String s : a.getNativeHeader("username")) {
            System.out.println(s);
        }
        return msg + " 안녕하세요";
    }

    // TODO 테스트용 : 삭제
    @SubscribeMapping("/topic/room/042")
    public String subscribe() {
        try {
            this.sendMessage("/topic/room/042");
        } catch (InterruptedException e) {
            logger.error("메시지 전송 에러", e);
        }

        return "You're now subscribed to room 042";
    }

    // TODO 테스트용 : 삭제
    void sendMessage(String destination) throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            logger.debug("Sending message {} to destination {} ", i, destination);
            simpMessagingTemplate.convertAndSend(destination, "message " + i);
            Thread.sleep(600);
        }
    }

    // TODO: 테스트용 삭제
    @MessageMapping("/teststartgame")
    @SendTo("/topic/room/042")
    public String startGame(@Header String dest, Principal principal) {
        String[] playerNames = new String[5];
        for (int i = 0; i < 4; i++) {
            playerNames[i] = "User" + i;
        }
        playerNames[4] = principal.getName();

        new WebGame(playerNames, dest, simpMessagingTemplate);

        return "Starting new game at" + dest + principal.getName();
    }
}
