package com.ktb3.community.file.controller;

import com.ktb3.community.auth.annotation.AuthMemberId;
import com.ktb3.community.file.dto.PresignedUrlRequest;
import com.ktb3.community.file.dto.PresignedUrlResponse;
import com.ktb3.community.file.service.S3Service;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/files")
public class FileController {

    private final S3Service s3Service;


    // presigned-url 요청
    @PostMapping("/presigned")
    public PresignedUrlResponse presigned(
            @RequestBody @Valid PresignedUrlRequest request,
            @AuthMemberId Long memberId
    ) {
        return s3Service.createPresignedUrl(request, memberId);
    }
}
