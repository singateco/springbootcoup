package com.soldesk2.springbootcoup.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "COUP_USER")
public class User {
    @Id
    private String id;
    private String password;
}
