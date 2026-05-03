package com.klinekit.data;

import com.klinekit.domain.Candle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CsvCandleProvider implements CandleProvider {

    private static final List<DateTimeFormatter> DATETIME_FORMATS = List.of(
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy")
    );

    private final Path csvPath;
    private final String defaultSymbol;

    public CsvCandleProvider(Path csvPath, String defaultSymbol) {
        this.csvPath = csvPath;
        this.defaultSymbol = defaultSymbol;
    }

    @Override
    public List<Candle> load() {
        try (BufferedReader r = Files.newBufferedReader(csvPath)) {
            String headerLine = readHeaderLine(r);
            if (headerLine == null) {
                throw new IllegalArgumentException("empty CSV: " + csvPath);
            }
            Map<String, Integer> idx = headerIndex(headerLine);
            List<Candle> out = new ArrayList<>();
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] cols = splitCsv(line);
                Candle c = parseRow(cols, idx);
                if (c != null) out.add(c);
            }
            out.sort(Comparator.comparing(Candle::timestamp));
            return List.copyOf(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String readHeaderLine(BufferedReader r) throws IOException {
        String line;
        while ((line = r.readLine()) != null) {
            if (line.isBlank()) continue;
            if (line.startsWith("https://") || line.startsWith("http://")) continue;
            if (line.toLowerCase().startsWith("# ")) continue;
            return line;
        }
        return null;
    }

    private Map<String, Integer> headerIndex(String header) {
        String[] cols = splitCsv(header);
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < cols.length; i++) {
            map.put(cols[i].trim().toLowerCase().replace(" ", "_"), i);
        }
        return map;
    }

    private Candle parseRow(String[] cols, Map<String, Integer> idx) {
        try {
            Instant ts = parseTimestamp(cols, idx);
            BigDecimal open = bd(cols, idx, "open");
            BigDecimal high = bd(cols, idx, "high");
            BigDecimal low = bd(cols, idx, "low");
            BigDecimal close = bd(cols, idx, "close");
            BigDecimal volume = bdOptional(cols, idx, "volume", "volume_btc", "volume_usd", "volume_usdt");
            String symbol = stringOptional(cols, idx, "symbol", "pair");
            if (symbol == null) symbol = defaultSymbol;
            else symbol = symbol.replace("/", "").replace("-", "").toUpperCase();
            return new Candle(symbol, ts, open, high, low, close, volume == null ? BigDecimal.ZERO : volume);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Instant parseTimestamp(String[] cols, Map<String, Integer> idx) {
        for (String key : List.of("timestamp", "unix", "time", "date", "datetime", "open_time")) {
            Integer i = idx.get(key);
            if (i == null || i >= cols.length) continue;
            String raw = cols[i].trim();
            if (raw.isEmpty()) continue;
            Instant t = tryEpoch(raw);
            if (t != null) return t;
            t = tryDateTime(raw);
            if (t != null) return t;
        }
        throw new IllegalArgumentException("no parseable timestamp in row");
    }

    private Instant tryEpoch(String raw) {
        try {
            long n = Long.parseLong(raw);
            if (n > 4_000_000_000L) return Instant.ofEpochMilli(n);
            return Instant.ofEpochSecond(n);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Instant tryDateTime(String raw) {
        for (DateTimeFormatter f : DATETIME_FORMATS) {
            try {
                if (f == DateTimeFormatter.ISO_OFFSET_DATE_TIME) {
                    return OffsetDateTime.parse(raw, f).toInstant();
                }
                if (f == DateTimeFormatter.ISO_LOCAL_DATE_TIME
                        || f.toString().contains("HH")) {
                    return LocalDateTime.parse(raw, f).toInstant(ZoneOffset.UTC);
                }
                return LocalDate.parse(raw, f).atStartOfDay(ZoneOffset.UTC).toInstant();
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        return null;
    }

    private static BigDecimal bd(String[] cols, Map<String, Integer> idx, String key) {
        Integer i = idx.get(key);
        if (i == null || i >= cols.length) {
            throw new IllegalArgumentException("missing column: " + key);
        }
        return new BigDecimal(cols[i].trim());
    }

    private static BigDecimal bdOptional(String[] cols, Map<String, Integer> idx, String... keys) {
        for (String k : keys) {
            Integer i = idx.get(k);
            if (i != null && i < cols.length && !cols[i].isBlank()) {
                try {
                    return new BigDecimal(cols[i].trim());
                } catch (NumberFormatException ignored) {
                    // try next
                }
            }
        }
        return null;
    }

    private static String stringOptional(String[] cols, Map<String, Integer> idx, String... keys) {
        for (String k : keys) {
            Integer i = idx.get(k);
            if (i != null && i < cols.length && !cols[i].isBlank()) {
                return cols[i].trim();
            }
        }
        return null;
    }

    private static String[] splitCsv(String line) {
        return line.split(",", -1);
    }
}
