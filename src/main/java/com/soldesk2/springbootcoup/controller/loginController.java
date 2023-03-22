package com.soldesk2.springbootcoup.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;

import com.soldesk2.springbootcoup.service.LoginService;

@Controller
public class loginController {

    @Autowired
    LoginService loginService;

    @PostMapping("/login")
    public String login(String id, String password){
        Boolean exist_user = loginService.get_user(id, password);
        if(exist_user == true){
            return "index";
        } else{
            return "login_fail";
        }
    }
}
