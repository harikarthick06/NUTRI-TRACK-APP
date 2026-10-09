package com.nutritrack.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CsvWriterTest {
    @Test
    void quotesEscapesAndLeavesNullsBlank() {
        String csv = new CsvWriter().row("a,b", "say \"hi\"", null, new BigDecimal("12.500"), 3).toString();
        assertThat(csv).isEqualTo("\"a,b\",\"say \"\"hi\"\"\",,12.5,3\r\n");
    }

    @Test
    void neutralisesSpreadsheetFormulas() {
        assertThat(CsvWriter.cell("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
        assertThat(CsvWriter.cell("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(CsvWriter.cell("+91 phone")).isEqualTo("'+91 phone");
        assertThat(CsvWriter.cell(new BigDecimal("-1.6"))).isEqualTo("-1.6");
    }

    @Test
    void writesUtf8WithBom() {
        byte[] bytes = new CsvWriter().row("Vanjaram (வஞ்சிரம்)").toBytes();
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8)).contains("வஞ்சிரம்");
    }
}
