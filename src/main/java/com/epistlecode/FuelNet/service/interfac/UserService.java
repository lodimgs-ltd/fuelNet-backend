package com.epistlecode.FuelNet.service.interfac;

import com.epistlecode.FuelNet.model.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

public interface UserService {

    public User findUserByJwtToken(String jwt);

    public User findUserByEmail(String email) throws Exception;

    public User forgotPassword(String email,String password) throws Exception;

    public List<User> getUsers();

    public User updateUserStatus(String email);

    public UserDetails loadByEmail(String email);
}
