final class InfraiException extends RuntimeException {
    private final String code;
    private final int statusCode;

    InfraiException(String code, String message, int statusCode) {
        super(message);
        this.code = code;
        this.statusCode = statusCode;
    }

    String code() { return code; }
    int statusCode() { return statusCode; }
}
