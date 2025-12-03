package com.ktb3.community.common.filter;

import com.ktb3.community.common.util.JwtProvider;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import static com.ktb3.community.common.constant.TokenConst.*;
import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();

        // 제외 경로는 필터 건너뜀
        if (isExcludedPath(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 쿠키에서 토큰 꺼내기
        String token = jwtProvider.extractToken(request,ACCESS_TOKEN);

        // Access Token 유효성 검증
        if (!jwtProvider.validate(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        // 유효한 토큰이면 memberId를 request에 저장
        Claims claims = jwtProvider.claims(token);
        Long memberId = claims.get("id", Long.class);
        request.setAttribute("memberId", memberId);

        // 다음 필터로 진행
        filterChain.doFilter(request, response);
    }

    // 필터 제외 경로 설정 - shouldNotFilter는 분기처리가 불가하여 새로운 함수지정
    private boolean isExcludedPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        // favicon.ico 요청 예외
        if ("/favicon.ico".equals(path)) return true;

        // 회원가입 (POST /users)
        if ("/api/users".equals(path) && "POST".equalsIgnoreCase(method)) return true;


        // 이메일/닉네임 중복확인 (항상 필터 제외)
        if (("/api/users/email".equals(path) || "/api/users/nickname".equals(path))
                && "POST".equalsIgnoreCase(method)) {
            return true;
        }

        // 로그인 (POST /auth)
        if ("/api/auth".equals(path) && "POST".equalsIgnoreCase(method)) return true;


        // 토큰 재발급 (/auth/refresh)
        if ("/api/auth/refresh".equals(path)) return true;


        // 푸터에 있는 페이지
        if (path.startsWith("/api/terms") || path.startsWith("/api/privacy")) return true;


        return false;
    }



}
