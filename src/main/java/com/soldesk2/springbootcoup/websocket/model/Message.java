package com.soldesk2.springbootcoup.websocket.model;

import lombok.Data;

@Data
public class Message {
    private String senderName;
    private String receiverName;
    private String messageBody;
    private Status status;
}
