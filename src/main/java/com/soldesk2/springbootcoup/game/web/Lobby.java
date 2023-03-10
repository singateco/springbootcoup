package com.soldesk2.springbootcoup.game.web;

import java.util.ArrayList;
import java.util.Objects;

import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import ch.qos.logback.classic.Logger;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Lobby {
    static final int MIN_PLAYER = 2;
    static final int MAX_PLAYER = 6;

    private final Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(this.getClass());

    private State state;
    private String name;
    private WebGame game;

    private SimpMessagingTemplate simpMessagingTemplate;

    public ArrayList<String> playerNames;
    
    public Lobby(String name, SimpMessagingTemplate simpMessagingTemplate) {
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.name = name;
        this.playerNames = new ArrayList<>();
        this.state = State.OPEN;
    }

    public void addPlayer(String playerName) {

        if (this.playerNames.size() >= MAX_PLAYER) {
            throw new IllegalStateException("로비명 " + this.name + "은 가득 차있다. 현재 접속한 플레이어: " + this.getPlayerNames());
        }

        if (this.playerNames.contains(playerName)) {
            throw new IllegalStateException("로비명 " + this.name + "에 이미 접속해있다. 현재 접속한 플레이어: " + this.getPlayerNames());
        }

        playerNames.add(playerName);
        updateAllPlayers("현재 접속한 로비 " + this.name + "에 " + playerName + "가 접속했다.");

        if (this.playerNames.size() >= MAX_PLAYER) {
            this.state = State.FULL;
        }
    }

    public void startGame() {
        if (this.playerNames.size() < MIN_PLAYER || this.playerNames.size() > MAX_PLAYER) {
            throw new IllegalStateException("로비명 " + this.name + "에서 인원수가 맞지 않은데 게임이 시작되려 함");
        }

        if (this.game != null) {
            throw new IllegalStateException("로비명 " + this.name + "에서 시작되어서는 안되는 게임이 이미 시작 되었음.");
        }


        String[] playerNamesArray = new String[playerNames.size()];
        playerNamesArray = playerNames.toArray(playerNamesArray);

        String destination = "/lobby/" + name;
        this.game = new WebGame(playerNamesArray, destination, simpMessagingTemplate);

        this.state = State.STARTED;
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
               + "현재 플레이어: " + this.playerNames;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }   

    public enum State {
        EMPTY,
        OPEN,
        FULL,
        STARTED
    }
}
