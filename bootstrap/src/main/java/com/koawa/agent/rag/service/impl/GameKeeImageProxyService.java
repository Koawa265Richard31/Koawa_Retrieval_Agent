/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.koawa.agent.rag.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

/**
 * GameKee 图片代理服务：按需抓取外部图片并缓存到 RustFS(S3)，图片不再直连外部 CDN。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameKeeImageProxyService {

    private static final String BUCKET = "gakumas-images";
    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "cdnimg-v2.gamekee.com", "cdnimg.gamekee.com", "img.gamekee.com");
    private static final String GAMEKEE_REFERER = "https://www.gamekee.com/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    private static final long MAX_BYTES = 8L * 1024 * 1024;

    private final S3Client s3Client;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private volatile boolean bucketEnsured = false;

    public ImageResult resolve(String url) {
        validate(url);
        String key = objectKey(url);
        InputStream cached = tryGet(key);
        if (cached != null) {
            return new ImageResult(cached, contentTypeOf(url));
        }
        byte[] body = fetch(url);
        try {
            ensureBucket();
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(BUCKET)
                            .key(key)
                            .contentType(contentTypeOf(url))
                            .build(),
                    RequestBody.fromBytes(body));
        } catch (Exception e) {
            log.warn("cache put failed for key={}, serving fetched bytes anyway", key, e);
        }
        return new ImageResult(new ByteArrayInputStream(body), contentTypeOf(url));
    }

    private InputStream tryGet(String key) {
        try {
            return s3Client.getObject(GetObjectRequest.builder()
                    .bucket(BUCKET)
                    .key(key)
                    .build());
        } catch (NoSuchKeyException e) {
            return null;
        } catch (NoSuchBucketException e) {
            return null;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    private void ensureBucket() {
        if (bucketEnsured) {
            return;
        }
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
            bucketEnsured = true;
            log.info("created s3 bucket {}", BUCKET);
        } catch (S3Exception e) {
            if (e.statusCode() == 409 || e.statusCode() == 400) {
                bucketEnsured = true;
            } else {
                throw e;
            }
        }
    }

    private byte[] fetch(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Referer", GAMEKEE_REFERER)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "image/avif,image/webp,image/png,image/*,*/*;q=0.8")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200 || response.body().length == 0) {
                throw new IllegalStateException("upstream status " + response.statusCode());
            }
            if (response.body().length > MAX_BYTES) {
                throw new IllegalStateException("image too large: " + response.body().length);
            }
            return response.body();
        } catch (Exception e) {
            throw new IllegalStateException("fetch upstream failed: " + url, e);
        }
    }

    private void validate(String url) {
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || !ALLOWED_HOSTS.contains(uri.getHost())) {
                throw new IllegalArgumentException("url not allowed: " + url);
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid url: " + url);
        }
    }

    private String objectKey(String url) {
        return "img/" + sha256(url) + extensionOf(url);
    }

    private String extensionOf(String url) {
        String path = URI.create(url).getPath().toLowerCase(Locale.ROOT);
        int dot = path.lastIndexOf('.');
        if (dot >= 0 && dot < path.length() - 1 && path.substring(dot).matches("\\.[a-z0-9]{2,5}")) {
            return path.substring(dot);
        }
        return ".png";
    }

    private String contentTypeOf(String url) {
        return switch (extensionOf(url)) {
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".webp" -> "image/webp";
            case ".gif" -> "image/gif";
            case ".svg" -> "image/svg+xml";
            default -> "image/png";
        };
    }

    private String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("sha256 failed", e);
        }
    }

    public record ImageResult(InputStream inputStream, String contentType) {
    }
}
