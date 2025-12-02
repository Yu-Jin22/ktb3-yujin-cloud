package com.ktb3.community.file.entity;

import com.ktb3.community.member.entity.Member;
import com.ktb3.community.post.entity.Post;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 파일 엔티티
 * 1. Member : File = 1:N (단방향)
 *  →
 * 2. Post : File = 1:N (단방향)
 *  →
 */
@Entity
@Getter
@Table(name = "file")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class File {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "file_id")
    private Long id;

    @Column(nullable = false, length = 30)
    private String type;
    @Column(name = "file_path", nullable = false, length = 255)
    private String filePath;
    @Column(name = "file_name", length = 255)
    private String fileName;
    @Column(name="file_size")
    private Long fileSize;
    @Column(name = "mime_type", length = 50)
    private String mimeType;
    @Column(name = "file_order")
    private Integer fileOrder = 1;

    @CreatedDate
    @Column(name = "created_at",updatable = false)
    private LocalDateTime createdAt;
    @Column(name="deleted_at")
    private LocalDateTime deletedAt;

    // 회원 단방향
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="member_id")
    private Member member;

    // 게시물 단방향
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="post_id")
    private Post post;

    // 정적 팩토리 메서드 사용 : 회원이냐 게시물용이냐만 다르고 나머지는 같아서 생서자 2개로는 오류남
    // 회원 프로필용
    public static File createProfileImage(Member member, String filePath,
                                          String fileName, Long fileSize,
                                          String mimeType){
        File file = new File();
        file.member = member;
        file.type = "profile";
        file.post = null;       // 명확하게 null
        file.filePath = filePath;
        file.fileName = fileName;
        file.fileSize = fileSize;
        file.mimeType = mimeType;
        file.fileOrder = 1;
        return file;
    }

    // 게시물용
    public static File createPostFile(Post post, String filePath,
                                      String fileName, Long fileSize,
                                      String mimeType, Integer order) {
        File file = new File();
        file.post = post;
        file.type = "post";
        file.member = null;       // 명확하게 null
        file.filePath = filePath; // S3 key
        file.fileName = fileName;
        file.fileSize = fileSize;
        file.mimeType = mimeType;
        file.fileOrder = order;
        return file;
    }

    // 파일 순서 변경
    public void updateOrder(Integer newOrder) {
        this.fileOrder = newOrder;
    }

    // 소프트 삭제
    public void deleteFile(){
        this.deletedAt = LocalDateTime.now();
    }

}
