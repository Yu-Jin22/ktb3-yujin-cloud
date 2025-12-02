package com.ktb3.community.post.service;

import com.ktb3.community.common.exception.BusinessException;
import com.ktb3.community.file.entity.File;
import com.ktb3.community.file.service.FileService;
import com.ktb3.community.member.entity.Member;
import com.ktb3.community.member.repository.MemberRepository;
import com.ktb3.community.post.dto.PostDto;
import com.ktb3.community.post.entity.Post;
import com.ktb3.community.post.repository.PostCommentRepository;
import com.ktb3.community.post.repository.PostLikeRepository;
import com.ktb3.community.post.repository.PostRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
//    private final FileRepository fileRepository;
    private final MemberRepository memberRepository;
    private final FileService fileService;
    private final PostCommentService commentService;
    private final PostLikeRepository likeRepository;
    private final PostCommentRepository commentRepository;

    public PostDto.PostListPageResponse getPostList(Long cursor, int size) {

        // 다음 페이지 존재 여부 확인용 - 실제 조회는 size+1 이니까
        int fetchSize = size + 1;

        // 1. 게시물 목록 조회 - QueryDSL
        List<Post> posts = postRepository.findPostsByCursor(cursor, fetchSize);

        // 2. 게시물 id, 작성자 id 리스트 조회
        List<Long> postIds = posts.stream().map(Post::getId).toList();

        List<Long> memberIds = posts.stream()
                .map(post -> post.getMember().getId())
                .distinct().toList();

        // 3. 프로필이미지, 좋아요/댓글 수 카운트 조회 - N+1방지
        Map<Long, String> profileUrls = fileService.getProfileImageUrls(memberIds);
        Map<Long, Long> likeCounts = getLikeCounts(postIds);
        Map<Long, Long> commentCounts = getCommentCounts(postIds);

        // 4. DTO변환
        List<PostDto.PostListResponse> response = posts.stream()
                .map(post -> PostDto.PostListResponse.from(
                post,
                likeCounts.getOrDefault(post.getId(), 0L),
                commentCounts.getOrDefault(post.getId(), 0L),
                profileUrls.get(post.getMember().getId())
        )).collect(Collectors.toList());

        // 6. 페이징 형태로 응답
        return PostDto.PostListPageResponse.of(response, size);

    }

    private Map<Long, Long> getLikeCounts(List<Long> postIds) {
        List<Map<String, Object>> results = likeRepository.countByPostIdIn(postIds);

        return results.stream()
                .collect(Collectors.toMap(
                        map -> ((Number) map.get("postId")).longValue(),
                        map -> ((Number) map.get("likeCount")).longValue()
                ));
    }

    private Map<Long, Long> getCommentCounts(List<Long> postIds) {
        List<Map<String, Object>> results = commentRepository.countByPostIdIn(postIds);

        return results.stream()
                .collect(Collectors.toMap(
                        map -> ((Number) map.get("postId")).longValue(),
                        map -> ((Number) map.get("commentCount")).longValue()
                ));
    }

    @Transactional
    public PostDto.PostDetailResponse getPostDetail(Long postId, Long currentMemberId) {

        // 1. 게시물 조회 (Member JOIN FETCH)
        Post post = postRepository.findByIdWithMember(postId)
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST,"존재하지 않는 게시물입니다."));

        // 2. 조회수 증가
        post.increaseHit();

        // 3. 이미지 URL 목록 조회
        List<String> imageUrls = fileService.getPostImageUrls(postId);

        // 4. 작성자 프로필 이미지 조회
        String authorProfileUrl = fileService.getProfileImageUrl(post.getMember().getId());

        // 5. 좋아요 수 조회
        long likeCount = likeRepository.countByPostId(postId);

        // 6. 댓글 수 조회
        long commentCount = commentRepository.countByPost_IdAndDeletedAtIsNull(postId);

        // 7. 권한 정보 조회
        boolean isAuthor = checkIsAuthor(post, currentMemberId);
        boolean isLiked = checkIsLiked(postId, currentMemberId);

        // 8. DTO 생성
        return PostDto.PostDetailResponse.of(
                post,
                imageUrls,
                authorProfileUrl,
                likeCount,
                commentCount,
                isAuthor,
                isLiked
        );
    }


    /**
     * 내가 작성한 게시물인지 확인
     */
    private boolean checkIsAuthor(Post post, Long currentMemberId) {
        if (currentMemberId == null) {
            return false;
        }
        return post.getMember().getId().equals(currentMemberId);
    }

    /**
     * 내가 좋아요를 눌렀는지 확인
     */
    private boolean checkIsLiked(Long postId, Long currentMemberId) {
        if (currentMemberId == null) {
            return false;
        }
        return likeRepository.existsByMember_IdAndPost_Id(currentMemberId, postId);
    }


    @Transactional
    public PostDto.PostResponse createPost(Long memberId, PostDto.PostCreateRequest request) {

        // 1. 작성자(회원) 확인
        Member member = memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(()-> new BusinessException(HttpStatus.BAD_REQUEST,"존재하지 않는 회원입니다."));

        // 2. 게시물 저장
        Post post = Post.builder()
                .member(member)
                .title(request.getTitle())
                .content(request.getContent())
                .build();
        postRepository.save(post);

        // 3. 이미지 저장
        List<String> imageUrls = fileService.savePostImages(post, request.getImages());

        return PostDto.PostResponse.from(post,imageUrls);

    }

    @Transactional
    public PostDto.PostEditResponse getPostForEdit(Long postId, Long memberId) {

        // 1. 게시물 조회 (Member JOIN FETCH)
        Post post = postRepository.findByIdWithMember(postId)
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST,"존재하지 않는 게시물입니다."));

        // 2. 작성자 수정 권한 확인
        validateOwnership(post, memberId);

        // 3. 이미지 전체 정보(key, fileName, url)
        List<PostDto.ImageDto> images = fileService.getPostImagesForEdit(postId);

        // 4. 반환
        return PostDto.PostEditResponse.from(post, images);
    }

    @Transactional
    public PostDto.PostResponse updatePost(Long postId, Long memberId, PostDto.PostUpdateRequest request) {

        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "존재하지 않는 게시물입니다."));

        // 작성자 검증
        validateOwnership(post, memberId);

        // 제목/내용 수정 (null이면 기존 유지)
        String newTitle = request.getTitle() != null ? request.getTitle() : post.getTitle();
        String newContent = request.getContent() != null ? request.getContent() : post.getContent();
        post.updatePost(newTitle, newContent);

        // 이미지 교체 (newImage가 있는 경우에만 동작)
        if (request.getNewImage() != null) {
            fileService.replacePostImage(post, request.getNewImage());
        }

        List<String> imageUrls = fileService.getPostImageUrls(postId);

        return PostDto.PostResponse.from(post, imageUrls);
    }

    @Transactional
    public void deletePost(Long postId, Long memberId) {
        // 1. 게시물 확인
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시물입니다."));

        // 2. 작성자 삭제 권한 확인
        validateOwnership(post, memberId);

        // 3. 게시물 삭제
        post.deletePost();

        // 4. 게시물 이미지 존재시 삭제
        fileService.softDeletePostImages(postId);

        // 5. 댓글 존재시 삭제
        commentService.softDeleteComments(postId);
    }

    // 개시물 작성자 권한 확인
    private void validateOwnership(Post post, Long memberId) {
        if (!post.isOwner(memberId)) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }
    }



}
