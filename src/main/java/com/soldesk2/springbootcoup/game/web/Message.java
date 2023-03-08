package com.soldesk2.springbootcoup.game.web;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Message {
    private MessageType type;
    
    /**
     * 유저에게 제공할 메시지.
     */
    private String userMessage;

    /**
     * JSON 데이터.
     */
    private Object content;

    public Message(MessageType type, Object content, String userMessage) {
        this.type = type;
        this.content = content;
        this.userMessage = userMessage;
    }

    public Message(MessageType type, Object content) {
        this(type, content, null);
    }

}
