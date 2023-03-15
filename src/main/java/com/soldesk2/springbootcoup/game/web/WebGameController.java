package com.soldesk2.springbootcoup.game.web;

import java.security.Principal;
import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.util.Map.Entry;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import com.google.gson.Gson;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

@Controller
public class WebGameController {
    private final Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(this.getClass());
    private final HashMap<String, Lobby> lobbyList = new HashMap<>();

    @Autowired
    Gson gson;

    @Autowired
    SimpMessagingTemplate simpMessagingTemplate;

    public WebGameController() {
        // 로거 설정
        this.logger.setLevel(Level.DEBUG);
    }

    @MessageMapping("/chat/{lobbyName}")
    @SendTo("/topic/chat/{lobbyName}")
    public Message chat(@DestinationVariable(value = "lobbyName") String lobbyName, Principal principal, @Payload String message) {
        String username = principal.getName();
        
        //logger.debug("Message recieved: {} from: {}", message, username);

        ChatPayload sendingPayload = new ChatPayload();
        sendingPayload.setSender(username);
        sendingPayload.setMessage(message);

        return new Message(MessageType.CHAT, sendingPayload);
    }


    @MessageMapping("/game")
    @SendToUser("/lobby")
    public String gameInput(Principal principal, @Header String lobbyName, @Payload String message) {
        String username = principal.getName();

        if (!lobbyList.containsKey(lobbyName)) {
            logger.info("유저 {}가 존재하지 않는 로비 {} 게임에 메시지 전하려고 함. 메시지: {}", username, lobbyName, message);
            return "로비명 " + lobbyName + "는 존재하지 않습니다.";
        }

        Lobby lobby = lobbyList.get(lobbyName);
        Entry<String, String> pair = new SimpleEntry<>(username, message);
        lobby.game.playerResponseQueue.add(pair);

        logger.info("로비 {}의 메시지 큐에 유저 {}의 메시지 {} 저장", lobbyName, username, message);
        return "로비명 " + lobbyName + "에 메시지 전송 완료";
    }



    @MessageMapping("/start")
    @SendToUser("/lobby")
    public String startGame(Principal principal, @Header String lobbyName) {
        String username = principal.getName();
        logger.info("유저 {}가 로비 {}에 들어가려함", username, lobbyName);
        
        if (!lobbyList.containsKey(lobbyName)) {
            logger.info("유저 {}가 존재하지 않는 로비 {} 게임 시작 하려 함.", username, lobbyName);
            return "로비명 " + lobbyName + "는 존재하지 않습니다.";
        }

        Lobby lobby = lobbyList.get(lobbyName);

        if (lobby.getState() == Lobby.State.STARTED) {
            logger.info("유저 {}가 이미 게임이 시작된 로비 {} 게임 시작 하려함.", username, lobbyName);
            return "로비명 " + lobbyName + "은 이미 게임이 시작되었습니다.";
        }
        
        try {
            lobby.startGame();
        } catch (IllegalStateException e) {
            return "에러 일어남 : " + e.getMessage();
        }
        
        
        return lobbyName + "의 게임을 시작함.";
    }

    @MessageMapping("/showallgame")
    @SendToUser("/lobby")
    public String showAllGame() {
        return this.lobbyList.toString();
    }

    @MessageMapping("/create")
    @SendToUser("/lobby")
    public String createRoom(Principal principal, @Header(defaultValue = "missingHeader") String lobbyName) {

        if (lobbyName.equals("missingHeader")) {
            logger.info("유저 {}가 로비명 없이 로비 생성하려함", principal.getName());
            return "로비명을 입력해주세요.";
        }

        String name = principal.getName();
        logger.info("유저 {} 가 로비명 {} 로비를 만들고 싶어함", name, lobbyName);

        // 리스트에 이미 로비가 존재하지 않음
        if (!lobbyList.containsKey(lobbyName)) {
            logger.info("로비명 {}의 로비가 리스트에 없음", lobbyName);
            Lobby newLobby = new Lobby(lobbyName, simpMessagingTemplate);
            newLobby.playerNames.add(name);
            lobbyList.put(lobbyName, newLobby);

            logger.info("로비 {} 생성. 현재 로비 리스트: {}", lobbyName, lobbyList);
            return "로비명 " + lobbyName + " 로비를 생성했다. ";
        }

        Lobby lobby = lobbyList.get(lobbyName);

        try {
            lobby.addPlayer(name);
        } catch (IllegalStateException e) {
            logger.error("로비에 유저 추가시 에러 발생 : {}", e.getMessage());
            return "에러 발생함: " + e.getMessage(); 
        }

        logger.info("유저 {}가 로비 {}에 접속함", name, lobbyName);
        return "로비 이름 " + lobbyName + "에 접속 성공. 현재 로비 인원: " + lobby.getPlayerNames(); 
    }

}

