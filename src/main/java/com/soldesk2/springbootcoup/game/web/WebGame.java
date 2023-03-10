package com.soldesk2.springbootcoup.game.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.soldesk2.springbootcoup.game.Action;
import com.soldesk2.springbootcoup.game.Card;
import com.soldesk2.springbootcoup.game.Player;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;


public class WebGame {
    public static final int MIN_PLAYER = 2;
    public static final int MAX_PLAYER = 6;

    private final Random random;
    private StringBuilder stringBuilder;

    protected final Player[] players;
    private final List<Card> deck;
    private final Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(this.getClass());

    private final String destination;

    private SimpMessagingTemplate simpMessagingTemplate;

    public WebGame(String[] playerNames, String destination, SimpMessagingTemplate simpMessagingTemplate) {
        logger.setLevel(Level.DEBUG);
        logger.debug("Setting game up... playerNames : {} destination : {}", playerNames, destination);

        this.stringBuilder = new StringBuilder();
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

        try {
            play();
        } catch (Exception e) {
            logger.error("에러 일어남", e);
        }
        
    }

    public void play() throws InterruptedException {
        Thread.sleep(1000);
        
        update();

        for (int i = 0; i <this.players.length; i++) {
            String user = players[i].getName();
            String msg = "Your Deck: " + players[i].getCards();
            logger.info("Sending {} to {}", msg, user);
            simpMessagingTemplate.convertAndSendToUser(user, destination, msg);
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
        stringBuilder.setLength(0);
        stringBuilder.append("살아있는 플레이어 수 : ");
        stringBuilder.append(alivePlayers());
        stringBuilder.append("\n");
        stringBuilder.append("덱에 남은 카드 수 : ");
        stringBuilder.append(this.deck.size());
        stringBuilder.append("\n");

        for (int i = 0; i < players.length; i++) {
            stringBuilder.append("플레이어 " + i + ": ");
            stringBuilder.append("카드 ");
            stringBuilder.append(players[i].getCardNumbers());
            stringBuilder.append("개, ");
            stringBuilder.append("코인 ");
            stringBuilder.append(players[i].getCoins());
            stringBuilder.append("개");
            stringBuilder.append("\n");
        }

        String payload = stringBuilder.toString();
        updateAllPlayers(payload);
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

    void updateAllPlayers(Object obj) {
        for (Player player: players) {
            simpMessagingTemplate.convertAndSendToUser(player.getName(), destination, obj);
        }
    }
}
