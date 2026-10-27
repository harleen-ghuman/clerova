package com.clerova.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Files in a private Supabase Storage bucket. Only the backend uses the
 * service key; the app gets files through Clerova's API after its usual
 * sign-in and workspace checks.
 */
@Component
public class SupabaseFileStorage implements FileStorage {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String baseUrl;
    private final String serviceKey;
    private final String bucket;

    public SupabaseFileStorage(
            @Value("${app.storage.supabase-url:}") String supabaseUrl,
            @Value("${app.storage.service-key:}") String serviceKey,
            @Value("${app.storage.bucket:attachments}") String bucket
    ) {
        this.baseUrl = supabaseUrl == null ? "" : supabaseUrl.replaceAll("/+$", "");
        this.serviceKey = serviceKey == null ? "" : serviceKey.trim();
        this.bucket = bucket;
    }

    @Override
    public boolean isEnabled() {
        return !baseUrl.isBlank() && !serviceKey.isBlank();
    }

    @Override
    public void put(String path, byte[] content, String contentType) {
        HttpRequest request = request(path)
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", contentType)
                .header("x-upsert", "true")
                .POST(HttpRequest.BodyPublishers.ofByteArray(content))
                .build();

        send(request, "save");
    }

    @Override
    public byte[] get(String path) {
        HttpRequest request = request(path)
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();

        return send(request, "read");
    }

    private HttpRequest.Builder request(String path) {
        if (!isEnabled()) {
            throw new FileStorageException("File storage is not configured");
        }

        return HttpRequest.newBuilder(URI.create(baseUrl + "/storage/v1/object/" + bucket + "/" + encode(path)))
                .header("Authorization", "Bearer " + serviceKey)
                .header("apikey", serviceKey);
    }

    private byte[] send(HttpRequest request, String action) {
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() / 100 != 2) {
                // The body can echo request details; keep only the status
                throw new FileStorageException("Could not " + action + " the file (status " + response.statusCode() + ")");
            }

            return response.body();
        } catch (IOException e) {
            throw new FileStorageException("Could not " + action + " the file", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FileStorageException("Interrupted while trying to " + action + " the file", e);
        }
    }

    /** Each path segment encoded on its own, keeping the slashes. */
    private static String encode(String path) {
        return Arrays.stream(path.split("/"))
                .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(Collectors.joining("/"));
    }
}
