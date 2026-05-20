package com.example.quanlibaixesv.security;

import com.example.quanlibaixesv.exception.InvalidSessionException;
import com.example.quanlibaixesv.model.AdminAccount;
import com.example.quanlibaixesv.model.Student;
import com.example.quanlibaixesv.repository.AdminAccountRepository;
import com.example.quanlibaixesv.repository.StudentRepository;
import com.example.quanlibaixesv.service.LoginSessionService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtCookieService jwtCookieService;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private LoginSessionService loginSessionService;

    @Autowired
    private AdminAccountRepository adminRepo;

    @Autowired
    private StudentRepository studentRepo;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        String method = request.getMethod();

        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        // Không chạy JWT filter cho static file để cookie hết hạn không làm hỏng giao diện.
        if (!path.startsWith("/api/")) {
            return true;
        }

        // Các API public này phải dùng được kể cả browser đang có cookie cũ/hết hạn.
        return path.equals("/api/auth/login")
                || path.equals("/api/auth/logout")
                || path.equals("/api/password-reset/request");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String jwt = jwtCookieService.resolveToken(request);

        // Không có cookie/header token thì để Spring Security xử lý theo rule trong SecurityConfig.
        if (jwt == null || jwt.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String username = jwtService.extractUsername(jwt);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                long currentTokenVersion = findCurrentTokenVersion(username);

                if (!jwtService.isTokenValid(jwt, userDetails, currentTokenVersion)) {
                    throw new InvalidSessionException("Token không còn hợp lệ, vui lòng đăng nhập lại.");
                }

                long tokenVersion = jwtService.extractTokenVersion(jwt);
                String sessionId = jwtService.extractSessionId(jwt);

                // JWT exp quyết định thời gian phiên. SessionId chỉ kiểm tra token có còn được quản lý/cho phép hay không.
                loginSessionService.validateSession(sessionId, username, tokenVersion);

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }

            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException | InvalidSessionException ex) {
            SecurityContextHolder.clearContext();
            writeUnauthorizedResponse(response, "Token không hợp lệ, đã hết hạn hoặc phiên đăng nhập đã bị hủy.");
        }
    }

    private long findCurrentTokenVersion(String username) {
        AdminAccount admin = adminRepo.findByUsername(username).orElse(null);
        if (admin != null) {
            return admin.getTokenVersion();
        }

        Student student = studentRepo.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản"));
        return student.getTokenVersion();
    }

    private void writeUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}");
    }
}
