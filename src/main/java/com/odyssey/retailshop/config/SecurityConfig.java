package com.odyssey.retailshop.config;

import com.odyssey.retailshop.repository.UserRepository;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean UserDetailsService userDetailsService(UserRepository users) {
        return email -> {
            var account = users.findByLogin(email.strip().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
            return User.withUsername(account.email()).password(account.passwordHash()).roles(account.role()).build();
        };
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/support", "/support/**", "/cart/add", "/cart/update", "/cart/remove", "/orders", "/orders/**", "/checkout", "/account", "/account/**").authenticated()
                .requestMatchers("/admin", "/admin/**").hasRole("ADMIN")
                .requestMatchers("/staff", "/staff/**").hasAnyRole("STAFF", "ADMIN")
                .anyRequest().permitAll())
            .exceptionHandling(errors -> errors.defaultAuthenticationEntryPointFor(
                new org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint("/login?cartRequired"),
                request -> request.getServletPath().startsWith("/cart/")))
            .formLogin(form -> form.loginPage("/login").usernameParameter("email")
                .defaultSuccessUrl("/account", true).failureUrl("/login?error").permitAll())
            .logout(logout -> logout.logoutSuccessUrl("/login?logout").deleteCookies("JSESSIONID"));
        return http.build();
    }
}
