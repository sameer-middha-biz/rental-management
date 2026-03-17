package com.rental.pms.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;

/**
 * AWS S3 client configuration.
 * Supports configurable endpoint for LocalStack in local/test environments.
 */
@Configuration
public class S3Config {

    private final String region;
    private final String endpoint;
    private final String accessKey;
    private final String secretKey;

    public S3Config(
            @Value("${pms.s3.region}") String region,
            @Value("${pms.s3.endpoint:}") String endpoint,
            @Value("${pms.s3.access-key:}") String accessKey,
            @Value("${pms.s3.secret-key:}") String secretKey) {
        this.region = region;
        this.endpoint = endpoint;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    @Bean
    public S3Client s3Client() {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region));

        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint))
                    .forcePathStyle(true);
        }

        if (accessKey != null && !accessKey.isBlank()
                && secretKey != null && !secretKey.isBlank()) {
            builder.credentialsProvider(
                    StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)));
        }

        return builder.build();
    }
}
