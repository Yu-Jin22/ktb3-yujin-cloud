package com.ktb3.community.file.service;

import com.ktb3.community.common.exception.BusinessException;
import com.ktb3.community.file.dto.FileDto;
import com.ktb3.community.file.entity.File;
import com.ktb3.community.file.repository.FileRepository;
import com.ktb3.community.member.entity.Member;
import com.ktb3.community.post.entity.Post;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FileService {

    private final FileRepository fileRepository;
    private static final int MAX_IMAGE_COUNT = 5;


    /**
     * 회원 프로필 변경
     * @param member
     * @param profileImage
     * @return
     */
    @Transactional
    public String saveProfileImage(Member member, MultipartFile profileImage) {

        if (profileImage == null || profileImage.isEmpty()) {
            throw new IllegalArgumentException("프로필 이미지가 존재하지 않습니다.");
        }

        // 기존 프로필 이미지 삭제 (있으면)
        fileRepository.findProfileByMemberId(member.getId())
                .ifPresent(existing -> {
                    // TODO: S3 삭제
                    fileRepository.delete(existing);
                });

        // TODO: S3 업로드로 교체
//        String uploadUrl = uploadToS3(profileImage);
        String uploadUrl = "https://sample_" + System.currentTimeMillis() + ".jpg";

        File newProfile = File.createProfileImage(
                member,
                uploadUrl,
                profileImage.getOriginalFilename(),
                profileImage.getSize(),
                profileImage.getContentType()
        );

        fileRepository.save(newProfile);
        return uploadUrl;
    }

    // 회원 프로필관련 처리 클라이언트(람다) 처리로 DB에 저장만함
    @Transactional
    public void saveProfileImageByLambda(Member member, FileDto.FileRequest fileReq) {

        if (fileReq == null || fileReq.getFilePath() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "프로필 이미지 정보가 없습니다.");
        }

        // 기존 이미지 soft delete
        fileRepository.findProfileByMemberId(member.getId())
                .ifPresent(existing -> {
                    // TODO: S3 삭제
                    fileRepository.delete(existing);
                });

        File newProfile = File.createProfileImage(
                member,
                fileReq.getFilePath(),
                fileReq.getFileName(),
                fileReq.getFileSize(),
                fileReq.getMimeType()
        );

        fileRepository.save(newProfile);
    }

    /**
     * 프로필 이미지 하드삭제 - 사용자가 프로필사진 삭제할때
     * @param member
     */
    @Transactional
    public void deleteProfileImage(Member member) {
        fileRepository.findProfileByMemberId(member.getId())
                .ifPresent(file -> {
                    // TODO: S3 삭제
                    // 회원프로필 하드삭제
                    fileRepository.delete(file);
                });
    }

    /**
     * 프로필 이미지 소프트삭제 - 회원탈퇴할때 사용
     * @param memberId
     */
    @Transactional
    public void softDeleteProfileImage(Long memberId) {
        fileRepository.findProfileByMemberId(memberId)
                .ifPresent(file -> {
                    file.deleteFile();
                });
    }

    /**
     * 프로필 이미지 조회
     * @param memberId
     * @return
     */
    public String getProfileImageUrl(Long memberId) {
        return fileRepository.findProfileByMemberId(memberId)
                .map(File::getFilePath)
                .orElse(null);
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
                        File::getFilePath,
                        (existing, replacement) -> existing  // 중복 시 기존 값 유지
                ));
    }

    /**
     * 게시물 이미지 복수 저장
     * @param post
     * @param images
     * @return
     */
    @Transactional
    public List<String> savePostImages(Post post, List<MultipartFile> images) {

        List<String> imageUrls = new ArrayList<>();

        if (images == null || images.isEmpty()) {
            return imageUrls;
        }

        // 이미지 개수 검증
        validateImageCount(images.size());

        for (int i = 0; i < images.size(); i++) {
            MultipartFile image = images.get(i);

            if (image == null || image.isEmpty()) continue;

            // 이미지 단일 저장
            String imageUrl = savePostImage(post, image, i + 1);
            imageUrls.add(imageUrl);
        }
        return imageUrls;

    }

    /**
     * 게시물 이미지 단일 저장
     * @param post
     * @param image
     * @param order
     * @return
     */
    private String savePostImage(Post post, MultipartFile image, int order) {
        try {

            // TODO: S3 업로드로 교체
            // String filePath = fileStorageService.saveFile(image, "posts");
            String filePath = "posts/sample_" + System.currentTimeMillis() + ".jpg";
            String imageUrl = "https://sample_" + filePath;

            // File 엔티티 생성 및 저장
            File postFile = File.createPostFile(
                    post,
                    filePath,
                    image.getOriginalFilename(),
                    image.getSize(),
                    image.getContentType(),
                    order
            );

            fileRepository.save(postFile);

            return imageUrl;

        } catch (Exception e) {
            throw new RuntimeException("게시물 이미지 저장에 실패했습니다: " + image.getOriginalFilename(), e);
        }
    }

    /**
     * 이미지 개수 검증
     * @param count
     */
    private void validateImageCount(int count) {

        if (count > MAX_IMAGE_COUNT) {
            throw new IllegalArgumentException(
                    String.format("이미지는 최대 %d개까지 업로드 가능합니다.", MAX_IMAGE_COUNT));
        }
    }

    /**
     * 게시물 이미지 추가(게시물 수정시)
     * @param post
     * @param images
     * @return
     */
    @Transactional
    public List<String> addPostImages(Post post, List<MultipartFile> images) {

        List<String> imageUrls = new ArrayList<>();

        if (images == null || images.isEmpty()) {
            return imageUrls;
        }

        // 1. 현재 이미지 개수 확인
        int currentCount = countPostImages(post.getId());
        int newCount = images.size();

        // 2. 최대 개수 검증
        if (currentCount + newCount > MAX_IMAGE_COUNT) {
            throw new IllegalArgumentException(
                    String.format("이미지는 최대 %d개까지 업로드 가능합니다. (현재: %d개, 추가: %d개)",
                            MAX_IMAGE_COUNT, currentCount, newCount));
        }

        // 3. 다음 order 계산
        int nextOrder = currentCount + 1;

        // 4. 이미지 저장
        for (MultipartFile image : images) {

            if (image == null || image.isEmpty()) {continue;}

            String imageUrl = savePostImage(post, image, nextOrder);
            imageUrls.add(imageUrl);
            nextOrder++;
        }

        return imageUrls;
    }

    /**
     * 게시물의 이미지 개수 조회
     */
    public int countPostImages(Long postId) {

        return fileRepository.countByPost_IdAndDeletedAtIsNull(postId);
    }

    /**
     * 게시물 이미지 하드 딜리트 (수정 시 사용)
     * @param postId
     * @param fileIds
     */
    @Transactional
    public void hardDeletePostImages(Long postId, List<Long> fileIds) {

        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }

        for (Long fileId : fileIds) {

            // 1. 파일 확인
            File file = fileRepository.findById(fileId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 파일입니다: " + fileId));

            // 2. 해당 게시물의 이미지인지 확인
            if (!file.getPost().getId().equals(postId)) {
                throw new IllegalArgumentException("해당 게시물의 이미지가 아닙니다: " + fileId);
            }

            // 3. 하드 딜리트
            hardDeleteFile(fileId);
        }

        // 4. order 재정렬
        reorderPostImages(postId);

    }

    @Transactional
    public void hardDeleteFile(Long fileId) {

        File file = fileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 파일입니다."));

        String filePath = file.getFilePath();

        // 1. DB에서 완전 삭제
        fileRepository.delete(file);

        // 2. 실제 파일 삭제
//        try {
//            fileStorageService.deleteFile(filePath);
//        } catch (IOException e) {
//            에외처리 문구 추가
//        }
    }

    /**
     * 게시물 이미지 삭제(게시물 소프트 삭제시 이미지도 소프트 삭제)
     * @param postId
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
     * 게시물 이미지 order 재정렬
     * @param postId
     */
    @Transactional
    public void reorderPostImages(Long postId) {

        // Query Method 사용
        List<File> files = fileRepository.findByPost_IdAndDeletedAtIsNullOrderByFileOrderAsc(postId);

        for (int i = 0; i < files.size(); i++) {
            File file = files.get(i);
            int newOrder = i + 1;

            if (!file.getFileOrder().equals(newOrder)) {
                file.updateOrder(newOrder);
            }
        }

    }

    /**
     * 게시물의 이미지 URL 목록 조회
     * @param postId
     * @return
     */
    public List<String> getPostImageUrls(Long postId) {

        return fileRepository.findByPost_IdAndDeletedAtIsNullOrderByFileOrderAsc(postId)
                .stream()
                .map(File::getFilePath)  // filePath가 URL
                .collect(Collectors.toList());
    }

}
