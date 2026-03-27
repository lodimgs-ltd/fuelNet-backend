package com.epistlecode.FuelNet.controller;

import com.epistlecode.FuelNet.config.JwtProvider;
import com.epistlecode.FuelNet.model.User;
import com.epistlecode.FuelNet.model.UserRole;
import com.epistlecode.FuelNet.model.UserStatus;
import com.epistlecode.FuelNet.request.CreateUserRequest;
import com.epistlecode.FuelNet.request.LoginRequest;
import com.epistlecode.FuelNet.response.AuthResponse;
import com.epistlecode.FuelNet.respository.UserRepository;
import com.epistlecode.FuelNet.service.interfac.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {
    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    UserService userService;


    @PostMapping()
    public ResponseEntity<?> createUser(@Valid @RequestBody CreateUserRequest req, BindingResult result){
        if (result.hasErrors()) {
            // Get first error message (or loop through all)
            String errorMessage = result.getAllErrors().get(0).getDefaultMessage();
            assert errorMessage != null;
            return new ResponseEntity<>(Map.of("error", errorMessage), HttpStatus.BAD_REQUEST);
        }

        try{
            User newUser = new User();
            newUser.setFullName(req.getFullName());
            newUser.setEmail(req.getEmail());
            newUser.setPassword(passwordEncoder.encode(req.getPassword()));
            newUser.setCreatedAt(new Timestamp(System.currentTimeMillis()));
            newUser.setStatus(UserStatus.ENABLED);
            newUser.setRole(UserRole.ROLE_USER);

            userRepository.save(newUser);

            Authentication authentication = new UsernamePasswordAuthenticationToken(newUser.getEmail(), req.getPassword());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            String jwt = jwtProvider.generateToken(authentication);

            AuthResponse response = new AuthResponse();
            response.setJwt(jwt);
            response.setMessage("Registration Successful");
            response.setRole(UserRole.ROLE_USER);
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(Map.of("error", "Something went wrong while creating User"),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req,
                                   BindingResult result) throws Exception {
        if (result.hasErrors()) {
            // Get first error message (or loop through all)
            String errorMessage = result.getAllErrors().get(0).getDefaultMessage();
            assert errorMessage != null;
            return new ResponseEntity<>(Map.of("error", errorMessage), HttpStatus.BAD_REQUEST);
        }
        String username = req.getEmail();
        String password = req.getPassword();
        User user = userService.findUserByEmail(username);
        if (user == null) {
            return new ResponseEntity<>(Map.of("error", "Invalid credentials"), HttpStatus.UNAUTHORIZED);
        }
        if (user.getStatus() != UserStatus.ENABLED) {
            return new ResponseEntity<>(Map.of("error", "Account is disabled. Contact support."),
                    HttpStatus.FORBIDDEN);
        }
        Authentication authentication = authenticate(username, password);
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        String role = authorities.isEmpty() ? null : authorities.iterator().next().getAuthority();
        String jwt = jwtProvider.generateToken(authentication);
        user.setLastLogin(new Timestamp(System.currentTimeMillis()));
        userRepository.save(user);

        AuthResponse authResponse = new AuthResponse();
        authResponse.setJwt(jwt);
        authResponse.setMessage("Login Successful");
        authResponse.setRole(UserRole.valueOf(role));

        return new ResponseEntity<>(authResponse, HttpStatus.OK);

    }

    @GetMapping()
    public ResponseEntity<User> getUser(@RequestHeader ("Authorization") String jwt){
        String email = jwtProvider.getEmailFromJwtToken(jwt);
        User user = userRepository.findByEmail(email);
        return new ResponseEntity<>(user, HttpStatus.OK);
    }



    @ControllerAdvice
    public class GlobalExceptionHandler {

        @ExceptionHandler(BadCredentialsException.class)
        public ResponseEntity<?> handleBadCredentials(BadCredentialsException ex) {
            return new ResponseEntity<>(Map.of("error", ex.getMessage()), HttpStatus.UNAUTHORIZED);
        }

        @ExceptionHandler(DataIntegrityViolationException.class)
        public ResponseEntity<?> handleConstraintViolation(DataIntegrityViolationException ex) {
            return new ResponseEntity<>(Map.of("error", "Email already in use"), HttpStatus.BAD_REQUEST);
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<?> handleOtherExceptions(Exception ex) {
            return new ResponseEntity<>(Map.of("error", "An unexpected error occurred"), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    private Authentication authenticate(String username, String password) {
        UserDetails userDetails = userService.loadByEmail(username);

        if (userDetails == null) {
            throw new BadCredentialsException("Invalid email....");
        }
        if (!passwordEncoder.matches(password, userDetails.getPassword())) {
            throw new BadCredentialsException("Invalid Credentials....");
        }
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }


}
