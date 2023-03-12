package com.soldesk2.springbootcoup.game.web;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.soldesk2.springbootcoup.game.Action;
import com.soldesk2.springbootcoup.game.Card;
import com.soldesk2.springbootcoup.game.Player;
import com.soldesk2.springbootcoup.game.Action.ActionType;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;


public class WebGame {
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
            String msg = "Your Coin: " + players[i].getCoins() + "\n" +
                         "Your Deck: " + players[i].getCards() + "\n" +
                         "Actions: " + getAction(players[i]);
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
            stringBuilder.append(players[i].getName());
            stringBuilder.append(" ");
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
    ArrayList<Action> getAction(Player player) {
        ArrayList<Action> actions = new ArrayList<>();

        // 10코인 이상일 경우 쿠밖에 못함
        if (player.getCoins() >= 10) {
            actions.add(new Action(ActionType.Coup));
            return actions;
        }

        actions.add(new Action(ActionType.Income));
        actions.add(new Action(ActionType.ForeignAid));
        

        // 3코인 이상이면 암살 가능
        if (player.getCoins() >= 3) {
            actions.add(new Action(ActionType.Assassinate, !player.hasCard(Card.Assassin)));
        }

        // 7코인 이상이면 쿠 가능
        if (player.getCoins() >= 7) {
            actions.add(new Action(ActionType.Coup));
        }

        // 직업 카드는 bluff인지 계산
        actions.add(new Action(ActionType.Tax, !player.hasCard(Card.Duke)));
        actions.add(new Action(ActionType.Steal, !player.hasCard(Card.Captain)));
        actions.add(new Action(ActionType.Exchange, !player.hasCard(Card.Ambassador)));

        return actions;
    }

    /**
     * 선택 액션을 수행한다
     * 
     * @param player
     * @param action
     */
    void getdoAction(Player player, Action action){

        ActionType type = action.getActionType();

        switch(type){
            case Income:
                player.setCoins(player.getCoins()+1);
                break;

            case ForeignAid:
                player.setCoins(player.getCoins()+2);
                break;

            case Tax:
                player.setCoins(player.getCoins()+3);
                break;

            case Coup:
            case Assassinate:
                Player target_kill = getTarget(player);
                cardDown(target_kill);
                break;
            
            case Exchange:
                changeCard(player);
                break;

            case Steal:
                stealCoin(player);
                break;

            case Block:
                blockAction();
                break;
        }

    }


    /**
     * 사망한 플레이어와 자신을 제외하여 타겟으로 설정 가능한 플레이어들의 배열 생성 후 
     * 타겟 선택하는 메서드로 이동
     * @param player 타겟이 존재하는 액션을 사용하여 액션에 대한 타겟을 선택하는 플레이어
     * @return 선택 플레이어, 선택가능한 타겟 배열, 메세지 getChoiceTarget으로 전송하여 타겟 선택, 선택한 플레이어를 반환
     */
    Player getTarget(Player player) {
        List<Player> target = new ArrayList<>(Arrays.asList(players));
        target.remove(null);
        target.remove(player);

        return getChoiceTarget(player, target.toArray(new Player[0]), "대상을 지정해주세요");
    }

    /**
     * 선택할 수 있는 플레이어 중 타겟 선택
     * 
     * @param player 타겟을 선택하는 플레이어
     * @param choices 고를 수 있는 플레이어의 배열
     * @param message 선택 메세지
     * @return 선택한 플레이어를 반환
     */
    Player getChoiceTarget(Player player, Player[] choices, String message){
        String[] choiceArray = new String[choices.length];
        for(int i=0; i<choices.length; i++){
            choiceArray[i] = choices[i].toString();
        }

        String userMessage = message + "\n" + choiceArray;

        String playername = player.toString();

        simpMessagingTemplate.convertAndSendToUser(playername, destination, userMessage);

        Player result = null;

        while(result == null){
            String getresult = "전달받은 값";

            for(int i=0; i<choices.length; i++){
                if(choiceArray[i].equals(getresult)) result = choices[i];
            }
            if(result == null){
                simpMessagingTemplate.convertAndSendToUser(playername, destination, userMessage);
            }
        }

        return result;
    }

    

    /**
     * 암살자, Coup에 의한 카드 버리기 / 도전 의심 블록에서도 사용될 예정
     * @param target 카드를 버리게 되는 플레이어
     */
    void cardDown(Player target){

        
        Card[] targetcardlist = target.getCardList();
        String[] cardcount = new String[target.getCardList().length];
        for(int i=0; i<cardcount.length; i++){
            cardcount[i] = target.getCardList()[i].toString();
        }
        
        String userMessage = "버릴 카드 선택 \n" + cardcount;
        String targetname = target.toString();
        
        simpMessagingTemplate.convertAndSendToUser(targetname, destination, userMessage);

        Card result = null;

        while(result == null){
            String getresult = "전달받은 값";

            for(int i=0; i<cardcount.length; i++){
                if(cardcount[i].equals(getresult)) result = targetcardlist[i];
            }
            if(result == null){
                simpMessagingTemplate.convertAndSendToUser(targetname, destination, userMessage);
            }
        }

        target.removeCard(result);
    }

    /**
     * 외교관에 의한 카드 교체
     * @param player 카드를 교체할 플레이어
     */
    void changeCard(Player player){
        List<Card> cardlist = player.getCards();
        cardlist.add(drawOne());
        cardlist.add(drawOne());

        int cardsize = player.getCardNumbers();

        String playername = player.toString();
        String userMessage = "카드" + cardsize +"개 선택 (,로 개수 구분)\n" + cardlist; 

        simpMessagingTemplate.convertAndSendToUser(playername, destination, userMessage);

        List<Card> result = null;

        while(result == null){
            String getresult = "전달받은 값";
            String []resultArray = getresult.split(",");

            if("전달받은 문자열 수" != String.valueOf(cardsize)){
                simpMessagingTemplate.convertAndSendToUser(playername, destination, userMessage);
            }
            for(int i=0; i<resultArray.length; i++){
                for(int j=0; j<cardsize; j++){
                    if((cardlist.get(j).toString()).equals(resultArray[i])){ 
                        result.add(cardlist.get(j));
                    }
                }
            }
            if(result == null || result.size() != cardsize){
                simpMessagingTemplate.convertAndSendToUser(playername, destination, userMessage);
            }
        }

        player.setCards(result);

    }

    /**
     * 사령관에 의한 코인 강탈
     * @param player 사령관을 사용한 플레이어
     */
    void stealCoin(Player player){
        Player target = getTarget(player);

        if(target.getCoins() >= 2){
            target.setCoins(target.getCoins()-2);
            player.setCoins(player.getCoins()+2);
        }
        else{
            target = getTarget(player);
        }
    }

    /**
     * 액션에 대한 카운터 액션
     */
    void blockAction(){

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
