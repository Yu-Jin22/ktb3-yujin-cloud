package com.ktb3.community.file.service;

import com.ktb3.community.file.dto.ImageRequest;
import com.ktb3.community.file.entity.File;
import com.ktb3.community.file.repository.FileRepository;
import com.ktb3.community.member.entity.Member;
import com.ktb3.community.post.dto.PostDto;
import com.ktb3.community.post.entity.Post;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FileService {

    private final FileRepository fileRepository;
    private final S3Client s3Client;
    private static final int MAX_IMAGE_COUNT = 5;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.region.static}")
    private String region;


    // 회원 프로필 메타 데이터 저장
    @Transactional
    public File saveProfileImage(Member member, ImageRequest req) {

        // 기존 프로필 이미지 삭제 (있으면)
        deleteProfileImage(member);

        File newProfile = File.createProfileImage(
                member,
                req.getFilePath(),      // S3 key
                req.getFileName(),
                req.getFileSize(),
                req.getMimeType()
        );

        return fileRepository.save(newProfile);
    }

    // 프로필 이미지 하드삭제
    @Transactional
    public void deleteProfileImage(Member member) {

        fileRepository.findProfileByMemberId(member.getId())
                .ifPresent(existing -> {

                    // 1.  S3에서 삭제
                    String key = existing.getFilePath(); // S3 key
                    if (key != null && !key.isBlank()) {
                        deleteFromS3(key);
                    }

                    // 2. DB 삭제
                    fileRepository.delete(existing);
                });
    }

    // S3버킷에서 삭제
    private void deleteFromS3(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
        } catch (S3Exception e) {
            // 삭제 실패해도 서비스 동작은 유지(DB에는 없어지지만 S3에 파일 남는 건 비용차지하지만, 기능 영향은 없음)
        }
    }

    // 프로필 이미지 소프트삭제 - 회원탈퇴할때 사용
    @Transactional
    public void softDeleteProfileImage(Long memberId) {
        fileRepository.findProfileByMemberId(memberId)
                .ifPresent(file -> {
                    file.deleteFile();
                });
    }

    // S3 URL로 변환
    public String buildFileUrl(String key) {
        return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
    }

    // 프로필 이미지 조회
    public String getProfileImageUrl(Long memberId) {

        String filePath =  fileRepository.findProfileByMemberId(memberId)
                .map(File::getFilePath)
                .orElse(null);

        if (filePath == null) {
            return null;
        }

        return buildFileUrl(filePath);
    }


    @Transactional
    public Map<Long, String> getProfileImageUrls(List<Long> memberIds) {

        if (memberIds == null || memberIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // IN 쿼리로 한 번에 조회
        List<File> profileImages = fileRepository
                .findByMember_IdInAndTypeAndDeletedAtIsNull(memberIds, "profile");

        // memberId를 키로 하는 Map 생성
        return profileImages.stream()
                .collect(Collectors.toMap(
                        file -> file.getMember().getId(),
                        file -> buildFileUrl(file.getFilePath()),
                        (existing, replacement) -> existing
                ));
    }

    // 게시물 이미지 저장
    @Transactional
    public List<String> savePostImages(Post post, List<PostDto.PostCreateRequest.ImageRequest> images) {

        if (images == null || images.isEmpty()) {
            return List.of();
        }

        List<String> imageUrls = new ArrayList<>();
        int order = 1;

        for (PostDto.PostCreateRequest.ImageRequest img : images) {

            File file = File.createPostFile(
                    post,
                    img.getKey(),               // S3 key
                    img.getOriginalName(),      // 원본 파일명
                    img.getFileSize(),          // 파일크기
                    img.getMimeType(),          // MIME 타입
                    order++
            );

            fileRepository.save(file);

            imageUrls.add(buildFileUrl(img.getKey()));
        }

        return imageUrls;
    }

    // 게시물 이미지 수정
    public void replacePostImage(Post post, PostDto.PostCreateRequest.ImageRequest newImage) {

        // 1. 기존 이미지 조회 (단일 기준)
        List<File> existingFiles = fileRepository
                .findByPost_IdAndDeletedAtIsNullOrderByFileOrderAsc(post.getId());

        for (File file : existingFiles) {
            // S3 삭제
            deleteFromS3(file.getFilePath());
            // DB 삭제
            hardDeletePostImages(post.getId());
        }

        // 2. 새 이미지 저장
        File newFile = File.createPostFile(
                post,
                newImage.getKey(),
                newImage.getOriginalName(),
                newImage.getFileSize(),
                newImage.getMimeType(),
                1
        );

        fileRepository.save(newFile);
    }

    /**
     * 게시물 이미지 하드 딜리트 (수정 시 사용)
     */
    @Transactional
    public void hardDeletePostImages(Long postId) {

        List<File> files = fileRepository.findByPost_IdAndDeletedAtIsNullOrderByFileOrderAsc(postId);

        if (files.isEmpty()) {
            return;
        }

        files.forEach(file -> {
            fileRepository.delete(file);
        });

    }

    /**
     * 게시물 이미지 삭제(게시물 소프트 삭제시 이미지도 소프트 삭제)
     */
    @Transactional
    public void softDeletePostImages(Long postId) {

        List<File> files = fileRepository.findByPost_IdAndDeletedAtIsNullOrderByFileOrderAsc(postId);

        if (files.isEmpty()) {
            return;
        }

        files.forEach(file -> {
            file.deleteFile();
        });

    }


    /**
     * 게시물의 이미지 URL 목록 조회
     */
    public List<String> getPostImageUrls(Long postId) {

        return fileRepository.findByPost_IdAndDeletedAtIsNullOrderByFileOrderAsc(postId)
                .stream()
                .map(f -> buildFileUrl(f.getFilePath()))
                .collect(Collectors.toList());
    }

    public List<PostDto.ImageDto> getPostImagesForEdit(Long postId) {

        List<File> files = fileRepository.findByPost_IdAndDeletedAtIsNullOrderByFileOrderAsc(postId);

        return files.stream()
                .map(f -> new PostDto.ImageDto(
                        buildFileUrl(f.getFilePath()),  // S3 URL
                        f.getFilePath(),                // S3 key
                        f.getFileName()                 // 원본 파일명
                ))
                .collect(Collectors.toList());
    }

}
