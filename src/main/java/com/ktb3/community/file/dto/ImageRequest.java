package com.ktb3.community.file.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ImageRequest {

    private String filePath;   // S3 Key
    private String fileName;
    private Long fileSize;
    private String mimeType;
    private Boolean delete;    // optional

}
