package com.soldesk2.springbootcoup.game;

public class CounterAction {
    public boolean isBlock;
    public Player player;
    public Card card;

    public CounterAction(boolean isBlock, Player player, Card card) {
        this.isBlock = isBlock;
        this.player = player;
        this.card = card;
    }

    public CounterAction() {}
}
