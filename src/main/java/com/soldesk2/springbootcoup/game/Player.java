package com.soldesk2.springbootcoup.game;

import java.util.ArrayList;
import java.util.List;

public class Player {
    private int coins;
    private final String name;
    private ArrayList<Card> cards;

    public Player(String name, Card card1, Card card2) {
        this.name = name;
        this.coins = 2;
        cards = new ArrayList<>(4);
        cards.add(card1);
        cards.add(card2);
    }
    
    public void addCard(Card card) {
        cards.add(card);
    }

    public void removeCard(Card card) throws IllegalArgumentException {
        if (cards.contains(card)) {
            cards.remove(card);
        } else {
            throw new IllegalArgumentException("플레이어 " + this.name + "에게 카드 " + card + "가 없습니다.");
        }
    }

    public int getCardNumbers() {
        return this.cards.size();
    }

    public int getCoins() {
        return this.coins;
    }

    public void setCoins(int coins) {
        this.coins = coins;
    }

    public String getName() {
        return this.name;
    }


    public List<Card> getCards() {
        return this.cards;
    }

    public void setCards(List<Card> cards) {
        this.cards = (ArrayList<Card>) cards;
    }

    public boolean hasCard(Card card) {
        return this.cards.contains(card);
    }
    
    public Card[] getCardList(){
        Card[] cardList = null;
        int index = 0;

        for(int i=0; i<cards.size(); i++){
            if(cards.get(i) != null){
                cardList[index] = cards.get(i);
                index++;
            }
        }
        
        return cardList;
    }

    @Override
    public String toString() {
        return "플레이어 {이름: " + this.name + " 코인: " + this.coins + " 카드 : " + this.cards + " }"; 
    }

}