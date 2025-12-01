package com.ktb3.community.file.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PresignedUrlRequest {

    private String type;        // "profile" | "post"
    private Long postId;        // 게시물 이미지일 때만 사용
    private Long memberId;      // 프로필 이미지일 때만 사용
    private String fileName;
    private String contentType;
    private Long fileSize;
}