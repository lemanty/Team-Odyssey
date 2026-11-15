package com.odyssey.retailshop.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.CsrfException;

/** Recover stale authentication forms without accepting their credentials or mutations. */
public class AuthCsrfAccessDeniedHandler implements AccessDeniedHandler {
    private final AccessDeniedHandler fallback = new AccessDeniedHandlerImpl();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException exception) throws IOException, ServletException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (exception instanceof CsrfException && "POST".equals(request.getMethod())
                && ("/login".equals(path) || "/register".equals(path))) {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            boolean signedIn = auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
            String destination = signedIn ? "/account" : path + "?expired";
            response.setStatus(HttpServletResponse.SC_SEE_OTHER);
            response.setHeader("Location", AccountWindowConfig.url(destination, AccountWindowConfig.window(request)));
            return;
        }
        fallback.handle(request, response, exception);
    }
}
