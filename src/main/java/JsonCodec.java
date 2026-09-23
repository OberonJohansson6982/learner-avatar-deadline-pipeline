import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class JsonCodec {
    private JsonCodec() {}

    static String encode(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) return "\"" + escape(text) + "\"";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            StringBuilder out = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) out.append(',');
                first = false;
                out.append(encode(entry.getKey().toString())).append(':').append(encode(entry.getValue()));
            }
            return out.append('}').toString();
        }
        if (value instanceof Iterable<?> values) {
            StringBuilder out = new StringBuilder("[");
            boolean first = true;
            for (Object item : values) {
                if (!first) out.append(',');
                first = false;
                out.append(encode(item));
            }
            return out.append(']').toString();
        }
        throw new IllegalArgumentException("Cannot encode JSON value: " + value.getClass());
    }

    static Object decode(String source) {
        Parser parser = new Parser(source);
        Object value = parser.value();
        parser.space();
        if (!parser.end()) throw new IllegalArgumentException("Trailing JSON data");
        return value;
    }

    private static String escape(String text) {
        StringBuilder out = new StringBuilder();
        for (char c : text.toCharArray()) {
            switch (c) {
                case '\"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 32) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }

    private static final class Parser {
        private final String source;
        private int at;

        Parser(String source) { this.source = source; }
        boolean end() { return at == source.length(); }
        void space() { while (!end() && Character.isWhitespace(source.charAt(at))) at++; }

        Object value() {
            space();
            if (end()) throw new IllegalArgumentException("Empty JSON");
            return switch (source.charAt(at)) {
                case '{' -> object();
                case '[' -> array();
                case '\"' -> string();
                case 't' -> literal("true", true);
                case 'f' -> literal("false", false);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            at++;
            Map<String, Object> map = new LinkedHashMap<>();
            space();
            if (take('}')) return map;
            do {
                space();
                String key = string();
                space();
                require(':');
                map.put(key, value());
                space();
            } while (take(','));
            require('}');
            return map;
        }

        private List<Object> array() {
            at++;
            List<Object> list = new ArrayList<>();
            space();
            if (take(']')) return list;
            do { list.add(value()); space(); } while (take(','));
            require(']');
            return list;
        }

        private String string() {
            require('\"');
            StringBuilder out = new StringBuilder();
            while (!end()) {
                char c = source.charAt(at++);
                if (c == '\"') return out.toString();
                if (c != '\\') { out.append(c); continue; }
                char escaped = source.charAt(at++);
                switch (escaped) {
                    case '\"', '\\', '/' -> out.append(escaped);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        out.append((char) Integer.parseInt(source.substring(at, at + 4), 16));
                        at += 4;
                    }
                    default -> throw new IllegalArgumentException("Invalid JSON escape");
                }
            }
            throw new IllegalArgumentException("Unclosed JSON string");
        }

        private Object number() {
            int start = at;
            while (!end() && "-+0123456789.eE".indexOf(source.charAt(at)) >= 0) at++;
            String token = source.substring(start, at);
            return token.contains(".") || token.contains("e") || token.contains("E")
                    ? Double.parseDouble(token) : Long.parseLong(token);
        }

        private Object literal(String token, Object value) {
            if (!source.startsWith(token, at)) throw new IllegalArgumentException("Invalid JSON token");
            at += token.length();
            return value;
        }

        private boolean take(char expected) {
            if (!end() && source.charAt(at) == expected) { at++; return true; }
            return false;
        }

        private void require(char expected) {
            if (!take(expected)) throw new IllegalArgumentException("Expected " + expected);
        }
    }
}
