package com.soldesk2.springbootcoup.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;

import com.soldesk2.springbootcoup.entity.User;
import com.soldesk2.springbootcoup.repository.LoginRepository;

public class LoginServiceimpl implements LoginService{

    @Autowired
    LoginRepository loginRepository;

    @Override
    public User add_user(User user){
        User new_user = loginRepository.save(user);
        return new_user;
    }

    @Override
    public Boolean get_user(String id, String password) {
        Optional<User> user = loginRepository.findById(id);
        if(user == null || !user.get().getPassword().equals(password)){
            return false;
        }
        return true;
    }

}
