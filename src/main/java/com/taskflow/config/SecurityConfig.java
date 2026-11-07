package com.taskflow.config;

import com.taskflow.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.HttpSessionEventPublisher;

import java.util.Locale;

@Configuration
public class SecurityConfig {
    private static final String CONTENT_SECURITY_POLICY = String.join("; ",
        "default-src 'self'", "script-src 'self'", "style-src 'self' 'unsafe-inline'",
        "font-src 'self'", "img-src 'self' data:", "connect-src 'self'", "worker-src 'self'",
        "manifest-src 'self'", "object-src 'none'", "base-uri 'self'", "frame-ancestors 'none'",
        "form-action 'self'");

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(UserRepository userRepository) {
        return email -> userRepository.findByEmail(email.strip().toLowerCase(Locale.ROOT))
            .map(user -> User.withUsername(user.getEmail())
                .password(user.getPassword())
                .roles("USER")
                .disabled(user.requiresEmailConfirmation())
                .build())
            .orElseThrow(() -> new UsernameNotFoundException("Неверный email или пароль"));
    }

    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    HttpSessionEventPublisher sessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SessionRegistry sessionRegistry) throws Exception {
        return http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/register", "/confirm-email", "/confirm-email/resend",
                    "/forgot-password", "/reset-password", "/offline.html", "/css/**", "/js/**",
                    "/fonts/**", "/icons/**", "/manifest.webmanifest", "/sw.js", "/error").permitAll()
                .anyRequest().authenticated())
            .formLogin(form -> form.loginPage("/login")
                .usernameParameter("email")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error")
                .permitAll())
            .sessionManagement(session -> session.maximumSessions(-1)
                .sessionRegistry(sessionRegistry)
                .expiredUrl("/login?sessionExpired"))
            .logout(logout -> logout.logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID"))
            .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY)))
            .build();
    }
}
