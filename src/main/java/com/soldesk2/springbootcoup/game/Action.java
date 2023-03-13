package com.soldesk2.springbootcoup.game;

import lombok.Getter;

@Getter
public class Action {
    
    private ActionType actionType;
    private boolean legitMove;

    public Action(ActionType actionType, boolean legitMove) {
        this.actionType = actionType;
        this.legitMove = legitMove;
    }

    public Action(ActionType actionType) {
        this.actionType = actionType;
        this.legitMove = true;
    }


    @Override
    public String toString() {
        return "{" + this.actionType + " | legitMove?: " + this.legitMove + "}";
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
