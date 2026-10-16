package com.odyssey.retailshop.controller;

import com.odyssey.retailshop.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    public AuthController(UserRepository users, PasswordEncoder passwords) { this.users = users; this.passwords = passwords; }
    public record Registration(@NotBlank @Size(max=80) String name,
        @NotBlank @Email @Size(max=254) String email,
        @NotBlank @Size(min=8,max=64) String password, @NotBlank String confirmPassword) {}
    @GetMapping("/login")
    public String login(Principal principal, Model model) {
        if (principal != null) return "redirect:/account";
        model.addAttribute("databaseAvailable", users.available());
        return "login";
    }
    @GetMapping("/register")
    public String register(Principal principal, Model model) {
        if (principal != null) return "redirect:/account";
        model.addAttribute("databaseAvailable", users.available());
        return "register";
    }
    @PostMapping("/register")
    public String register(@Valid @ModelAttribute Registration registration, BindingResult result, Model model, Principal principal) {
        if (principal != null) return "redirect:/account";
        model.addAttribute("databaseAvailable", users.available());
        model.addAttribute("nameValue", registration.name());
        model.addAttribute("emailValue", registration.email());
        if (!users.available()) { model.addAttribute("registrationError", "database"); return "register"; }
        if (result.hasErrors() || !registration.password().equals(registration.confirmPassword()) || registration.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            model.addAttribute("registrationError", "validation"); return "register";
        }
        try {
            users.create(registration.name().strip(), registration.email().strip().toLowerCase(Locale.ROOT), passwords.encode(registration.password()));
        } catch (DuplicateKeyException e) {
            model.addAttribute("registrationError", "duplicate"); return "register";
        }
        return "redirect:/login?registered";
    }
    @GetMapping("/account")
    public String account(Principal principal, Model model) {
        var account = users.findByEmail(principal.getName()).orElseThrow();
        model.addAttribute("displayName", account.displayName());
        model.addAttribute("accountEmail", account.email());
        model.addAttribute("accountRole", account.role());
        return "account";
    }
}
