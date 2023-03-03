package com.soldesk2.springbootcoup.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.soldesk2.springbootcoup.entity.Board;
import com.soldesk2.springbootcoup.service.BoardService;

@Controller
@RequestMapping("/board")
public class BoardController {

	@Autowired
	BoardService boardService;
	
	@GetMapping("/main")
	public String main(Model model) {
		List<Board> BoardList = boardService.getList();
		model.addAttribute("BoardList", BoardList);
		return "main";
	}
}
