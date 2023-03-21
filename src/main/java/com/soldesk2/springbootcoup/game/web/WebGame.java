package com.soldesk2.springbootcoup.game.web;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;

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
    public Queue<Map.Entry<String, String>> actionQueue;

    private static final long ACTION_TIMEOUT_SECONDS = 45;
    private volatile boolean gameRunning = false;

    // 게임 시작시 접속한 플레이어들 이름 목록
    String[] playerNames;

    // 살아있는 플레이어 목록
    private Player[] players;
    private List<Card> deck;
    private final String destination;

    public WebGame(String destination, SimpMessagingTemplate simpMessagingTemplate) {
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.random = new Random();
        this.destination = destination;
        this.actionQueue = new ArrayDeque<>();

        logger.setLevel(Level.DEBUG);
        logger.debug("게임 생성됨.");
    }

    public void play(String[] playerNames) {
        this.playerNames = playerNames;

        Message message = new Message(MessageType.START, "게임 시작", "게임 시작");
        sendToAllPlayers(message);

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

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        this.gameRunning = true;

        try {
            update();
            int playerIndex = 0;

            // 게임이 끝날 때까지 반복
            while (alivePlayers() > 1 && gameRunning) {
                // 행동할 플레이어를 정한다.
                Player nowPlayer = players[playerIndex];
                // 플레이어가 할 액션을 프론트에 요청한다.
                
                logger.info("플레이어 {}의 턴", nowPlayer.getName());
                log("플레이어 %s의 턴", nowPlayer.getName());
                
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
            playerWon(Arrays.stream(players).filter(Objects::nonNull).findFirst()
                    .orElseThrow(IllegalStateException::new));

            this.endGame();

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

    /**
     * 액션을 실행한다.
     * 
     * @param action 액션
     * @param card   카드
     * @param player 플레이어
     * @param target 타겟
     * @return 액션이 성공적으로 실행되었는지 여부
     * @throws InterruptedException
     */
    boolean doAction(Action action, Card card, Player player, Player target) throws InterruptedException {

        // 돈이 필요한 액션은 돈을 먼저 낸다.
        payCost(action, player);
        
        // 누군가 챌린지 했는지 확인한다.
        CounterAction counterAction = getCounterAction(action, card, player, target, false);

        // 누군가 카운터를 하지 않았으면 블락을 확인한다.
        if (counterAction == null) {

            CounterAction blockCounterAction = getCounterAction(action, card, player, target, true);

            if (blockCounterAction == null) {
                // 누군가 블락을 하지 않았으면 액션을 실행한다.
                handleAction(action, player, target);
                return true;
            }

            // 누군가 블락을 했다.

            if (action == Action.ForeignAid) {
                // 블락의 챌린지를 받는다.
                if (doAction(Action.Block, Card.Duke, blockCounterAction.player, player)) {
                    log("블록 성공!");
                    return false;
                } else {
                    log("블록 실패!");
                    handleAction(action, player, target);
                    return true;
                }

            } else {
                log("%s가 %s로 블록함", blockCounterAction.player, blockCounterAction.card);

                // 블락의 챌린지를 받는다.
                if (doAction(Action.Block, blockCounterAction.card, blockCounterAction.player, player)) {
                    log("블록 성공!");
                    return false;
    
                } else {
                    log("블록 실패!");
                    // 블록이 챌린지되고 실패했다면 타겟이 죽었을 가능성이 있다.
                    if (target != null && target.getCardNumbers() == 0) {
                        return false;
                    }
                    
                    handleAction(action, player, target);
                    return true;
                }

            }
            
        }

        log("%s가 챌린지한다", counterAction.player);

        boolean lying = !player.hasCard(card);

        if (lying) {
            log("%s의 챌린지 성공! %s은 카드를 한장 버려야 한다.", counterAction.player, player);
            sacrificeCard(player);

            // 챌린지에 성공해 액션이 실패했으므로 종료
            return false;

        } else {
            log("%s의 챌린지 실패! %s은 카드를 한장 버려야 한다.", counterAction.player, counterAction.player);
            sacrificeCard(counterAction.player);

            // 플레이어가 거짓말을 하지 않았으므로 카드를 덱에 넣고 섞은 후 다시 뽑는다.
            player.removeCard(card);
            deck.add(card);
            shuffleDeck();
            player.addCard(drawOne());

            // 타겟이 죽었는지 체크
            if (target.getCardNumbers() == 0) {
                return false;
            }

            // 챌린지에 실패해 액션이 성공했으므로 다시 액션을 실행한다.
            handleAction(action, player, target);
            return true;
        }
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
     * 
     * @param player 플레이어
     * @param cards  카드를 2장 버리게 할 카드들
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

    /**
     * 플레이어가 카드를 1장 버리게 한다.
     * 
     * @param player 카드를 버리게 할 플레이어
     * @throws InterruptedException
     */
    private void sacrificeCard(Player player) throws InterruptedException {
        Card card = getCardToSacrifice(player);
        log("%s가 %s를 버림", player, card);
        player.removeCard(card);
    }

    /**
     * 플레이어에게 버릴 카드를 선택하게 한다.
     * 
     * @param player 카드를 선택하게 할 플레이어
     * @return 선택한 카드
     * @throws InterruptedException
     */
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

        options.add(Action.Tax);

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

    private <T> T getChoice(Player player, T[] choices, String prompt) {
        // TODO: JSON 형식으로 메시지를 보내도록 수정.

        logger.debug("Getting Choice from player {}... options: {}. prompt: {}", player, choices, prompt);
        String[] choicesString = Arrays.stream(choices).map(Object::toString).toArray(String[]::new);

        Message message = new Message(MessageType.CHOICE, choicesString, prompt);
        sendMessage(player, message);

        // 선택지를 선택할 때까지 대기한다.
        while (true) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Error while waiting for player {} to choose.", player, e);
            }

            Map.Entry<String, String> entry = this.actionQueue.poll();

            if (entry == null) {
                continue;
            }

            String playerName = entry.getKey();
            String action = entry.getValue();

            if (!playerName.equals(player.getName())) {
                continue;
            }

            logger.debug("Player {} inputted action {}", playerName, action);

            for (int i = 0; i < choices.length; i++) {
                if (choices[i].toString().equals(action)) {
                    return choices[i];
                }
            }

            logger.warn("Player {} inputted invalid action {}", playerName, action);
        }
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

    /**
     * 카운터 액션을 반환한다. 카운터 액션이 없는 경우 null을 반환한다.
     * 
     * @param action 액션
     * @param card   카드
     * @param player 플레이어
     * @param target 타겟
     * @return 카운터 액션
     * @throws InterruptedException
     */
    CounterAction getCounterAction(Action action, Card card, Player player, Player target, boolean isBlock)
            throws InterruptedException {

        // 카운터가 불가능한 액션인 경우 null을 반환한다.
        if (action != Action.ForeignAid && card == null) {
            return null;
        }

        if (action == Action.ForeignAid && !isBlock) {
            return null;
        }

        if (isBlock && action.blockedBy.isEmpty()) {
            return null;
        }

        Map<Player, List<String>> choicesMap = new HashMap<>();
        Map<String, Card> cardMap = new HashMap<>();
        Player[] players = Arrays.stream(this.players).filter(Objects::nonNull).toArray(Player[]::new);

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

        ArrayList<String> choices = new ArrayList<>();

        if (!isBlock) {
            // 블락이 아니므로 챌린지나 패스만 체크한다.

            choices.add("Challenge");
            choices.add("Pass");

            for (Player p : players) {
                // 액션을 수행하는 플레이어는 카운터할 수 없다.
                if (p != player) {
                    sendMessage(p, new Message(MessageType.CHOICE, choices.toArray(new String[0]), message));
                    choicesMap.put(p, choices);
                }
            }
        } else {
            // 블락일 경우

            // 원조만이 모두에게 블락당할 수 있다.
            if (action == Action.ForeignAid) {
                choices.add("Block (Duke)");
                choices.add("Pass");

                // 플레이어들마다 Future를 받는다.
                for (Player p : players) {
                    // 액션을 수행하는 플레이어는 카운터할 수 없다.
                    if (p != player) {
                        sendMessage(p, new Message(MessageType.CHOICE, choices.toArray(new String[0]), message));
                        choicesMap.put(p, choices);
                    }
                }
            } else {
                // 이 액션을 카운터할 수 있는 카드를 넣는다.
                for (Card c : action.blockedBy) {
                    String s = String.format("Block (%s)", c);
                    choices.add(s);
                    cardMap.put(s, c);
                }

                // 타겟에게 블락 선택지를 보낸다.
                sendMessage(target, new Message(MessageType.CHOICE, choices.toArray(new String[0]), message));
                choicesMap.put(target, choices);
            }
        }

        // 선택한 선택지를 저장할 맵 생성 (플레이어, 선택지)
        Map<Player, String> chosenMap = new HashMap<>(players.length - 1); // 액션을 수행하는 플레이어는 제외

        for (Player p : players) {
            if (p != player) {
                chosenMap.put(p, null);
            }
        }

        while (true) {
            Thread.sleep(50);

            Map.Entry<String, String> entry = this.actionQueue.poll();

            if (entry != null) {
                String playerName = entry.getKey();
                String choice = entry.getValue();

                logger.debug("Action queue polled : name {} : choice {}", playerName, choice);

                Optional<Player> p = Arrays.stream(players).filter(f -> f.getName().equals(playerName)).findFirst();

                if (!p.isPresent()) {
                    logger.info("Player {} not found while queue polling", playerName);
                    continue;
                }

                Player responsePlayer = p.get();
                sendMessage(responsePlayer, "Your choice " + choice + " reached the game server");

                if (choicesMap.get(responsePlayer).contains(choice)) {
                    chosenMap.put(responsePlayer, choice);

                    // Check if it was a choice other than pass
                    if (!choice.equalsIgnoreCase("pass")) {
                        stopChoice();
                        Card c = cardMap.get(choice);
                        return new CounterAction(c != null, responsePlayer, c);
                    }

                } else {
                    logger.info("Player {} chose invalid choice {}", playerName, choice);
                    Message msg = new Message(MessageType.ERROR, choicesMap.get(responsePlayer),
                            "You chose invalid choice :" + choice + " Your choices: " + choicesMap.get(responsePlayer));
                    sendMessage(responsePlayer, msg);
                    continue;
                }

                if (chosenMap.values().stream().allMatch(Objects::nonNull)) {
                    break;
                }
            }
        }

        // 모든 플레이어에게 선택이 끝났다는걸 전달한다.
        stopChoice();
        return null;
    }

    /**
     * 플레이어들에게 선택이 끝났다는 메시지를 전달한다.
     */
    void stopChoice() {
        String userMessage = "선택이 끝났다.";
        Message message = new Message(MessageType.UPDATE, userMessage, userMessage);
        sendToAllPlayers(message);
    }

    /**
     * 플레이어에게 자신의 카드만 볼 수 있도록 메시지를 담기 위해 사용하는 클래스
     */
    private static class Update {
        public Card[] localPlayerCards;
        public PlayerState[] players;
        public String userName;
        public int coins;

        public Update(Player localPlayer, Player[] players) {
            this.userName = localPlayer.getName();
            this.coins = localPlayer.coins;
            this.localPlayerCards = localPlayer.getCards().toArray(new Card[0]);
            this.players = Arrays.stream(players)
                    .filter(Objects::nonNull)
                    .map(PlayerState::new)
                    .toArray(PlayerState[]::new);
        }

        @Override
        public String toString() {
            String message = "";
            message += "당신의 이름: " + userName + " 코인 수: " + coins + "\n";
            message += "당신의 카드 : " + Arrays.toString(localPlayerCards) + "\n";

            for (int i = 0; i < players.length; i++) {
                message += "Player " + i + " : " + players[i].name;

                if (players[i].name.equals(userName)) {
                    message += " (당신)";
                }

                message += " (" + players[i].coins + " coins, "
                        + players[i].cardNumbers + " cards) ";
            }

            return message;
        }
    }

    /**
     * 플레이어의 상태를 담기 위해 사용하는 클래스
     */
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

    public void endGame() {
        this.gameRunning = false;
        this.players = null;
    }

}
