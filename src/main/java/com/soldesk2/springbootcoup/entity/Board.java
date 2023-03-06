package com.soldesk2.springbootcoup.entity;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

@Entity
@Data
public class Board {

		@Id //pk
		@GeneratedValue(strategy = GenerationType.IDENTITY)
		private Long index; //자동증가
	   
		private String title;
		private String content;
	   
		@Column(updatable = false)
		private String writer;
	   
		@Column(insertable = false, updatable = false, columnDefinition = "datetime default now()")
		private Date date; //now()
	   
		@Column(insertable = false, columnDefinition = "int default 0")
		private Long readCount;
}
