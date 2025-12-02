package com.ktb3.community.post.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb3.community.auth.annotation.AuthMemberId;
import com.ktb3.community.post.dto.PostDto;
import com.ktb3.community.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    /**
     * 게시물 목록 조회 - 커서 페이징
     */
    @GetMapping
    public ResponseEntity<PostDto.PostListPageResponse> getPosts(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {

        // 최대 크기 제한
        if (size > 100) {
            size = 100;
        }

        PostDto.PostListPageResponse response = postService.getPostList(cursor, size);

        return ResponseEntity.ok(response);
    }

    /**
     * 게시물 상세 조회
     */
    @GetMapping("/{postId}")
    public ResponseEntity<PostDto.PostDetailResponse> getPostDetail(
            @PathVariable Long postId,
            @AuthMemberId Long memberId) {

        // 게시물 상세 조회
        PostDto.PostDetailResponse response = postService.getPostDetail(postId, memberId);

        return ResponseEntity.ok(response);
    }


    /**
     * 게시물 등록
     */
    @PostMapping
    public ResponseEntity<PostDto.PostResponse> createPost(
            @RequestBody PostDto.PostCreateRequest request,
            @AuthMemberId Long memberId) {

        PostDto.PostResponse response = postService.createPost(memberId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 게시물 수정용 상세 불러오기
     */
    @GetMapping("/edit/{postId}")
    public ResponseEntity<PostDto.PostEditResponse> getPostForEdit(
            @PathVariable Long postId,
            @AuthMemberId Long memberId) {

        PostDto.PostEditResponse response = postService.getPostForEdit(postId, memberId);
        return ResponseEntity.ok(response);
    }

    /**
     * 게시물 수정
     */
    @PatchMapping("/{postId}")
    public ResponseEntity<PostDto.PostResponse> updatePost(
            @PathVariable Long postId,
            @RequestBody PostDto.PostUpdateRequest request,
            @AuthMemberId Long memberId
    ) {
        PostDto.PostResponse response = postService.updatePost(postId, memberId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 게시물 삭제
     */
    @DeleteMapping("/{postId}")
    public ResponseEntity<Map<String, String>> deletePost(@PathVariable Long postId, @AuthMemberId Long memberId) {

        postService.deletePost(postId, memberId);

        return ResponseEntity.ok(Map.of("message", "게시물이 삭제되었습니다."));

    }


}
