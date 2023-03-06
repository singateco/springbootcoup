package com.soldesk2.springbootcoup.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.soldesk2.springbootcoup.entity.Board;
import com.soldesk2.springbootcoup.repository.BoardRepository;

@Service
public class BoardSerivceimpl implements BoardService{
	
	@Autowired
	BoardRepository boardRepository;
	

	@Override
	public List<Board> get_List(){
		List<Board> li = boardRepository.findAll();
		return li;
	}
	
	@Override
	public Board get_Board(Long idx) {
		Optional<Board> vo = boardRepository.findById(idx);
		if (vo.isPresent()) {
			return vo.get();
		} else {
			return null;
		}
	}
	
	@Override
	public Board add_Board(Board board) {
		Board add_board = boardRepository.save(board);
		return add_board;
	}
	
	@Override
	public Board update_Board(Board board) {
		Board update_board = boardRepository.save(board);
		Optional<Board> vo = Optional.ofNullable(update_board);
		if(vo.isPresent()) {
			return vo.get();
		}
		else {
			return null;
		}
	}
	
	@Override
	public void delete_Board(Long idx) {
		boardRepository.deleteById(idx);
	}
}
