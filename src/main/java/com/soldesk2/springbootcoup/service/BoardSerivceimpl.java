package com.soldesk2.springbootcoup.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import com.soldesk2.springbootcoup.entity.Board;
import com.soldesk2.springbootcoup.repository.BoardRepository;

public class BoardSerivceimpl {
	
	@Autowired
	BoardRepository boardRepository;

	public List<Board> getList(){
		
		List<Board> li = boardRepository.findAll();
		return li;
	}
}
