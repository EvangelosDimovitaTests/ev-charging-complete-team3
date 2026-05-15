package com.evcharging.controller;

import com.evcharging.model.Booking;
import com.evcharging.model.User;
import com.evcharging.repository.BookingRepository;
import com.evcharging.repository.UserRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /** Lists users without password hashes. Admin only. */
    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        List<UserResponse> users = userRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(User::getId))
                .map(this::toUserResponse)
                .toList();

        return ResponseEntity.ok(users);
    }

    /** Gets one user without the password hash. Admin only. */
    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        return userRepository.findById(id)
                .<ResponseEntity<?>>map(user -> ResponseEntity.ok(toUserResponse(user)))
                .orElse(ResponseEntity.status(404).body(Map.of("error", "User not found")));
    }

    /** Creates a user. Admin only. */
    @PostMapping
    public ResponseEntity<?> createUser(@Valid @RequestBody CreateUserRequest request) {
        String username = request.username().trim();
        String email = request.email().trim();

        if (userRepository.existsByUsername(username)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username is already taken"));
        }

        User.Role role = parseRole(request.role());
        if (role == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Role must be DRIVER or ADMIN"));
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(role);

        User saved = userRepository.save(user);
        return ResponseEntity.ok(toUserResponse(saved));
    }

    /** Updates a user. A blank password keeps the old password. Admin only. */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id,
                                        @Valid @RequestBody UpdateUserRequest request,
                                        Authentication authentication) {

        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        String username = request.username().trim();
        String email = request.email().trim();

        User existingByUsername = userRepository.findByUsername(username).orElse(null);
        if (existingByUsername != null && !existingByUsername.getId().equals(id)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username is already taken"));
        }

        User.Role role = parseRole(request.role());
        if (role == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Role must be DRIVER or ADMIN"));
        }

        boolean editingSelf = user.getUsername().equals(authentication.getName());
        if (editingSelf && role != User.Role.ADMIN) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "You cannot remove your own ADMIN role"
            ));
        }

        user.setUsername(username);
        user.setEmail(email);
        user.setRole(role);

        if (request.password() != null && !request.password().isBlank()) {
            if (request.password().length() < 6) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Password must be at least 6 characters"
                ));
            }
            user.setPassword(passwordEncoder.encode(request.password()));
        }

        User saved = userRepository.save(user);
        return ResponseEntity.ok(toUserResponse(saved));
    }

    /** Deletes a user when it is safe to remove their booking references. Admin only. */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deleteUser(@PathVariable Long id, Authentication authentication) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        if (user.getUsername().equals(authentication.getName())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "You cannot delete your own admin account"
            ));
        }

        List<Booking> userBookings = bookingRepository.findByUserId(id);

        boolean hasConfirmedBookings = userBookings.stream()
                .anyMatch(booking -> booking.getStatus() == Booking.BookingStatus.CONFIRMED);

        if (hasConfirmedBookings) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", "Cannot delete user because they have confirmed bookings"
            ));
        }

        if (!userBookings.isEmpty()) {
            bookingRepository.deleteAll(userBookings);
        }

        userRepository.delete(user);
        return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
    }

    private User.Role parseRole(String roleValue) {
        if (roleValue == null) {
            return null;
        }

        try {
            return User.Role.valueOf(roleValue.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name()
        );
    }

    public record UserResponse(
            Long id,
            String username,
            String email,
            String role
    ) {}

    public record CreateUserRequest(
            @NotBlank(message = "Username is required")
            @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
            String username,

            @NotBlank(message = "Password is required")
            @Size(min = 6, max = 100, message = "Password must be at least 6 characters")
            String password,

            @NotBlank(message = "Email is required")
            @Email(message = "Email must be valid")
            String email,

            @NotNull(message = "Role is required")
            String role
    ) {}

    public record UpdateUserRequest(
            @NotBlank(message = "Username is required")
            @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
            String username,

            String password,

            @NotBlank(message = "Email is required")
            @Email(message = "Email must be valid")
            String email,

            @NotNull(message = "Role is required")
            String role
    ) {}
}
