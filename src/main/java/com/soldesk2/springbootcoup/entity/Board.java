package com.soldesk2.springbootcoup.entity;

import jakarta.persistence.Entity;
import lombok.Data;

@Entity
@Data
public class Board {

	private Long Board_idx; 		// 게시판 인덱스
	private String Board_title; 	// 게시 글 제목
	private String Board_content; 	// 게시 글 내용
	private String Board_writer;	// 게시 글 작성자
	private String Board_date; 		// 게시 글 작성잉자
}
