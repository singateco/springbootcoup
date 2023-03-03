package com.soldesk2.springbootcoup.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.soldesk2.springbootcoup.entity.Board;

public interface BoardRepository extends JpaRepository<Board,Long> {

}
