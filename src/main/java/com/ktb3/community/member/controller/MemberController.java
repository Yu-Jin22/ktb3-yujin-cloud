package com.ktb3.community.member.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb3.community.auth.annotation.AuthMemberId;
import com.ktb3.community.auth.service.AuthService;
import com.ktb3.community.auth.util.CookieUtil;
import com.ktb3.community.common.exception.BusinessException;
import com.ktb3.community.file.dto.FileDto;
import com.ktb3.community.member.dto.MemberDto;
import com.ktb3.community.member.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final AuthService authService;
    private final CookieUtil cookieUtil;

    /**
     * 이메일 중복확인
     * @param request
     * @return
     */
    @PostMapping("/email")
    public ResponseEntity<Map<String, Boolean>> checkEmail(@Valid @RequestBody MemberDto.CheckEmailRequest request){
        boolean isDuplicate = memberService.isEmailDuplicate(request.getEmail());
        return ResponseEntity.ok(Map.of("isDuplicate", isDuplicate));
    }

    /**
     * 닉네임 중복확인
     * @param request
     * @return
     */
    @PostMapping("/nickname")
    public ResponseEntity<Map<String,Boolean>> checkNickname(@Valid @RequestBody MemberDto.CheckNicknameRequest request) {
        boolean isDuplicate = memberService.isNicknameDuplicate(request.getNickname());
        return ResponseEntity.ok(Map.of("isDuplicate", isDuplicate));
    }

    /**
     * 회원가입
     * @param request
     * @return
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> signUp(@Valid @RequestBody MemberDto.SignUpRequest request){
        memberService.signUp(request);
        return ResponseEntity.ok(Map.of("message", "회원가입이 완료되었습니다."));
    }

    /**
     * 회원 정보 조회
     * @param memberId
     * @return
     */
    @GetMapping("/me")
    public ResponseEntity<MemberDto.DetailResponse> getMember(@AuthMemberId Long memberId) {

        MemberDto.DetailResponse response = memberService.getMemberDetail(memberId);
        return ResponseEntity.ok(response);
    }

    /**
     * 회원 정보 수정
     * @param nickname
     * @param profileFileInfo
     * @param deleteProfileImage
     * @param memberId
     * @return
     * @throws JsonProcessingException
     */
    @PatchMapping(value = "/{me}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MemberDto.DetailResponse> updateMember(
            @RequestParam(value = "nickname", required = false)
            @Size(min = 1, max = 10, message = "닉네임은 1자 이상 10자 이하여야 합니다")
            @Pattern(regexp = "^\\S+$", message = "닉네임에 공백을 사용할 수 없습니다")
            String nickname,
            @RequestParam(required = false) String profileInfo, // JSON 문자열
            @RequestParam(value = "deleteProfileImage", required = false, defaultValue = "false") Boolean deleteProfileImage,
            @AuthMemberId Long memberId) throws JsonProcessingException {

        // 최소한 하나는 수정되어야 함
        if (nickname == null && profileInfo == null && !deleteProfileImage) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "수정할 내용이 없습니다");
        }

        FileDto.FileRequest fileRequest = null;
        if (profileInfo != null) {
            ObjectMapper mapper = new ObjectMapper();
            fileRequest = mapper.readValue(profileInfo, FileDto.FileRequest.class);
        }

        MemberDto.DetailResponse response = memberService.updateMember(memberId, nickname, fileRequest ,deleteProfileImage);

        return ResponseEntity.ok(response);
    }

    /**
     * 회원탈퇴
     * @param memberId
     * @param request
     * @return
     */
    @DeleteMapping("/withdraw")
    public ResponseEntity<Map<String, String>> deleteMember (@AuthMemberId Long memberId, HttpServletRequest request) {

        // 1. 탈퇴
        memberService.deleteMember(memberId);

        // 2. 로그아웃
        authService.logout(request);

        // 3. 브라우저 쿠키 삭제
        ResponseCookie atCookie = cookieUtil.deleteAccessTokenCookie();
        ResponseCookie rtCookie = cookieUtil.deleteRefreshTokenCookie();

        return ResponseEntity.ok()
                .headers(h -> {
                    h.add(HttpHeaders.SET_COOKIE, atCookie.toString());
                    h.add(HttpHeaders.SET_COOKIE, rtCookie.toString());
                })
                .body(Map.of("message", "회원 탈퇴가 완료되었습니다."));
    }

}
