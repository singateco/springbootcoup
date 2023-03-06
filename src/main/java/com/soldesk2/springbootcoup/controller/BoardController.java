package com.soldesk2.springbootcoup.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.Gson;
import com.soldesk2.springbootcoup.entity.Board;
import com.soldesk2.springbootcoup.service.BoardService;

import io.micrometer.core.ipc.http.HttpSender.Response;

@RestController
public class BoardController {

	@Autowired
	BoardService boardService;
	
	@RequestMapping(value = "/board", method = RequestMethod.GET)
	public ResponseEntity<String> Find_Boards_All() { // GET / 게시글 전체 검색
		List<Board> BoardList = boardService.get_List();
		String jsonString = new Gson().toJson(BoardList); 
		return ResponseEntity.status(HttpStatus.OK).body(jsonString);
	}
	
	@RequestMapping(value = "/board/{idx}", method = RequestMethod.GET)
	public ResponseEntity<String> Find_Board(@PathVariable("idx") Long idx) { // GET / idx로 게시글 검색
		Board board = boardService.get_Board(idx);
		if (board == null) {
			return ResponseEntity.status(HttpStatus.NO_CONTENT).body("none");
		}

		String jsonString = new Gson().toJson(board);
		return ResponseEntity.status(HttpStatus.OK).body(jsonString);
	}
	
	@RequestMapping(value = "/board", method = RequestMethod.POST)
	public ResponseEntity<Board> Add_Boards(@RequestBody Board board) { // POST / 게시글 추가
		Board add_Board = boardService.add_Board(board);
		return ResponseEntity.status(HttpStatus.CREATED).body(add_Board);
	}
	
	@RequestMapping(value = "/board", method = RequestMethod.PUT)
	public ResponseEntity<Board> Update_Board(@RequestBody Board board) { // PUT / 게시글 수정
		Board update_Borad = boardService.update_Board(board);
		return ResponseEntity.status(HttpStatus.OK).body(update_Borad);
	}
	
	@RequestMapping(value = "/board/{idx}", method = RequestMethod.DELETE)
	public ResponseEntity<Board> Delete_Board(@PathVariable("idx") Long idx) { // DELETE / 게시글 삭제
		boardService.delete_Board(idx);
		return ResponseEntity.status(HttpStatus.OK).body(null);
	}
}
