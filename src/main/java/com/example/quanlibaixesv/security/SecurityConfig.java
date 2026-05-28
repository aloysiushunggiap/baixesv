package com.example.quanlibaixesv.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          UserDetailsService userDetailsService,
                          PasswordEncoder passwordEncoder) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/css/**",
                                "/js/**",
                                "/views/**",
                                "/images/**",
                                "/favicon.ico"
                        ).permitAll()

                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Auth public endpoints.
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()

                        // Đăng ký tài khoản user/admin phải do ADMIN đang đăng nhập thực hiện.
                        .requestMatchers(HttpMethod.POST, "/api/auth/register").hasRole("ADMIN")

                        // User quên mật khẩu không cần đăng nhập.
                        .requestMatchers(HttpMethod.POST, "/api/password-reset/request").permitAll()

                        // Admin xem và xử lý mục Yêu cầu.
                        .requestMatchers(HttpMethod.GET, "/api/password-reset/admin/requests").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/password-reset/admin/requests/**").hasRole("ADMIN")

                        // Admin quản lý danh sách session/token còn hạn.
                        .requestMatchers(HttpMethod.GET, "/api/sessions/active").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/sessions/**").hasRole("ADMIN")

                        // Đổi mật khẩu: USER đổi tài khoản của mình, ADMIN reset được mọi tài khoản.
                        .requestMatchers(HttpMethod.PUT, "/api/account/change-password").hasAnyRole("USER", "ADMIN")

                        // Chỉ ADMIN được xóa tài khoản admin.
                        .requestMatchers(HttpMethod.DELETE, "/api/account/admin/**").hasRole("ADMIN")

                        // Quẹt thẻ trên giao diện phải có đăng nhập để backend phân biệt USER và ADMIN test.
                        .requestMatchers("/api/parking/swipe").hasAnyRole("USER", "ADMIN")

                        // USER và ADMIN được xem lịch sử.
                        .requestMatchers("/api/parking/history").hasAnyRole("USER", "ADMIN")

                        // Chỉ ADMIN được mô phỏng phí, đăng ký thẻ, xóa thẻ.
                        .requestMatchers("/api/parking/simulate-fee").hasRole("ADMIN")
                        .requestMatchers("/api/parking/register").hasRole("ADMIN")
                        .requestMatchers("/api/parking/delete-card").hasRole("ADMIN")

                        // USER và ADMIN đều được xem bảng giá.
                        .requestMatchers(HttpMethod.GET, "/api/pricing/**").hasAnyRole("USER", "ADMIN")

                        // Chỉ ADMIN được thêm/sửa/xóa bảng giá.
                        .requestMatchers(HttpMethod.POST, "/api/pricing/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/pricing/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/pricing/**").hasRole("ADMIN")

                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "http://127.0.0.1:*"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setExposedHeaders(List.of("Authorization"));
        // Bắt buộc nếu frontend/backend khác origin mà dùng cookie.
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
