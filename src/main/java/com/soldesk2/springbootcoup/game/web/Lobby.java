package com.soldesk2.springbootcoup.game.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import ch.qos.logback.classic.Logger;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Lobby {
    static final int MIN_PLAYER = 1;
    static final int MAX_PLAYER = 6;

    private final Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(this.getClass());

    // 현재 상황 : 밑의 enum 참고
    private State state;

    // 로비 주소
    private String destination;

    private String name;
    public WebGame game;

    private SimpMessagingTemplate simpMessagingTemplate;

    public List<String> playerNames;
    
    public Lobby(String name, SimpMessagingTemplate simpMessagingTemplate) {
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.name = name;
        this.destination = "/lobby";
        this.playerNames = new ArrayList<>();
        this.state = State.OPEN;
        this.game = new WebGame(destination, simpMessagingTemplate);
    }

    public void addPlayer(String playerName) {

        if (this.playerNames.size() >= MAX_PLAYER) {
            throw new IllegalStateException("로비명 " + this.name + "은 가득 차있다. 현재 접속한 플레이어: " + this.getPlayerNames());
        }

        if (this.playerNames.contains(playerName)) {
            throw new IllegalStateException("로비명 " + this.name + "에 이미 접속해있다. 현재 접속한 플레이어: " + this.getPlayerNames());
        }

        logger.info("로비 {}에 {}가 접속했다.", this.name, playerName);


        playerNames.add(playerName);
        updateAllPlayers("현재 접속한 로비 " + this.name + "에 " + playerName + "가 접속했다. \n" +
                        "현재 접속한 플레이어: " + this.playerNames);

        if (this.playerNames.size() >= MAX_PLAYER) {
            this.state = State.FULL;
        }
    }

    public void updateAllPlayers(Object obj) {
        logger.debug("{} 으로 메시지 보내는 중... {}", destination, obj);
        for (String player: playerNames) {
            simpMessagingTemplate.convertAndSendToUser(player, destination, obj);
        }
    }

    public void startGame() {
        if (this.playerNames.size() < MIN_PLAYER || this.playerNames.size() > MAX_PLAYER) {
            throw new IllegalStateException("로비명 " + this.name + "에서 인원수가 맞지 않은데 게임이 시작되려 함");
        }

        String[] playerNamesArray = new String[playerNames.size()];
        playerNamesArray = playerNames.toArray(playerNamesArray);

        logger.info("{} 에서 게임 시작중....", destination);
        updateAllPlayers("로비 " + this.getName() + "에서 게임 시작중...");


        logger.info("Game Started at lobbyName {}, State:", this.name, this.state);
        this.state = State.STARTED;

        this.game.play(playerNamesArray);

        this.state = State.ENDED;
    }

    public void endGame() {
        this.playerNames = null;
        this.state = State.ENDED;
        if (!Objects.isNull(this.game)) {
            this.game.endGame();
            this.game = null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof Lobby)) {
            return false;
        }
        Lobby lobby = (Lobby) o;
        return Objects.equals(name, lobby.name);
    }

    @Override
    public String toString() {
        return "현재 상태 : " + this.state + "\n"
               + "현재 플레이어: " + this.playerNames + "\n"
               + "대상 주소: " + this.destination;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }   

    public enum State {
        EMPTY,
        OPEN,
        FULL,
        STARTED,
        ENDED
    }
}
