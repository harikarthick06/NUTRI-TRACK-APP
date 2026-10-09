package com.nutritrack.report;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Minimal RFC 4180 CSV builder. Cells that a spreadsheet would treat as a formula (starting with =, +, -, @, tab or
 * carriage return) are prefixed with an apostrophe, unless they are plain numbers.
 */
public final class CsvWriter {
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private final StringBuilder sb = new StringBuilder();

    public CsvWriter row(Object... cells) {
        return row(java.util.Arrays.asList(cells));
    }

    public CsvWriter row(List<?> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(cell(cells.get(i)));
        }
        sb.append("\r\n");
        return this;
    }

    static String cell(Object value) {
        if (value == null) {
            return "";
        }
        String s = value instanceof BigDecimal bd ? bd.stripTrailingZeros().toPlainString() : value.toString();
        if (!(value instanceof Number) && !s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            s = '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    @Override
    public String toString() {
        return sb.toString();
    }

    /** UTF-8 with a byte-order mark so that Excel shows non-ASCII food names correctly. */
    public byte[] toBytes() {
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[UTF8_BOM.length + body.length];
        System.arraycopy(UTF8_BOM, 0, out, 0, UTF8_BOM.length);
        System.arraycopy(body, 0, out, UTF8_BOM.length, body.length);
        return out;
    }
}
