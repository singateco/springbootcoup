package com.soldesk2.springbootcoup.game.web;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import com.google.gson.Gson;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

@Controller
public class WebGameController {
    private final Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(this.getClass());
    private HashMap<String, Lobby> lobbyList;
    
    @Autowired
    Gson gson;

    @Autowired
    SimpMessagingTemplate simpMessagingTemplate;

    public WebGameController() {
        // 로거 설정
        this.logger.setLevel(Level.DEBUG);

        // 로비리스트 만들기
        this.lobbyList = new HashMap<>();
    }

    @MessageMapping("/startgame")
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

    @MessageMapping("/createroom")
    @SendToUser("/lobby")
    public String createRoom(Principal principal, @Header String lobbyName) {

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

        Lobby existingLobby = lobbyList.get(lobbyName);
        ArrayList<String> playerNames = existingLobby.playerNames;

        logger.debug("현재 로비 인원수: {}, 인원: {}", playerNames.size(), playerNames);
        
        // 이미 풀방임
        if (playerNames.size() >= 6) {
            String message = "로비명 " + lobbyName + "은 꽉차있다. 현재 접속한 플레이어 : " + existingLobby.getPlayerNames();
            logger.info("유저 {}에게 메시지 보냄 : {}", name, message);
            return message;
        } else {
            // 이미 그 로비에 접속해 있을시
            if (playerNames.contains(name)) {
                String message = "이미 로비 " + lobbyName + "에 접속해있다! 현재 접속한 플레이어: " + playerNames;
                logger.info("유저 {}에게 메시지 보냄 : {}", name, message);
                return message; 
            }

            // 풀방이 아닐시 로비의 플레이어 이름에 저장
            playerNames.add(name);
            String message = "로비명 " + lobbyName + "에 접속했다. 현재 접속한 플레이어 : " + existingLobby.getPlayerNames();
            logger.info("유저 {}에게 메시지 보냄 : {}", name, message);
            return message;
        }
    }

}
