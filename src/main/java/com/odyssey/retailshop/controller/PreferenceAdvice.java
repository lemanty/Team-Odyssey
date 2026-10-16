package com.odyssey.retailshop.controller;

import java.security.Principal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@ControllerAdvice
public class PreferenceAdvice {
    @ModelAttribute("zh") public boolean chinese(HttpServletRequest request) { return Boolean.TRUE.equals(request.getAttribute("zh")); }
    @ModelAttribute("signedIn") public boolean signedIn(Principal principal) { return principal != null; }
}
