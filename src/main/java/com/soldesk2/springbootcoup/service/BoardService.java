package com.soldesk2.springbootcoup.service;

import com.soldesk2.springbootcoup.entity.Board;
import java.util.List;

public interface BoardService {
	public List<Board> get_List(); 						// 전체 검색
	public Board get_Board(Long idx); 					// 하나 검색
	public Board add_Board(Board board); 				// 글 추가
	public Board update_Board(Board board);				// 글 수정
	public void delete_Board(Long idx); 				// 글 삭제
}
