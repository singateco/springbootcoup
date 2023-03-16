package com.soldesk2.springbootcoup.game.web;

import lombok.Data;

@Data
public class ChatPayload {
    private String sender;
    private String message;
}
