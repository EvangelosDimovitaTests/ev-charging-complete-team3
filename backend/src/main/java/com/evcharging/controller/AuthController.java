package com.evcharging.controller;

import com.evcharging.model.User;
import com.evcharging.repository.UserRepository;
import com.evcharging.security.JwtUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    /** Logs in a user and returns a JWT token. */
    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.username().trim(),
                            loginRequest.password()
                    )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            String jwt = jwtUtils.generateJwtToken(authentication);
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();

            String role = userDetails.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .findFirst()
                    .orElse("ROLE_DRIVER")
                    .replace("ROLE_", "");

            User user = userRepository.findByUsername(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

            return ResponseEntity.ok(Map.of(
                    "token", jwt,
                    "username", userDetails.getUsername(),
                    "role", role,
                    "userId", user.getId(),
                    "email", user.getEmail()
            ));

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", "Invalid username or password"
            ));
        } catch (DisabledException e) {
            return ResponseEntity.status(403).body(Map.of(
                    "error", "User account is disabled"
            ));
        } catch (LockedException e) {
            return ResponseEntity.status(403).body(Map.of(
                    "error", "User account is locked"
            ));
        }
    }

    /** Registers a basic driver account. */
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        String username = registerRequest.username().trim();
        String email = registerRequest.email().trim();

        if (userRepository.existsByUsername(username)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Username is already taken"
            ));
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(registerRequest.password()));
        user.setEmail(email);
        user.setRole(User.Role.DRIVER);

        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "message", "User registered successfully",
                "username", user.getUsername(),
                "role", user.getRole().name()
        ));
    }

    public record LoginRequest(
            @NotBlank(message = "Username is required")
            String username,

            @NotBlank(message = "Password is required")
            String password
    ) {}

    public record RegisterRequest(
            @NotBlank(message = "Username is required")
            @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
            String username,

            @NotBlank(message = "Password is required")
            @Size(min = 6, max = 100, message = "Password must be at least 6 characters")
            String password,

            @NotBlank(message = "Email is required")
            @Email(message = "Email must be valid")
            String email
    ) {}
}