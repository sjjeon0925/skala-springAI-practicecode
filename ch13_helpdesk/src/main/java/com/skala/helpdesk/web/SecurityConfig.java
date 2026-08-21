package com.skala.helpdesk.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 13장 Phase 7 — 인증·인가.
 *
 * <p>HTTP Basic으로 단순하게 시작한다. {@code Lab3 실습}(12-1) 노트가 그대로 쓴
 * {@code curl -u user1:pass}·{@code curl -u admin:admin} 패턴과 맞춘 계정 구성이다.
 *
 * <ul>
 *   <li>{@code user-1}/{@code user-2} — 상담 API(/chat)를 쓰는 일반 사용자.
 *       {@code OrderRepository}의 주문 소유자 ID와 이름을 맞춰서, 로그인한 사람이
 *       곧 {@code ToolContext}에 들어가는 userId가 되게 했다.</li>
 *   <li>{@code admin} — 담당자 화면(/admin/**, /ingest-samples, /inspect)만 쓸 수 있다.</li>
 * </ul>
 *
 * <p>Swagger UI에서 오른쪽 위 "Authorize" 버튼으로 로그인하면 이어지는 호출에 자동으로 붙는다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        var user1 = User.withUsername("user-1").password(encoder.encode("pass")).roles("USER").build();
        var user2 = User.withUsername("user-2").password(encoder.encode("pass")).roles("USER").build();
        var admin = User.withUsername("admin").password(encoder.encode("admin")).roles("ADMIN").build();
        return new InMemoryUserDetailsManager(user1, user2, admin);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())              // REST API — 세션 쿠키를 안 쓴다
                .authorizeHttpRequests(auth -> auth
                        // 문서·헬스체크는 로그인 없이
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**",
                                "/actuator/health", "/actuator/prometheus").permitAll()
                        // 담당자 전용 — 인제스트 · 승인 · 인제스트 품질 확인(Phase 2 심화)
                        .requestMatchers("/admin/**", "/ingest-samples", "/inspect")
                                .hasRole("ADMIN")
                        // 나머지 상담 API는 로그인만 하면 된다(권한은 도구 안에서 별도로 검증)
                        .requestMatchers("/chat/**").authenticated()
                        .anyRequest().permitAll())
                .httpBasic(basic -> {})
                .build();
    }
}
