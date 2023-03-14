package com.soldesk2.springbootcoup.game.web;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.soldesk2.springbootcoup.game.Action;
import com.soldesk2.springbootcoup.game.Card;
import com.soldesk2.springbootcoup.game.CounterAction;
import com.soldesk2.springbootcoup.game.Player;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

public class WebGame {

    private final Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(this.getClass());
    private SimpMessagingTemplate simpMessagingTemplate;

    private final Random random;
    private StringBuilder stringBuilder;
    public final ConcurrentLinkedQueue<Entry<String, String>> playerResponseQueue = new ConcurrentLinkedQueue<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    private static final long ACTION_TIMEOUT_SECONDS = 60;

    // 게임 시작시 접속한 플레이어들 이름 목록
    String[] playerNames;

    // 살아있는 플레이어 목록
    private Player[] players;
    private List<Card> deck;
    private final String destination;

    public WebGame(String destination, SimpMessagingTemplate simpMessagingTemplate) {
        this.stringBuilder = new StringBuilder();
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.random = new Random();
        this.destination = destination;

        logger.setLevel(Level.DEBUG);
        logger.debug("게임 생성됨.");
    }

    public void play(String[] playerNames) {
        this.playerNames = playerNames;

        int numberOfPlayers = playerNames.length;
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

        // TODO: 서버 딜레이 시뮬레이션용 (완성시 삭제)
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        try {
            update();
            int playerIndex = 0;

            // 게임이 끝날 때까지 반복
            while (alivePlayers() > 1) {
                // 행동할 플레이어를 정한다.
                Player nowPlayer = players[playerIndex];
                // 플레이어가 할 액션을 프론트에 요청한다.
                Action action = getAction(nowPlayer);

                logger.debug("Action chosen : {}, Is action targeted? {}", action, action.targeted);

                // 타겟이 있는 액션이라면 타겟을 요청하여 받는다
                Player target = action.targeted ? getTarget(nowPlayer) : null;

                if (target != null) {
                    if (action.card != null) {
                        log("%s가 카드 %s를 사용하여 %s에게 %s를 함", nowPlayer, action.card, target, action);
                    } else {
                        log("%s가 %s에게 %s를 함", nowPlayer, target, action);
                    }
                } else {
                    if (action.card != null) {
                        log("%s가 카드 %s를 사용하여 %s를 함", nowPlayer, action.card, action);
                    } else {
                        log("%s가 %s를 함", nowPlayer, action);
                    }
                }

                // 타겟이 있을지 모르는 액션을 실행한다.
                doAction(action, nowPlayer, target);

                // 카드가 0인 플레이어를 제거한다.
                for (int i = 0; i < players.length; i++) {
                    if (players[i] != null && players[i].getCardNumbers() == 0) {
                        log("플레이어 %s가 사망했다.", players[i].getName());
                        players[i] = null;
                    }
                }

                update();

                // 행동할 플레이어를 다음 플레이어로 변경한다.
                do {
                    playerIndex = (playerIndex + 1) % players.length;
                } while (players[playerIndex] == null);

            }

            // 남아 있는 플레이어가 승리한다.
            playerWon(Arrays.stream(players).filter(Objects::nonNull).findFirst().orElseThrow(IllegalStateException::new));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    Player getTarget(Player player) throws InterruptedException {
        List<Player> options = new ArrayList<>(Arrays.asList(players));

        // 죽은 플레이어는 대상이 될 수 없음
        options.removeIf(Objects::isNull);
        // 자기 자신은 대상이 될 수 없음
        options.remove(player);

        return getChoice(player, options.toArray(new Player[0]), "대상을 선택하세요.");
    }

    void log(String format, Object... args) {
        String logMessage = String.format(format, args);
        logger.info(logMessage);
        Message message = new Message(MessageType.LOG, logMessage, logMessage);
        sendToAllPlayers(message);
    }

    /**
     * 게임의 승자를 알린다.
     * 
     * @param player 게임에 승리한 플레이어
     */
    void playerWon(Player player) {
        logger.info("플레이어 {}가 승리했다.", player.getName());
        this.sendToAllPlayers("플레이어 " + player.getName() + "가 승리했다.");
    }

    /**
     * 게임 안에 아직 패배하지 않은 플레이어의 수를 출력한다.
     * 
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
        Arrays.stream(players).forEach(this::updatePlayer);
    }


    void updatePlayer(Player player) {
        if (player == null) {
            return;
        }
        
        Update update = new Update(player, players);
        Message message = new Message(MessageType.UPDATE, update, update.toString());

        sendMessage(player, message);
    }



    boolean doAction(Action action, Player player, Player target) throws InterruptedException {
        return doAction(action, action.card, player, target);
    }

    boolean doAction(Action action, Card card, Player player, Player target) throws InterruptedException {
        // 돈이 필요한 액션은 돈을 먼저 낸다.
        payCost(action, player);
        CounterAction counterAction = getCounterAction(action, card, player, target);

        if (counterAction != null) {
            if (counterAction.isBlock) {
                log("%s가 %s로 블록함", counterAction.player, counterAction.card);
                // 이 블록도 챌린지될수 있음
                if (doAction(Action.Block, counterAction.card, counterAction.player, player)) {
                    log("블록 성공!");
                    return false;
                } else {
                    log("블록 실패!");
                    // 블록이 챌린지되고 실패했다면 타겟이 죽었을 가능성이 있다.
                    if (target != null && target.getCardNumbers() == 0)
                        return false;
                }
            } else {
                log("%s가 챌린지한다", counterAction.player);

                boolean lying = !player.hasCard(card);
                if (lying) {
                    log("챌린지 성공!");
                    sacrificeCard(player);
                } else {
                    log("챌린지 실패!");
                    sacrificeCard(counterAction.player);

                    // 플레이어가 거짓말을 하지 않았으므로 카드를 덱에 넣고 섞은 후 다시 뽑는다.
                    player.removeCard(card);
                    deck.add(card);
                    shuffleDeck();
                    player.addCard(drawOne());
                }
                
                if (lying || (target != null && target.getCardNumbers() == 0)) {
                    return false;
                }
            }
        }

        handleAction(action, player, target);
        return true;
    }

    private void handleAction(Action action, Player player, Player target) throws InterruptedException {
        logger.debug("Action {} by {} on {}", action, player, target);

        switch (action) {
            case Income:
                player.coins++;
                break;

            case ForeignAid:
                player.coins += 2;
                break;

            case Tax:
                player.coins += 3;
                break;

            case Assassinate:
            case Coup:
                sacrificeCard(target);
                break;

            case Exchange:
                exchange(player);
                break;

            case Steal:
                int removed = Math.min(Objects.requireNonNull(target).coins, 2);
                target.coins -= removed;
                player.coins += removed;
                break;

            case Block:
                break;
        }
    }

    private void exchange(Player player) throws InterruptedException {
        // 지금 플레이어 카드의 복사본을 만든다.
        ArrayList<Card> copy = new ArrayList<>(player.getCards());

        // 2장을 더 뽑는다.
        copy.add(drawOne());
        copy.add(drawOne());

        // 프론트에 어떤 카드를 버릴지 선택하게 한다.
        ArrayList<Card> toKeep = doExchange(player, copy);
        player.setCards(toKeep);

        // 버린 카드를 덱에 넣고 섞는다.
        copy.removeAll(toKeep);
        deck.addAll(copy);
        shuffleDeck();
    }
    
    /**
     * 카드를 2장 버리게 한다.
     * @param player 플레이어
     * @param cards 카드를 2장 버리게 할 카드들
     * @return 버린 카드를 제외한 카드들
     */
    private ArrayList<Card> doExchange(Player player, ArrayList<Card> cards) throws InterruptedException {
        // 카드를 2장 버리게 한다.

        ArrayList<Card> copy = new ArrayList<>(cards);

        for (int i = 1; i <= 2; i++) {
            Card card = getChoice(player, copy.toArray(new Card[0]), "버릴 카드를 선택하세요 2장 중" + i + "번째");
            copy.remove(card);
        }

        return copy;
    }

    private void sacrificeCard(Player player) throws InterruptedException {
        Card card = getCardToSacrifice(player);
        log("%s가 %s를 버림", player, card);
        player.removeCard(card);
    }

    private Card getCardToSacrifice(Player player) throws InterruptedException {
        return getChoice(player, player.getCards().toArray(new Card[0]), "버릴 카드를 선택하세요");
    }

    /**
     * 액션에 필요한 돈을 낸다.
     * 
     * @param action 액션
     * @param player 액션을 취하는 플레이어
     */
    void payCost(Action action, Player player) {
        if (action == Action.Assassinate) {
            player.coins -= 3;
        }

        if (action == Action.Coup) {
            player.coins -= 7;
        }
    }

    /**
     * 플레이어가 액션을 선택한다.
     * 
     * @param player 플레이어
     * @return 선택한 액션
     */
    Action getAction(Player player) throws InterruptedException {
        ArrayList<Action> options = new ArrayList<>();

        // 코인 10개 이상일시 쿠만 가능
        if (player.coins >= 10) {
            options.add(Action.Coup);
            return getChoice(player, options.toArray(new Action[0]), "액션을 고르시오");
        }

        options.add(Action.Income);
        options.add(Action.ForeignAid);
        if (player.coins >= 7) {
            options.add(Action.Coup);
        }

        if (player.coins >= 3) {
            options.add(Action.Assassinate);
        }
        options.add(Action.Exchange);
        options.add(Action.Steal);

        return getChoice(player, options.toArray(new Action[0]), "액션을 고르시오.");
    }

    /**
     * 플레이어가 선택할 수 있는 선택지를 출력하고 선택지 중 하나를 선택하도록 한다.
     * 
     * @param <T>     선택지의 타입
     * @param player  선택을 할 플레이어
     * @param choices 가능한 선택지
     * @param prompt  선택지를 출력할 때 출력할 메시지
     * @return 플레이어가 선택한 값.
     */
    private <T> T getChoice(Player player, T[] choices, String prompt) throws InterruptedException {
        // TODO: JSON 형식으로 메시지를 보내도록 수정.

        logger.debug("Getting Choice from player {}... options: {}. prompt: {}", player, choices, prompt);
        String[] choicesString = Arrays.stream(choices).map(Object::toString).toArray(String[]::new);

        Message message = new Message(MessageType.CHOICE, choicesString, prompt);
        sendMessage(player, message);

        Future<String> futureResponse = executorService.submit(
            () -> {
                try {
                    while (true) {
                        Entry<String, String> s = playerResponseQueue.poll();
                        if (s != null && s.getKey().equals(player.getName())) {
                            return s.getValue();
                        }
                        Thread.sleep(150);
                    }
                } catch (Exception e) {
                    logger.warn("플레이어 {}로부터 응답을 받는 도중 오류 발생", player.getName(), e);
                    return null;
                }
                
            }
        );

        try {
            String response = futureResponse.get(ACTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            for (int i = 0; i < choices.length; i++) {
                if (choices[i].toString().equals(response)) {
                    logger.debug("Choice from player {} is {}", player, choices[i]);
                    return choices[i];
                }
            }

        } catch (TimeoutException e) {
            logger.warn("플레이어 {}로부터 응답을 받는 도중 시간 초과 발생", player.getName(), e);
            futureResponse.cancel(true);
            return null;
        } catch (ExecutionException e) {
            logger.warn("플레이어 {}로부터 응답을 받는 도중 오류 발생", player.getName(), e);
            futureResponse.cancel(true);
            return null;
        }

        logger.warn("플레이어 {}로부터 옵션들 {}로부터 올바르지 않은 응답을 받음.", player.getName(), choices);
        return null;
    }

    /**
     * 플레이어에게 메시지를 전달한다.
     * 
     * @param player 메시지를 전달할 플레이어
     * @param obj    전달할 메시지
     */
    void sendMessage(Player player, Object obj) {
        if (obj == null) {
            logger.warn("플레이어 {}에게 전달할 메시지가 null임.", player.getName());
            return;
        }

        logger.info("플레이어 {}에게 메시지를 전달함. 메시지: {}", player.getName(), obj.toString());
        simpMessagingTemplate.convertAndSendToUser(player.getName(), destination, obj);
    }

    /**
     * 덱에서 카드를 한장 뽑는다.
     * 
     * @return 뽑은 카드
     */
    Card drawOne() {
        return this.deck.remove(this.deck.size() - 1);
    }

    /**
     * 덱을 셔플한다.
     */
    void shuffleDeck() {
        Collections.shuffle(this.deck, this.random);
    }

    /**
     * 덱에 카드를 추가한다.
     * 
     * @param card 추가할 카드
     */
    void addCardToDeck(Card card) {
        deck.add(card);
    }

    /**
     * 모든 플레이어에게 메시지를 전달한다.
     * 
     * @param obj 전달할 메시지의 payload
     */
    void sendToAllPlayers(Object obj) {
        for (String playername : playerNames) {
            simpMessagingTemplate.convertAndSendToUser(playername, destination, obj);
        }
    }

    CounterAction getCounterAction(Action action, Card card, Player player, Player target) throws InterruptedException {
        // 카운터가 불가능한 액션인 경우 null을 반환한다.
        if (action != Action.ForeignAid && card == null) {
            return null;
        }
        Map<Future<String>, Player> futureMap = new HashMap<>();
        Map<String, Card> cardMap = new HashMap<>();

        String message;
        if (card != null) {
            // 카드를 사용 하므로 어떤 카드를 사용하는지 보여준다.
            message = String.format("%s는 %s를 하기 위해 %s를 가지고 있다고 주장한다.", player.getName(), action.name(), card.name());
            if (target != null) {
                // 타겟이 있는 액션인 경우 타겟을 보여준다.
                message += String.format("%n목표 : %s", target.getName());
            }
        } else {
            // 카드를 사용하지 않는 액션
            message = String.format("%s는 %s를 하려고 한다.", player.getName(), action.name());
        }

        Player[] players = Arrays.stream(this.players).filter(Objects::nonNull).toArray(Player[]::new);

        // 원조만이 블락당할 수 있다.
        if (action == Action.ForeignAid) {
            // 플레이어들마다 Future를 받는다.
            for (Player p : players) {
                // 액션을 수행하는 플레이어는 카운터할 수 없다.
                if (p != player) {
                    Future<String> f = getChoiceAsync(p, new String[] { "Block (Duke)", "Pass" }, message);
                    futureMap.put(f, p);
                }
            }
        } else {
            List<String> choices = new ArrayList<>();
            // 이 액션을 카운터할 수 있는 카드를 넣는다.
            for (Card c : action.blockedBy) {
                String s = String.format("Block (%s)", c);
                choices.add(s);
                cardMap.put(s, c);
            }

            choices.add("Challenge");
            choices.add("Pass");

            for (Player p : players) {
                if (p != player && p != target) {
                    // 플레이어가 타겟도 행동을 취하는 플레이어도 아닌 경우 챌린지나 패스를 할 수 있다.
                    String[] options = { "Challenge", "Pass" };
                    Future<String> f = getChoiceAsync(p, options, message);
                    futureMap.put(f, p);
                } else if (p == target) {
                    // 타겟이면 위에서 넣은 옵션들을 사용한다.
                    Future<String> f = getChoiceAsync(p, choices.toArray(new String[0]), message);
                    futureMap.put(f, p);
                }
            }
        }

        // 받은 응답들 중에서 패스가 아닌 첫 응답을 찾는다. (모두가 패스했다면 null을 반환한다.)
        Future<String> future = getFirst(futureMap.keySet(), s -> !s.equalsIgnoreCase("pass"));

        // 모든 플레이어에게 선택이 끝났다는걸 전달한다.
        stopChoice();

        // 완료되지 않은 Future를 모두 취소한다.
        futureMap.keySet().forEach(f -> f.cancel(true));

        // 어떤 플레이어가 선택했다면 지금 반환한다.
        if (future != null) {
            try {
                String choice = future.get();
                Card c = cardMap.get(choice);
                return new CounterAction(c != null, futureMap.get(future), c);
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        // 아무 플레이어도 카운터하지 않았다면 null을 반환한다.
        return null;
    }

    private <T> Future<T> getChoiceAsync(Player player, T[] choices, String prompt) {
        return executorService.submit(() -> getChoice(player, choices, prompt));
    }

    /**
     * 플레이어들에게 선택이 끝났다는 메시지를 전달한다.
     */
    void stopChoice() {
        String userMessage = "선택이 끝났다.";
        Message message = new Message(MessageType.UPDATE, userMessage, userMessage);
        sendToAllPlayers(message);
    }

    <T> Future<T> getFirst(Collection<Future<T>> futures, Predicate<T> predicate) throws InterruptedException {
        while (!futures.isEmpty()) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }

            for (Iterator<Future<T>> iterator = futures.iterator(); iterator.hasNext();) {
                Future<T> future = iterator.next();
                // Future가 완료되었으면 결과를 반환한다.
                if (future.isDone()) {
                    try {
                        // Predicate가 true이면 결과를 반환한다.
                        if (predicate.test(future.get())) {
                            return future;
                        } else {
                            // Predicate가 false이면 Future를 제거한다.
                            iterator.remove();
                        }
                    } catch (ExecutionException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            Thread.sleep(100);
        }

        // 모든 Future가 완료되었지만 Predicate가 true인 결과가 없으면 null을 반환한다.
        return null;
    }

    
    private static class Update {
        public Card[] localPlayerCards;
        public PlayerState[] players;

        public Update(Player localPlayer, Player[] players) {
            this.localPlayerCards = localPlayer.getCards().toArray(new Card[0]);
            this.players = Arrays.stream(players)
                                 .filter(Objects::nonNull)
                                 .map(PlayerState::new)
                                 .toArray(PlayerState[]::new);
        }

        @Override
        public String toString() {
            String message = "";
            message += "당신의 카드 : " + Arrays.toString(localPlayerCards) + "\n";

            for (int i = 0; i < players.length; i++) {
                message += "Player " + i + " : " + players[i].name + " (" + players[i].coins + " coins, " + players[i].cardNumbers + " cards) ";
            }

            return message;
        }
    }

    private static class PlayerState {
        public String name;
        public int coins;
        public int cardNumbers;

        public PlayerState(Player player) {
            this.name = player.getName();
            this.coins = player.coins;
            this.cardNumbers = player.getCardNumbers();
        }
    }

}
