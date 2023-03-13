package com.soldesk2.springbootcoup.game.web;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.soldesk2.springbootcoup.game.Action;
import com.soldesk2.springbootcoup.game.Action.ActionType;
import com.soldesk2.springbootcoup.game.Card;
import com.soldesk2.springbootcoup.game.Player;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;


public class WebGame {
    private final Random random;
    private StringBuilder stringBuilder;

    protected final Player[] players;

    private final List<Card> deck;
    private final Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(this.getClass());
    private final String destination;
    private final List<String> allowedActions;

    private SimpMessagingTemplate simpMessagingTemplate;

    public WebGame(String[] playerNames, String destination, SimpMessagingTemplate simpMessagingTemplate) {
        logger.setLevel(Level.DEBUG);
        logger.debug("Setting game up... playerNames : {} destination : {}", playerNames, destination);

        this.allowedActions = new ArrayList<String>();
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

        play();
    }

    public void play() {

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        
        update();

        for (int i = 0; i < this.players.length; i++) {
            String user = players[i].getName();
            String msg = "Your Coin: " + players[i].getCoins() + "\n" +
                         "Your Deck: " + players[i].getCards() + "\n" +
                         "Actions: " + getAction(players[i]);
            logger.info("Sending {} to {}", msg, user);
            simpMessagingTemplate.convertAndSendToUser(user, destination, msg);
        }

        Player nowPlayer = players[0];
        ArrayList<Action> actions = getAction(nowPlayer);
        allowedActions.clear();
        
        for (Action action : actions) {
            allowedActions.add(action.getActionType().toString());
        }
    
        String userMessage = "Your Coin: " + nowPlayer.getCoins() + "\n" +
                             "Your Deck: " + nowPlayer.getCards() + "\n" +
                             "Actions: " + allowedActions;
                            
        Message message = new Message(MessageType.UPDATE, actions, userMessage);
        simpMessagingTemplate.convertAndSendToUser(nowPlayer.getName(), destination, message);
    }

    public boolean makeMove(String username, String action) {
        if (Arrays.stream(players).anyMatch(player -> player.getName().equals(username))) {
            
            if (allowedActions.contains(action)) {
                // TODO : Change game state and continue?

                logger.info("Player {} made move {}", username, action);
                allowedActions.clear();

                this.updateAllPlayers("Player " + username + " made move " + action);
                return true;
            }
            
            logger.info("Player {} made invalid move {}", username, action);

            Message message = new Message(MessageType.ERROR, allowedActions, "잘못된 액션입니다. 현재 가능한 액션 : " + allowedActions);
            simpMessagingTemplate.convertAndSendToUser(username, destination, message);
            return false;

        } else {
            logger.info("Player {} is not in game", username);
            return false;
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
            actions.add(new Action(ActionType.Assassinate, player.hasCard(Card.Assassin)));
        }

        // 7코인 이상이면 쿠 가능
        if (player.getCoins() >= 7) {
            actions.add(new Action(ActionType.Coup));
        }

        // 직업 카드는 인지 계산
        actions.add(new Action(ActionType.Tax, player.hasCard(Card.Duke)));
        actions.add(new Action(ActionType.Steal, player.hasCard(Card.Captain)));
        actions.add(new Action(ActionType.Exchange, player.hasCard(Card.Ambassador)));

        return actions;
    }

    /**
     * 선택 액션을 수행, blockAction에 따라 액션 차단 가능
     * 
     * @param player 액션을 선택한 플레이어
     * @param action 실행시킬 액션
     * @param card 액션에 사용되는 카드
     */
    void getdoAction(Player player, Action action, Card card){

        ActionType type = action.getActionType();

        switch(type){
            case Income:
                player.setCoins(player.getCoins()+1);
                break;

            case ForeignAid:
                if(!blockAction(player, action, card))player.setCoins(player.getCoins()+2);
                break;

            case Tax:
                if(!blockAction(player, action, card))player.setCoins(player.getCoins()+3);
                break;

            case Coup:
                player.setCoins(player.getCoins()-7);
                cardDown(getTarget(player));
                break;

            case Assassinate:
                player.setCoins(player.getCoins()-3);
                Player target_kill = getTarget(player);
                if(!blockAction(player, target_kill, action, card)){
                    if(target_kill == null){
                        break;
                    }
                    cardDown(target_kill);
                }
                break;
            
            case Exchange:
                if(!blockAction(player, action, card))changeCard(player);
                break;

            case Steal:
                Player target_steal = getStealTarget(player);
                if(!blockAction(player, target_steal, action, card)){
                    if(target_steal == null){
                        break;
                    }
                    stealCoin(player, target_steal);
                }
                break;
        }

    }

    /**
     * 선택한 유저를 제외한 모든 유저의 리스트를 가져오는 메서드
     * @param palyer 리스트에서 제외할 플레이어
     * @return 선택한 플레이어 이외의 모든 플레이어 List
     */
    List<Player> getotherplayers(Player player){
        List<Player> otherplayers = new ArrayList<>(Arrays.asList(players));
        otherplayers.removeAll(null);
        otherplayers.remove(player);

        return otherplayers;
    }

    /**
     * 사망한 플레이어와 자신을 제외하여 타겟으로 설정 가능한 플레이어들의 배열 생성 후 
     * 타겟 선택하는 메서드로 이동
     * @param player 타겟이 존재하는 액션을 사용하여 액션에 대한 타겟을 선택하는 플레이어
     * @return 선택 플레이어, 선택가능한 타겟 배열, 메세지 getChoiceTarget으로 전송하여 타겟 선택, 선택한 플레이어를 반환
     */
    Player getTarget(Player player) {
        List<Player> target = getotherplayers(player);
        return getChoiceTarget(player, target.toArray(new Player[0]), "대상을 지정해주세요");
    }

    /**
     * 코인이 2개 이상인 플레이어들의 배열 생성 후 타겟 설정
     * @param player Steal액션을 사용하는 플레이어
     * @return 생성한 배열을 타겟 설정 메서드로 이동 하여 타겟 설정
     */
    Player getStealTarget(Player player){
        List<Player> otherplayers = new ArrayList<>(Arrays.asList(players));
        otherplayers.removeAll(null);
        otherplayers.remove(player);
        for(int i=0; i<otherplayers.size(); i++){
            if(otherplayers.get(i).getCoins() >= 2){
                otherplayers.remove(i);
            }
        }

        return getChoiceTarget(player, otherplayers.toArray(new Player[0]), "대상을 지정해주세요");

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

        // 값 전달 받은 후
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


        String diedplayeranme = "";

        for(int i=0; i<players.length; i++){
            if(players[i] != null && players[i].getCardNumbers() == 0){
                players[i] = null;
                diedplayeranme = players[i].getName();
                userMessage = "사망";
                simpMessagingTemplate.convertAndSendToUser(diedplayeranme, destination, userMessage);
            }
        }
        
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
        String userMessage = "버릴 카드 2개 선택 \n" + cardlist; 

        simpMessagingTemplate.convertAndSendToUser(playername, destination, userMessage);

        List<Card> result = null;

        while(result == null){
            String getresult = "전달받은 값";  
            String []resultArray = getresult.split(","); 
            // Card형태로 변환 필요

            if("전달받은 문자열 수" != String.valueOf(cardsize)){
                simpMessagingTemplate.convertAndSendToUser(playername, destination, userMessage);
            }
            for(int i=0; i<resultArray.length; i++){
                for(int j=0; j<cardlist.size(); j++){
                    if((cardlist.get(j).toString()).equals(resultArray[i])){ 
                        result.add(cardlist.get(j));
                    }
                }
            }
            if(result == null || result.size() != cardsize){
                simpMessagingTemplate.convertAndSendToUser(playername, destination, userMessage);
            }

        }

        for (int i=0; i<cardlist.size(); i++){
            if((cardlist.get(i)).equals(result.get(0))){
                cardlist.remove(cardlist.get(i));
                break;
            }
        }
        
        if(cardsize == 2){
            for(int i=0; i<cardlist.size(); i++){
                if((cardlist.get(i)).equals(result.get(1))){
                    cardlist.remove(cardlist.get(i));
                    break;
                }
            }
        }

        for(int i=0; i<cardlist.size(); i++){
            godeck(cardlist.get(i));
        }

        shuffleDeck();

        player.setCards(result);

    }



    /**
     * 사령관에 의한 코인 강탈
     * @param player 사령관을 사용한 플레이어
     */
    void stealCoin(Player player, Player target){

        if(target.getCoins() >= 2){
            target.setCoins(target.getCoins()-2);
            player.setCoins(player.getCoins()+2);
        }
        else{
            target = getTarget(player);
        }
    }


    /**
     * 타겟이 없는 액션의 경우 null초기화
     * @param player 액션을 실행시킨 플레이어
     * @param action 실행되는 액션
     * @param card 액션에 사용되는 카드
     * @return 카운터 액션 실행하여 카운터에 대한 boolean값 반환
     */
    boolean blockAction(Player player, Action action, Card card){
        return blockAction(player, null, action, card);
    }

    /**
     * 방해 / 의심 / 패스 설정하여 각각 카운터에 대한 액션 수행
     * @param player 액션을 실행시킨 플레이어
     * @param target 액션의 타겟 (타겟이 존재하지 않는 액션일 경우 null)
     * @param action 실행되는 액션
     * @param card 액션에 사용되는 카드
     * @return 카운터 선택과 카드 보유 여부에 따른 카운터 성공 / 실패 반환 -> true(성공, 액션 차단)
     */
    boolean blockAction(Player player, Player target, Action action, Card card){

        ActionType type = action.getActionType();
        Boolean bluff = action.getlegitMove();

        String targetname = target.toString();
        String playername = player.toString();
        String userMessage = null;

        boolean choice = false; // 의심 or 방해 선택 여부 
        

        // 액션이 ForeignAid이 아닐 경우
        if(type != type.ForeignAid){
            List<Player> otherplayers = getotherplayers(player);
            userMessage = "[의심 / 패스] 선택";
            for(int i=0; i<otherplayers.size(); i++){
                String otherplayersname = otherplayers.get(i).getName().toString();
                simpMessagingTemplate.convertAndSendToUser(otherplayersname, destination, userMessage);
            }

            // 값 전달 받은 후 
            Player otherplayer = new Player("aa", Card.Ambassador, Card.Captain); // 임의의 임시 플레이어
            choice = false; // 임의의 의심 여부

            HashMap<Player, Boolean> doubtplayerMap = new HashMap<Player, Boolean>();
            for(int i=0; i<otherplayers.size(); i++){
                doubtplayerMap.put(otherplayer, choice);
            }

            Player doubtplayer = null;
            
            for(Player key : doubtplayerMap.keySet()){
                if(doubtplayerMap.get(key).equals(true)){
                    doubtplayer = key;
                    break;
                }
            }

            // 의심을 누른 사람이 있을 경우
            if(doubtplayer != null){
                // 블러핑이였을 경우
                if(bluff == true){
                    cardDown(player);
                    return true;
                }
                // 블러핑이 아니였을 경우
                else{
                    cardDown(doubtplayer);
                    successcounter(player, card);
                    return true;
                }
            }
            // 의심을 누른 사람이 없고 target이 존재할 경우
            else if(target != null){
                switch(type){

                // 해당 액션이 "암살자"일 경우
                case Assassinate:
                    userMessage = "[귀족으로 방해 / 패스] 선택";
                    simpMessagingTemplate.convertAndSendToUser(targetname, destination, userMessage);

                    choice = false;// 전달받은 값
                    
                    // 타겟이 "귀족으로 방해"를 선택한 경우
                    if(choice == true){
                        return assassinatecounter(player, target, action);
                    }
                    // 타겟이 "패스"를 선택할 경우
                    else{
                        return false;
                    }

                // 해당 액션이 "사령관"일 경우
                case Steal:
                    userMessage = "[사령관으로 방해 / 외교관으로 방해 / 패스] 선택";
                    simpMessagingTemplate.convertAndSendToUser(targetname, destination, userMessage);

                    // 타겟이 선택한 임의의 값(전달받은 값) -> Steal에서 쓰이는 값
                    String Steal = "전달받은 값";

                    switch(Steal){
                        // 타겟이 "사령관으로 방어"를 선택한 경우
                        case "사령관":
                            return stealcounter(player, target, Card.Captain);

                        // 타겟이 "외교관으로 방어"를 선택한 경우
                        case "외교관":
                            return stealcounter(player, target, Card.Ambassador);

                        // 타겟이 "패스"를 선택한 경우
                        case "패스":
                            return false;
                    }
                }       
            }
            // 아무도 의심을 누르지않고 타겟이 없을 경우
            else{
                return false;
            }
        return false;
        }
        // 액션이 ForeignAid일 경우
        else{ 
            List<Player> otherplayers = getotherplayers(player);
            userMessage = "[방해 / 패스] 선택";
            for(int i=0; i<otherplayers.size(); i++){
                String otherplayersname = otherplayers.get(i).getName().toString();
                simpMessagingTemplate.convertAndSendToUser(otherplayersname, destination, userMessage);
            }

            // 값 전달 받은 후 
            Player otherplayer = new Player("aa", Card.Ambassador, Card.Captain); // 임의의 임시 플레이어
            choice = false; // 임의의 방해 여부

            HashMap<Player, Boolean> blockplayerMap = new HashMap<Player, Boolean>();
            for(int i=0; i<otherplayers.size(); i++){
                blockplayerMap.put(otherplayer, choice);
            }

            Player blockplayer = null;
            
            for(Player key : blockplayerMap.keySet()){
                if(blockplayerMap.get(key).equals(true)){
                    blockplayer = key;
                    break;
                }
            }

            // 방해를 선택한 플레이어가 존재할 경우
            if(blockplayer != null){

                userMessage = "[의심 / 패스] 선택";
                simpMessagingTemplate.convertAndSendToUser(playername, destination, userMessage);

                // 값 전달받은 후
                choice = true; // 전달받은 값

                // 플레이어가 의심한 경우
                if(choice == true){
                    // 타겟이 공작 카드를 가지고 있을 경우
                    if(blockplayer.hasCard(Card.Duke)){
                        userMessage = "[공작으로 방어 / 카드 한 장 희생] 선택";
                        
                        // 값 전달받은 후
                        choice = true; // 전달받은 값

                        // 타겟이 "공작으로 방어"를 선택한 경우
                        if(choice == true){
                            cardDown(player);
                            successcounter(blockplayer, Card.Duke);
                            return true;
                        }
                        // 타겟이 "카드 한 장 희생"을 선택한 경우
                        else{
                            cardDown(blockplayer);
                            return false;
                        }
                    }
                    // 타겟에게 공작 카드가 없을 경우
                    else{
                        cardDown(blockplayer);
                        return false;
                    }
                }
                // 플레이어가 의심하지 않은 경우
                else{
                    return true;
                }

            }
            // 아무도 의심하지 않은 경우
            else{
                return false;
            }
        }
    }


    /**
     * 암살자 사용을 방해 받았을때 의심 할 수 있는 기회 제공
     * @param player 의심 / 패스를 선택하는 플레이어
     * @param target 방해를 실행시킨 액션의 대상 
     * @param action 실행되는 액션
     * @return 선택여부와 카드여부에 따라 카운터 결과 값 boolean으로 반환 -> true(성공, 액션 차단)
     */
    boolean assassinatecounter(Player player, Player target, Action action){

        ActionType type = action.getActionType();

        String playername = player.toString();
        String targetname = target.toString();
        String userMessage = null;

        boolean choice = false; // 의심 or 방해 선택 여부 (y,n)

        userMessage = "[의심 / 패스] 선택";
        simpMessagingTemplate.convertAndSendToUser(playername, destination, userMessage);

        // 값 전달 받은 후 
        choice = true; // 전달 받은 값

        // 타겟의 방해에 대한 의심
        if(choice == true){
            // 타겟이 귀족 카드를 가지고 있을 경우
            if(target.hasCard(Card.Contessa)){
                userMessage = "[귀족으로 방어 / 카드 한 장 희생] 선택";
                simpMessagingTemplate.convertAndSendToUser(targetname, destination, userMessage);

                // 값 전달받은 후 
                choice = true; // 전달 받은 값
                successcounter(target, Card.Contessa);
                return true;

            }
            // 타겟에게 귀족 카드가 없을 경우
            else{
                cardDown(target);
                return false;
            }
        }
        // 타겟의 방해에 대한 의심을 하지 않을 경우
        else{
            cardDown(player);
            return true;
        }
    }

    /**
     * 
     * 사령관 사용을 방해 받았을때 의심 할 수 있는 기회 제공
     * @param player 의심 / 패스를 선택하는 플레이어
     * @param target 방해를 실행시킨 액션의 대상 
     * @param action 실행되는 액션
     * @return 선택여부와 카드여부에 따라 카운터 결과 값 boolean으로 반환 -> true(성공, 액션 차단)
     */
    boolean stealcounter(Player player, Player target, Card card){

        String playername = player.toString();
        String targetname = target.toString();
        String userMessage = null;

        boolean choice = false;


        userMessage = "[의심 / 패스] 선택";
        simpMessagingTemplate.convertAndSendToUser(playername, destination, userMessage);

        // 값 전달받은 후 
        choice = true; // 전달 받은 값

        // 타겟의 방해에 대한 의심
        if(choice == true){
            
            switch(card){

                // 타겟이 "사령관으로 방어"를 선택했을 경우
                case Captain:
                // 타겟이 사령관 카드를 가지고 있을 경우
                if(target.hasCard(Card.Captain)){
                    userMessage = "[사령관으로 방어 / 카드 한 장 희생] 선택";
                    simpMessagingTemplate.convertAndSendToUser(targetname, destination, userMessage);

                    // 값 전달 받은 후 
                    choice = true; // 전달 받은 값

                    // 타겟이 "사령관으로 방어"를 선택했을 경우
                    if(choice == true){
                        cardDown(player);
                        successcounter(target, Card.Captain);
                        return true;
                    }
                    // 타겟이 "카드 한 장 희생"을 선택했을 경우
                    else{
                        cardDown(target);
                        return false;
                    }
                }
                // 타겟이 사령관 카드를 가지고 있지 않은 경우
                else{
                    cardDown(target);
                    return false;
                }

                // 타겟이 "외교관으로 방어"를 선택했을 경우
                case Ambassador:
                if(target.hasCard(Card.Ambassador)){
                    userMessage = "[외교관으로 방어 / 카드 한 장 희생] 선택";
                    simpMessagingTemplate.convertAndSendToUser(targetname, destination, userMessage);

                    // 값 전달 받은 후 
                    choice = true; // 전달 받은 값

                    // 타겟이 "외교관으로 방어"를 선택했을 경우
                    if(choice == true){
                        cardDown(player);
                        successcounter(target, Card.Ambassador);
                        return true;
                    }
                    // 타겟이 "카드 한 장 희생"을 선택했을 경우
                    else{
                        cardDown(target);
                        return false;
                    }
                }
                // 타겟이 외교관 카드를 가지고 있지 않은 경우
                else{
                    cardDown(target);
                    return false;
                }
            }
        }
        // 타겟의 방해에 대한 의심을 하지 않을 경우
        else{
            cardDown(player);
            return true;
        }
        return false;
    }

    /**
     * 카운터 액션에 성공했을 경우 해당 카드 덱으로 이동 후 새 카드 한장 드로우
     * @param player 카운터 액션에 성공한 플레이어
     * @param card 덱으로 이동할 카드
     */
    void successcounter(Player player, Card card){
        godeck(player.getCard(card));
        player.addCard(drawOne());
        shuffleDeck();
    }

    Card drawOne() {
        return this.deck.remove(this.deck.size() - 1);
    }

    void shuffleDeck() {
        Collections.shuffle(this.deck, this.random);
    }

    void godeck(Card card){
        deck.add(card);
    }

    void updateAllPlayers(Object obj) {
        for (Player player: players) {
            simpMessagingTemplate.convertAndSendToUser(player.getName(), destination, obj);
        }
    }
}
