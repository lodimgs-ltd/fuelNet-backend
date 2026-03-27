package com.epistlecode.FuelNet.service.impl;

import com.epistlecode.FuelNet.config.JwtProvider;
import com.epistlecode.FuelNet.model.User;
import com.epistlecode.FuelNet.model.UserRole;
import com.epistlecode.FuelNet.model.UserStatus;
import com.epistlecode.FuelNet.respository.UserRepository;
import com.epistlecode.FuelNet.service.interfac.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class UserServicesImpl implements UserService {

    @Autowired
    JwtProvider jwtProvider;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PasswordEncoder passwordEncoder;


    @Override
    public User findUserByJwtToken(String jwt) {
        String email = jwtProvider.getEmailFromJwtToken(jwt);
        return userRepository.findByEmail(email);
    }

    @Override
    public User findUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    public User forgotPassword(String email, String password) {
        User user = findUserByEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        return userRepository.save(user);
    }

    @Override
    public List<User> getUsers() {
        return userRepository.findAll();
    }


    @Override
    public User updateUserStatus(String email) {
        User user = findUserByEmail(email);

        if (user.getStatus() == UserStatus.ENABLED){
            user.setStatus(UserStatus.DISABLED);
        }else {
            user.setStatus(UserStatus.ENABLED);
        }
        return userRepository.save(user);
    }

    @Override
    public UserDetails loadByEmail(String email) {
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new UsernameNotFoundException("User not found with email "+ email);
        }
        UserRole role = user.getRole();
        if (role == null) role = UserRole.ROLE_USER;
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(role.toString()));
        return new org.springframework.security.core.userdetails.User(user.getEmail(), user.getPassword(), authorities);
    }
}
