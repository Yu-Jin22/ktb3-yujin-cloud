package com.ktb3.community.auth.service;

import com.ktb3.community.auth.dto.AuthDto;
import com.ktb3.community.auth.repository.RefreshTokenRepository;
import com.ktb3.community.common.exception.BusinessException;
import com.ktb3.community.common.util.JwtProvider;
import com.ktb3.community.file.service.FileService;
import com.ktb3.community.member.entity.Member;
import com.ktb3.community.member.entity.MemberAuth;
import com.ktb3.community.member.repository.MemberAuthRepository;
import com.ktb3.community.member.repository.MemberRepository;
import io.jsonwebtoken.Claims;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static com.ktb3.community.common.constant.TokenConst.*;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final MemberRepository memberRepository;
    private final MemberAuthRepository memberAuthRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final TokenService tokenService;
    private final FileService fileService;

    public AuthDto.TokenResponse login(AuthDto.LoginRequest request){

        // 1. 이메일로 회원조회
        Member member = memberRepository.findByEmailAndDeletedAtIsNull(request.getEmail())
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "존재하지 않는 이메일입니다."));

        // 2. 해당 회원의 비밀번호 조회
        MemberAuth memberAuth = memberAuthRepository.findById(member.getId())
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "비밀번호가 일치하지 않습니다."));

        // 3. 2번의 값과 입력값 일치하는지 확인
        if (!passwordEncoder.matches(request.getPassword(), memberAuth.getPassword())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "비밀번호가 일치하지 않습니다.");
        }

        // 토큰 발급
        TokenService.TokenInfo tokens = tokenService.createTokens(member);

        // 프로필 이미지 조회
        String profileUrl = Optional.ofNullable(fileService.getProfileImageUrl(member.getId()))
                .orElse("");

        return new AuthDto.TokenResponse(
                member.getId(), member.getEmail(), member.getNickname(),profileUrl,
                tokens.accessToken(),
                tokens.refreshToken()
        );

    }

    public AuthDto.TokenResponse refresh(HttpServletRequest request){

        // 1. 쿠키에서 리프레시 토큰 추출
        String refreshToken = jwtProvider.extractToken(request, REFRESH_TOKEN);
        if (refreshToken == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Refresh Token이 없습니다.");
        }

        // 2. Refresh Token 검증
        var savedToken = tokenService.validateRefreshToken(refreshToken);

        // 3. 회원 정보 조회
        Member member = memberRepository.findByIdAndDeletedAtIsNull(savedToken.getMemberId())
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "회원 정보를 찾을 수 없습니다."));

        // 4. 새 Access / Refresh Token 생성 (회전)
        TokenService.TokenInfo tokens = tokenService.rotateTokens(savedToken, member);

        return new AuthDto.TokenResponse(member.getId(), member.getEmail(), member.getNickname(),null, tokens.accessToken(),
                tokens.refreshToken());

    }

    @Transactional
    public void logout(HttpServletRequest request) {

        String refreshToken = jwtProvider.extractToken(request, REFRESH_TOKEN);
        if (refreshToken == null) {
            return; // 쿠키가 이미 없으면 그냥 종료
        }
        try {
            // memberId 추출 후 토큰 삭제
            Claims claims = jwtProvider.claims(refreshToken);
            Long memberId = claims.get("id", Long.class);

            tokenService.deleteRefreshToken(memberId);
        } catch (BusinessException e) {
            // 만료된 토큰이면 무시 (이미 무효)
        }

    }

    @Transactional
    public void changePassword(Long memberId, String currentPassword, String newPassword) {

        // 1. 회원 조회
        Member member = memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(()-> new BusinessException(HttpStatus.BAD_REQUEST,"회원을 찾을 수 없습니다."));

        // 2. 인증정보 조회
        MemberAuth memberAuth = memberAuthRepository.findById(memberId)
                .orElseThrow(()-> new BusinessException(HttpStatus.BAD_REQUEST, "인증정보를 찾을 수 없습니다."));

        // 3. 현재 비밀번호 맞는지 확인
        if(!passwordEncoder.matches(currentPassword, memberAuth.getPassword())){
            throw new BusinessException(HttpStatus.BAD_REQUEST, "현재 비밀번호가 일지하지 않습니다.");
        }

        // 4. 새로운 비밀번호랑 현재 비밀번호가 다른지 확인
        if (passwordEncoder.matches(newPassword, memberAuth.getPassword())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "새 비밀번호는 현재 비밀번호와 달라야 합니다");
        }

        // 5. 새로운 비밀번호 암호화하여 변경
        String encodedPassword = passwordEncoder.encode(newPassword);
        memberAuth.changePassword(encodedPassword);
    }

}
