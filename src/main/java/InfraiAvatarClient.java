import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

final class InfraiAvatarClient {
    private final AvatarConfig config;
    private final HttpClient http;

    InfraiAvatarClient(AvatarConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(config.requestTimeout()).build());
    }

    InfraiAvatarClient(AvatarConfig config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    String upload(byte[] file, String filename, String operationId) throws IOException, InterruptedException {
        String boundary = "avatar-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        part(body, boundary, "filename", filename.getBytes(StandardCharsets.UTF_8), null);
        part(body, boundary, "file", file, filename);
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return dataId(send("POST", "/v1/image/upload", body.toByteArray(),
                "multipart/form-data; boundary=" + boundary, operationId));
    }

    String smartCrop(String imageId, String operationId) throws IOException, InterruptedException {
        return dataId(json("POST", "/v1/image/smart_crop",
                Map.of("image", imageId, "aspect", "1:1"), operationId));
    }

    String optimize(String imageId, String operationId) throws IOException, InterruptedException {
        return dataId(json("POST", "/v1/image/resize", Map.of(
                "image", imageId, "width", 512, "height", 512, "fit", "cover",
                "enlarge", false, "format", "webp", "store", true), operationId));
    }

    void attachToUser(String userId, String imageId, String operationId) throws IOException, InterruptedException {
        json("PATCH", "/v1/auth/user/update/" + pathSegment(userId),
                Map.of("user_id", userId, "metadata", Map.of("avatar_image_id", imageId)), operationId);
    }

    private Map<String, Object> json(String method, String path, Map<String, Object> body, String operationId)
            throws IOException, InterruptedException {
        return send(method, path, JsonCodec.encode(body).getBytes(StandardCharsets.UTF_8),
                "application/json", operationId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> send(String method, String path, byte[] body, String contentType, String operationId)
            throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(config.baseUrl().resolve(path))
                    .timeout(config.requestTimeout())
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", contentType)
                    .header("Idempotency-Key", operationId)
                    .method(method, HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

            Object decoded;
            try { decoded = JsonCodec.decode(response.body()); }
            catch (RuntimeException invalidJson) {
                if (response.statusCode() >= 500) throw new IOException("Infrai transport response was not JSON", invalidJson);
                throw new IOException("Infrai response was not an envelope", invalidJson);
            }
            if (!(decoded instanceof Map<?, ?> raw)) throw new IOException("Infrai response was not an envelope");
            Map<String, Object> envelope = (Map<String, Object>) raw;

            if (response.statusCode() == 429 && attempt < 3) {
                Thread.sleep(retryDelay(response, attempt));
                continue;
            }
            if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                Map<String, Object> error = envelope.get("error") instanceof Map<?, ?> e
                        ? (Map<String, Object>) e : Map.of();
                throw new InfraiException(String.valueOf(error.getOrDefault("code", "REQUEST_REJECTED")),
                        String.valueOf(error.getOrDefault("message", "Request rejected")), response.statusCode());
            }
            if (response.statusCode() >= 500) throw new IOException("Infrai transport failure");
            return envelope;
        }
        throw new IOException("Retry budget exhausted");
    }

    @SuppressWarnings("unchecked")
    private static String dataId(Map<String, Object> envelope) {
        if (!(envelope.get("data") instanceof Map<?, ?> raw)) throw new IOExceptionUnchecked("Missing response data");
        Object id = ((Map<String, Object>) raw).get("id");
        if (!(id instanceof String value) || value.isBlank()) throw new IOExceptionUnchecked("Missing image id");
        return value;
    }

    private static long retryDelay(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .map(InfraiAvatarClient::retryAfterMillis)
                .orElse((long) (250 * Math.pow(2, attempt)));
    }

    private static long retryAfterMillis(String value) {
        try { return Math.max(0, Long.parseLong(value) * 1000); }
        catch (NumberFormatException ignored) { return 1000; }
    }

    private static String pathSegment(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static void part(ByteArrayOutputStream out, String boundary, String name, byte[] value, String filename)
            throws IOException {
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\""
                + (filename == null ? "" : "; filename=\"" + filename.replace("\"", "") + "\"")
                + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(value);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static final class IOExceptionUnchecked extends RuntimeException {
        IOExceptionUnchecked(String message) { super(message); }
    }
}
