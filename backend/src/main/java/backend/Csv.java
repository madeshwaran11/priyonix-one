package backend;

/**
 * Minimal RFC-4180-style CSV helper for the export endpoints.
 *
 * Cells containing a comma, quote, CR or LF are wrapped in double quotes with
 * embedded quotes doubled. This produces files that open cleanly in Excel and
 * other spreadsheet tools without any third-party dependency.
 */
public final class Csv {

    private Csv() {
    }

    public static String row(Object... cells) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(escape(cells[i]));
        }
        return builder.append('\n').toString();
    }

    public static String escape(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if (!(value instanceof Number) && text.stripLeading().matches("^[=+@\\-].*")) {
            text = "'" + text;
        }
        boolean needsQuotes = text.indexOf(',') >= 0
                || text.indexOf('"') >= 0
                || text.indexOf('\n') >= 0
                || text.indexOf('\r') >= 0;
        if (!needsQuotes) {
            return text;
        }
        return '"' + text.replace("\"", "\"\"") + '"';
    }
}
