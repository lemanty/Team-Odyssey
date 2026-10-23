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
            return User.withUsername(account.email()).password(account.passwordHash()).roles(account.role()).disabled(account.role().equals("DELETED")).build();
        };
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, UserRepository users) throws Exception {
        http.addFilterBefore(new org.springframework.web.filter.OncePerRequestFilter() {
            @Override protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, jakarta.servlet.FilterChain chain) throws jakarta.servlet.ServletException, java.io.IOException {
                var context=org.springframework.security.core.context.SecurityContextHolder.getContext();
                var auth=context.getAuthentication();
                if(users.available() && auth!=null && auth.isAuthenticated() && !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
                    var stored=users.findByEmail(auth.getName());
                    if(stored.isPresent() && stored.get().role().equals("DELETED")) {
                        org.springframework.security.core.context.SecurityContextHolder.clearContext();
                        var session=request.getSession(false);if(session!=null)session.invalidate();
                        response.sendRedirect(AccountWindowConfig.url("/login?deleted",AccountWindowConfig.window(request)));
                        return;
                    }
                    stored.ifPresent(account->{
                        var roles=java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_"+account.role()));
                        if(!new java.util.HashSet<>(auth.getAuthorities()).equals(new java.util.HashSet<>(roles))) {
                            var refreshed=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(auth.getPrincipal(),null,roles);
                            refreshed.setDetails(auth.getDetails());context.setAuthentication(refreshed);
                            var session=request.getSession(false);
                            if(session!=null)session.setAttribute("SPRING_SECURITY_CONTEXT",context);
                        }
                    });
                }
                chain.doFilter(request,response);
            }
        }, org.springframework.security.web.access.intercept.AuthorizationFilter.class);
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/products/*/reviews", "/products/*/reviews/**").authenticated()
                .requestMatchers("/staff/support", "/staff/support/**").hasRole("STAFF")
                .requestMatchers("/support", "/support/**").hasAnyRole("CUSTOMER", "STAFF")
                .requestMatchers("/cart/add", "/cart/update", "/cart/remove", "/orders", "/orders/**", "/checkout", "/checkout/**", "/account", "/account/**").authenticated()
                .requestMatchers("/admin", "/admin/**").hasRole("ADMIN")
                .requestMatchers("/staff/products", "/staff/products/**").hasRole("ADMIN")
                .requestMatchers("/staff", "/staff/**").hasAnyRole("STAFF", "ADMIN")
                .anyRequest().permitAll())
            .exceptionHandling(errors -> errors.defaultAuthenticationEntryPointFor(
                new org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint("/login?cartRequired"),
                request -> request.getServletPath().startsWith("/cart/")))
            .formLogin(form -> form.loginPage("/login").usernameParameter("email")
                .defaultSuccessUrl("/account", true).failureUrl("/login?error").permitAll())
            .logout(logout -> logout.logoutSuccessUrl("/login?logout"));
        return http.build();
    }
}
