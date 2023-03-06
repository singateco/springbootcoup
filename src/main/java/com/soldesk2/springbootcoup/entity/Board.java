package com.soldesk2.springbootcoup.entity;

import java.util.Date;

import org.springframework.data.annotation.Id;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Board {

		@Id //pk
		@GeneratedValue(strategy = GenerationType.IDENTITY)
		private Long idx; //자동증가
	   
		private String title;
		private String content;
	   
		@Column(updatable = false)
		private String writer;
	   
		@Column(insertable = false, updatable = false, columnDefinition = "datetime default now()")
		private Date dat; //now()
	   
		@Column(insertable = false, updatable = false, columnDefinition = "int default 0")
		private Long count;
}
