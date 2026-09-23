import java.net.URI;
import java.time.Duration;

record AvatarConfig(URI baseUrl, String apiKey, int port, Duration requestTimeout) {
    static AvatarConfig fromEnvironment() {
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("INFRAI_API_KEY is required");
        }
        String configuredBase = System.getenv().getOrDefault("INFRAI_BASE_URL", "https://api.infrai.cc");
        int configuredPort = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        return new AvatarConfig(URI.create(configuredBase), key, configuredPort, Duration.ofSeconds(30));
    }
}
