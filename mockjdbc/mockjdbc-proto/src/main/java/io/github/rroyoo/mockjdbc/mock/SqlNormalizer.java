package io.github.rroyoo.mockjdbc.mock;

public final class SqlNormalizer {

    private SqlNormalizer() {
    }

    public static String normalize(String sql) {
        if (sql == null) {
            return "";
        }

        var normalized = new StringBuilder(sql.length());
        var quote = '\0';
        var pendingWhitespace = false;

        for (var index = 0; index < sql.length(); index++) {
            var current = sql.charAt(index);

            if (quote != '\0') {
                normalized.append(current);
                if (current == '\\' && quote != ']') {
                    if (index + 1 < sql.length()) {
                        normalized.append(sql.charAt(++index));
                    }
                } else if (isClosingQuote(current, quote)) {
                    if (index + 1 < sql.length() && sql.charAt(index + 1) == current) {
                        normalized.append(sql.charAt(++index));
                    } else {
                        quote = '\0';
                    }
                }
                continue;
            }

            if (Character.isWhitespace(current)) {
                pendingWhitespace = normalized.length() > 0;
                continue;
            }

            if (pendingWhitespace) {
                normalized.append(' ');
                pendingWhitespace = false;
            }

            if (isOpeningQuote(current)) {
                quote = current == '[' ? ']' : current;
                normalized.append(current);
            } else {
                normalized.append(Character.toLowerCase(current));
            }
        }

        var result = normalized.toString().strip();
        while (result.endsWith(";")) {
            result = result.substring(0, result.length() - 1).stripTrailing();
        }
        return result;
    }

    private static boolean isOpeningQuote(char value) {
        return value == '\'' || value == '"' || value == '`' || value == '[';
    }

    private static boolean isClosingQuote(char value, char quote) {
        return value == quote;
    }
}
