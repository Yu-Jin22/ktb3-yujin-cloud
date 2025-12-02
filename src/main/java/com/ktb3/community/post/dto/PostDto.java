package com.ktb3.community.post.dto;

import com.ktb3.community.post.entity.Post;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

public class PostDto {

    @Getter
    @Builder
    public static class PostListResponse{

        private Long postId;
        private String title;

        // 통계
        private long hit;                // 조회수
        private long likeCount;          // 좋아요 수
        private long commentCount;       // 댓글 수

        // 작성자 정보
        private Long authorId;
        private String authorNickname;
        private String authorProfileImageUrl;

        private LocalDateTime createdAt;

        public static PostListResponse from(Post post,
                                            long likeCount,
                                            long commentCount,
                                          String authorProfileImageUrl) {
            return PostListResponse.builder()
                    .postId(post.getId())
                    .title(post.getTitle())
                    .hit(post.getHit())
                    .likeCount(likeCount)
                    .commentCount(commentCount)
                    .authorId(post.getMember().getId())
                    .authorNickname(post.getMember().getNickname())
                    .authorProfileImageUrl(authorProfileImageUrl)
                    .createdAt(post.getCreatedAt())
                    .build();

        }
    }

    @Getter
    @Builder
    public static class PostListPageResponse {

        private List<PostListResponse> posts; // 게시물 리스트
        private Long nextCursor;  // 다음 페이지 커서 (마지막 게시물 ID)
        private boolean hasNext;  // 다음 페이지 존재 여부

        public static PostListPageResponse of(List<PostListResponse> posts, int requestSize) {

            // 게시물 리스트의 사이즈가 더 크면 다음페이지 존재
            boolean hasNext = posts.size() > requestSize;

            // 실제 반환할 데이터 자르기
            List<PostListResponse> content = hasNext
                    ? posts.subList(0, requestSize)
                    : posts;

            // nextCursor 계산: 마지막 게시물의 ID
            Long nextCursor = content.isEmpty()
                    ? null
                    : content.get(content.size() - 1).getPostId();

            return PostListPageResponse.builder()
                    .posts(content)
                    .nextCursor(nextCursor)
                    .hasNext(hasNext)
                    .build();
        }
    }

    @Getter
    @Builder
    public static class PostDetailResponse {

        // 게시물 기본 정보
        private Long postId;
        private String title;
        private String content;
        private List<String> imageUrls;

        // 통계
        private long hit;
        private long likeCount;
        private long commentCount;

        // 작성자 정보
        private Long authorId;
        private String authorNickname;
        private String authorProfileImageUrl;

        private final LocalDateTime createdAt;

        // 권한 정보 (프론트에서 버튼 표시 여부 판단)
        private final boolean isAuthor;  // 내가 작성한 게시물인가?
        private final boolean isLiked;   // 내가 좋아요를 눌렀는가?

        public static PostDetailResponse of(Post post,
                                            List<String> imageUrls,
                                            String profileImageUrl,
                                            long likeCount,
                                            long commentCount,
                                            boolean isAuthor,
                                            boolean isLiked) {
            return PostDetailResponse.builder()
                    .postId(post.getId())
                    .title(post.getTitle())
                    .content(post.getContent())
                    .imageUrls(imageUrls)
                    .hit(post.getHit())
                    .likeCount(likeCount)
                    .commentCount(commentCount)
                    .authorId(post.getMember().getId())
                    .authorNickname(post.getMember().getNickname())
                    .authorProfileImageUrl(profileImageUrl)
                    .createdAt(post.getCreatedAt())
                    .isAuthor(isAuthor)
                    .isLiked(isLiked)
                    .build();
        }
    }



    @Getter
    @NoArgsConstructor
    public static class PostCreateRequest{

        @NotBlank(message = "제목을 입력해주세요.")
        @Size(max = 26, message = "제목은 26자 이하로 입력해주세요")
        private String title;
        @NotBlank(message = "내용을 입력해주세요.")
        private String content;

        private List<ImageRequest> images;

        @Builder
        public PostCreateRequest(String title, String content, List<ImageRequest> images) {
            this.title = title;
            this.content = content;
            this.images = images;
        }

        @Getter
        @NoArgsConstructor
        public static class ImageRequest {
            private String key;          // S3 key
            private String originalName; // 원본 파일명
            private Long fileSize;       // 파일 크기
            private String mimeType;     // MIME 타입
        }
    }

    // 게시물 등록/삭제 응답
    @Getter
    @Builder
    public static class PostResponse {
        private Long postId;
        private String title;
        private String content;
        private String author;
        private List<String> imageUrls;

        public static PostResponse from(Post post,List<String> imageUrls){
            return PostResponse.builder()
                    .postId(post.getId())
                    .title(post.getTitle())
                    .content(post.getContent())
                    .imageUrls(imageUrls)
                    .author(post.getMember().getNickname())
                    .build();
        }
    }


    @Getter
    @NoArgsConstructor
    public static class PostUpdateRequest {

        private String title;
        private String content;

        // 새로 교체할 이미지 (없으면 null)
        private PostCreateRequest.ImageRequest newImage;
    }

    @Getter
    @AllArgsConstructor
    public static class ImageDto {
        private String url;      // S3 URL (미리보기)
        private String key;      // S3 key (삭제/유지 판단용)
        private String fileName; // 원본 파일명
    }

    // 게시물 수정 응답
    @Getter
    @AllArgsConstructor
    public static class PostEditResponse {
        private Long postId;
        private String title;
        private String content;
        private List<ImageDto> images; // 단일 이미지라도 배열 형태로 통일

        public static PostEditResponse from(Post post, List<ImageDto> images) {
            return new PostEditResponse(
                    post.getId(),
                    post.getTitle(),
                    post.getContent(),
                    images
            );
        }
    }


}
