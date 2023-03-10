package com.soldesk2.springbootcoup.game.web;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.soldesk2.springbootcoup.game.Action;
import com.soldesk2.springbootcoup.game.Card;
import com.soldesk2.springbootcoup.game.Player;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;


public class WebGame {
    public static final int MIN_PLAYER = 2;
    public static final int MAX_PLAYER = 6;

    private final Random random;

    protected final Player[] players;
    private final List<Card> deck;
    private final Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(this.getClass());

    private final String destination;

    private SimpMessagingTemplate simpMessagingTemplate;

    public WebGame(String[] playerNames, String destination, SimpMessagingTemplate simpMessagingTemplate) {
        logger.setLevel(Level.DEBUG);
        logger.debug("Setting game up... playerNames : {} destination : {}", playerNames, destination);

        this.simpMessagingTemplate = simpMessagingTemplate;
        this.random = new Random();
        this.destination = destination;

        int numberOfPlayers = playerNames.length;

        if (numberOfPlayers < MIN_PLAYER || numberOfPlayers > MAX_PLAYER) {
            throw new IllegalArgumentException("맞지 않는 수의 플레이어입니다.");
        }

        this.players = new Player[numberOfPlayers];
        
        // 카드마다 3장씩 덱의 크기를 설정
        this.deck = new ArrayList<>(Card.values().length * 3);

        // 덱에 카드 종류마다 3장씩 넣음
        for (Card card : Card.values()) {
            for (int i = 0; i < 3; i++) {
                deck.add(card);
            }
        }

        shuffleDeck();

        // 플레이어 설정
        for (int i = 0; i < numberOfPlayers; i++) {
            players[i] = new Player(playerNames[i], drawOne(), drawOne());
        }

        play();
    }

    public void play() {
        int playerIndex = 4;
        update();

        while (alivePlayers() > 1) {
           Player player = players[playerIndex];
           Action[] actionOptions = getAction(player);
        
           logger.debug("sending message to {}", player.getName());

           simpMessagingTemplate.convertAndSendToUser(player.getName(), destination, player.getName() + ", Your turn.");
           
           break;
        }
    }

    /**
     * 게임 안에 아직 패배하지 않은 플레이어의 수를 출력한다.
     * @return 패배하지 않은 플레이어의 수.
     */
    public int alivePlayers() {
        int count = 0;
        for (int i = 0; i < this.players.length; i++) {
            if (players[i] != null) {
                count++;
            }
        }

        return count;
    }

    /**
     * UI를 업데이트한다.
     */
    void update() {
        String wholeMessage = "";
        for (Player player: players) {
            if (player != null) {
                List<Card> cards = player.getCards();
                String message = player.getName() + " - Coins: " + player.getCoins() + " Cards : " +
                                 cards;

                logger.debug("sending {} to {}", message, player.getName());
                wholeMessage = wholeMessage + message + "\n";
            }
        }

        simpMessagingTemplate.convertAndSend(destination, wholeMessage);
    }

    /**
     * 사용 가능한 액션을 반환한다.
     * @param player 액션을 하는 플레이어.
     */
    Action[] getAction(Player player) {
        // TODO
        return new Action[]{};
    }

    Card drawOne() {
        return this.deck.remove(this.deck.size() - 1);
    }

    void shuffleDeck() {
        Collections.shuffle(this.deck, this.random);
    }
}
