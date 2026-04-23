package com.rental.pms.modules.property.dto;

import java.time.Instant;

/**
 * Response for a pre-signed S3 PUT URL.
 * Client PUTs the file to {@code uploadUrl} with the matching Content-Type header,
 * then calls POST /photos with {@code s3Key} to confirm the upload.
 */
public record GenerateUploadUrlResponse(
        String uploadUrl,
        String s3Key,
        String httpMethod,
        Instant expiresAt
) {
}
