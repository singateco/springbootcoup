package com.soldesk2.springbootcoup.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.Gson;
import com.soldesk2.springbootcoup.entity.Board;
import com.soldesk2.springbootcoup.service.BoardService;

@RestController
@RequestMapping("/board")
public class BoardController {

	@Autowired
	BoardService boardService;
	
	@RequestMapping(value = "/Boards", method = RequestMethod.GET)
	public String Find_Boards_All() { // GET / 게시글 전체 검색
		List<Board> BoardList = boardService.get_List();
		String jsonString = new Gson().toJson(BoardList);
		return jsonString;
	}
	
	@RequestMapping(value = "/Board/{idx}", method = RequestMethod.GET)
	public String Find_Board(@PathVariable("idx") Long idx) { // GET / idx로 게시글 검색
		Board board = boardService.get_Board(idx);
		String jsonString = new Gson().toJson(board);
		return jsonString;
	}
	
	@RequestMapping(value = "/Boards", method = RequestMethod.POST)
	public void Add_Boards(@RequestBody Board board) { // POST / 게시글 추가
		boardService.add_Board(board);
	}
	
	@RequestMapping(value = "/Board", method = RequestMethod.PUT)
	public void Update_Board(@RequestBody Board board) { // PUT / 게시글 수정
		boardService.update_Board(board);
	}
	
	@RequestMapping(value = "/Board/{idx}", method = RequestMethod.DELETE)
	public void Delete_Board(@PathVariable("idx") Long idx) { // DELETE / 게시글 삭제
		boardService.delete_Board(idx);
	}
}
