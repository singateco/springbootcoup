package com.soldesk2.springbootcoup.game;

import lombok.Getter;

@Getter
public class Action {
    
    private ActionType actionType;
    private boolean bluff;

    public Action(ActionType actionType, boolean bluff) {
        this.actionType = actionType;
        this.bluff = bluff;
    }

    public Action(ActionType actionType) {
        this.actionType = actionType;
        this.bluff = false;
    }


    @Override
    public String toString() {
        return "{" + this.actionType + " | bluff?: " + this.bluff + "}";
    }


    public enum ActionType {
        Income,
        ForeignAid,
        Tax,
        Assassinate,
        Coup,
        Exchange,
        Steal,
        Block
    }

    public ActionType getActionType(){
        return actionType;
    }
}
