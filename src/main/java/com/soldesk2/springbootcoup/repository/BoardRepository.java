package com.soldesk2.springbootcoup.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.soldesk2.springbootcoup.entity.Board;

@Repository
public interface BoardRepository extends JpaRepository<Board,Long> {

}
