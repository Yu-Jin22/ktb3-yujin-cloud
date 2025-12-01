package com.ktb3.community.file.dto;

public record PresignedUrlResponse(
        String uploadUrl,
        String key
) {}