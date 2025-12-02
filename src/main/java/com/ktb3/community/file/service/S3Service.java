package com.ktb3.community.file.service;

import com.ktb3.community.common.exception.BusinessException;
import com.ktb3.community.file.dto.PresignedUrlRequest;
import com.ktb3.community.file.dto.PresignedUrlResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Presigner s3Presigner;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    /** S3에 이미지 업로드용 PresignedUrl 발급*/
    public PresignedUrlResponse createPresignedUrl(PresignedUrlRequest request, Long memberId) {

        // S3 object 경로 생성
        String key = buildS3Key(request, memberId);

        // S3에 업로드할 파일 요청 정보
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(request.getContentType())
                .build();

        // PresignedUrl 발급 요청
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5))
                .putObjectRequest(objectRequest)
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);

        return new PresignedUrlResponse(
                presigned.url().toString(),
                key
        );
    }

    /** 프로필 / 게시물 이미지 S3 Key 생성 */
    private String buildS3Key(PresignedUrlRequest request, Long memberId) {
        String ext = StringUtils.getFilenameExtension(request.getFileName());
        String uuid = UUID.randomUUID().toString();

        if ("profile".equals(request.getType())) {
            return "profile/" + memberId + "/" + uuid + "." + ext;
        }
        if ("post".equals(request.getType())) {
            return "post/" + memberId + "/" + uuid + "." + ext;
        }

        throw new BusinessException(HttpStatus.BAD_REQUEST,"지원하지 않는 type: "+ request.getType());
    }


}
