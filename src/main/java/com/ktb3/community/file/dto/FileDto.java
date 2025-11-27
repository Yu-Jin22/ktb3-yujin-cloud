package com.ktb3.community.file.dto;

import lombok.Getter;
import lombok.Setter;

public class FileDto {

    @Getter
    @Setter
    public static class FileRequest {
        private String filePath;
        private String fileName;
        private Long fileSize;
        private String mimeType;
        private String fileType;  // profile or post
    }
}
