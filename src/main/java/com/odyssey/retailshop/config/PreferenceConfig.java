package com.odyssey.retailshop.config;

import java.time.Duration;
import jakarta.servlet.http.*;
import org.springframework.http.ResponseCookie;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

/** Language is a preference only: never trust a cookie for identity or roles. */
@Configuration
public class PreferenceConfig implements WebMvcConfigurer {
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                String language = "en";
                if (request.getCookies() != null) for (Cookie cookie : request.getCookies()) {
                    if (cookie.getName().equals("odyssey_language") && cookie.getValue().equals("zh")) language = "zh";
                }
                String chosen = request.getParameter("lang");
                if (request.getMethod().equals("GET") && ("en".equals(chosen) || "zh".equals(chosen))) {
                    language = chosen;
                    response.addHeader("Set-Cookie", ResponseCookie.from("odyssey_language", language)
                        .path("/").maxAge(Duration.ofDays(180)).httpOnly(true).secure(request.isSecure()).sameSite("Lax").build().toString());
                }
                request.setAttribute("zh", language.equals("zh"));
                return true;
            }
        });
    }
}
