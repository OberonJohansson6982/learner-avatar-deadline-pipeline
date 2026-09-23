import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AvatarPipelineServer {
    public static void main(String[] args) throws IOException {
        AvatarConfig config = AvatarConfig.fromEnvironment();
        LearnerAvatarService service = new LearnerAvatarService(new InfraiAvatarClient(config), Clock.systemUTC());
        HttpServer server = HttpServer.create(new InetSocketAddress(config.port()), 0);
        server.createContext("/avatars", exchange -> handle(exchange, service));
        server.start();
        System.out.println("Avatar pipeline listening on http://localhost:" + config.port() + "/avatars");
    }

    private static void handle(HttpExchange exchange, LearnerAvatarService service) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, Map.of("error", "POST required"));
            return;
        }
        try {
            String userId = requiredHeader(exchange, "X-User-Id");
            String courseId = requiredHeader(exchange, "X-Course-Id");
            String filename = requiredHeader(exchange, "X-Filename");
            Instant deadline = Instant.parse(requiredHeader(exchange, "X-Deadline"));
            LearnerAvatarService.AvatarCommand command = new LearnerAvatarService.AvatarCommand(
                    userId, courseId, deadline, exchange.getRequestBody().readAllBytes(), filename);
            LearnerAvatarService.AvatarResult result = service.process(command, LearnerAvatarService.CourseDelivery.ACTIVE);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("userId", result.userId());
            body.put("courseId", result.courseId());
            body.put("imageId", result.imageId());
            body.put("deadlineState", result.deadlineState().name());
            body.put("educatorReportIncremented", result.educatorReportIncremented());
            respond(exchange, 200, body);
        } catch (InfraiException rejected) {
            int status = rejected.statusCode() >= 400 && rejected.statusCode() < 500 ? rejected.statusCode() : 502;
            respond(exchange, status, Map.of("error", rejected.code(), "message", rejected.getMessage()));
        } catch (IllegalArgumentException rejected) {
            respond(exchange, 400, Map.of("error", "INVALID_REQUEST", "message", rejected.getMessage()));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            respond(exchange, 503, Map.of("error", "INTERRUPTED"));
        } catch (IOException transport) {
            respond(exchange, 502, Map.of("error", "UPSTREAM_TRANSPORT"));
        }
    }

    private static String requiredHeader(HttpExchange exchange, String name) {
        String value = exchange.getRequestHeaders().getFirst(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    private static void respond(HttpExchange exchange, int status, Map<String, Object> body) throws IOException {
        byte[] bytes = JsonCodec.encode(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
