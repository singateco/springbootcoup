package com.soldesk2.springbootcoup.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.soldesk2.springbootcoup.entity.User;

public interface LoginRepository extends JpaRepository<User,String>{
    
}
