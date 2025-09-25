package org.ddamme.controller;

import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ddamme.database.model.User;
import org.ddamme.dto.AuthResponse;
import org.ddamme.dto.LoginRequest;
import org.ddamme.dto.RegisterRequest;
import org.ddamme.metrics.Metrics;
import org.ddamme.security.service.JwtService;
import org.ddamme.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final UserService userService;
  private final JwtService jwtService;
  private final AuthenticationManager authenticationManager;
  private final MeterRegistry meterRegistry;

  @PostMapping("/register")
  @Operation(summary = "Register a new user and receive a JWT")
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
    User user = userService.registerUser(request);
    String token = jwtService.generateToken(user);
    AuthResponse response =
        AuthResponse.builder()
            .token(token)
            .username(user.getUsername())
            .role(user.getRole().name())
            .build();

    return ResponseEntity.ok(response);
  }

  @PostMapping("/login")
  @Operation(summary = "Login with username and password to receive a JWT")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
    Authentication authentication =
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
    User user = (User) authentication.getPrincipal();
    String token = jwtService.generateToken(user);
    
    // Track successful login
    Metrics.increment(meterRegistry, "auth.login.count", "result", "success");
    
    AuthResponse response =
        AuthResponse.builder()
            .token(token)
            .username(user.getUsername())
            .role(user.getRole().name())
            .build();
    return ResponseEntity.ok(response);
  }
}
