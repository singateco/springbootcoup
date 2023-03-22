package com.soldesk2.springbootcoup.service;

import com.soldesk2.springbootcoup.entity.User;

public interface LoginService { 
    public User add_user(User user);                       // 유저 추가
    public Boolean get_user(String id, String password);   // 해당 id값이 있는지 검색
}
