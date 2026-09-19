package com.epistlecode.FuelNet.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure text-extraction helpers used by the homepage reference-price scraper.
 * Kept free of I/O so the regexes can be unit-tested against sample press-release text.
 */
public final class PriceTextParser {

    private PriceTextParser() {}

    static final Pattern FROM_TO_PATTERN = Pattern.compile(
            "from\\s+N(?:GN)?\\s*([\\d,.]+)\\s+to\\s+N(?:GN)?\\s*([\\d,.]+)\\s+per litre",
            Pattern.CASE_INSENSITIVE);
    static final Pattern DIRECT_PATTERN = Pattern.compile(
            "(?:price of|priced at|sell for|sold at|at)\\s+N(?:GN)?\\s*([\\d,.]+)\\s+per litre",
            Pattern.CASE_INSENSITIVE);
    static final Pattern PER_LITRE_PATTERN = Pattern.compile(
            "N(?:GN)?\\s*([\\d,.]+)\\s+per litre",
            Pattern.CASE_INSENSITIVE);
    static final Pattern DIESEL_JETA1_PATTERN = Pattern.compile(
            "diesel\\s+and\\s+aviation\\s+fuel\\s+to\\s+N\\s*([\\d,.]+)\\s*,\\s*N\\s*([\\d,.]+)\\s+per litre\\s+respectively",
            Pattern.CASE_INSENSITIVE);
    static final Pattern TEXTUAL_DATE_PATTERN = Pattern.compile(
            "\\b(JANUARY|FEBRUARY|MARCH|APRIL|MAY|JUNE|JULY|AUGUST|SEPTEMBER|OCTOBER|NOVEMBER|DECEMBER)\\s+\\d{1,2},\\s+\\d{4}\\b",
            Pattern.CASE_INSENSITIVE);
    static final Pattern URL_DATE_PATTERN = Pattern.compile("/(20\\d{2})/(\\d{2})/(\\d{2})/");

    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    /**
     * Extracts the announced per-litre price from press-release text.
     * Preference order: "from N.. to N.." → "priced at N.." → any "N.. per litre".
     * When several bare "per litre" figures exist, {@code preferFirstPrice} picks the first,
     * otherwise the highest.
     */
    public static Double extractPrice(String text, boolean preferFirstPrice) {
        if (text == null) return null;

        Matcher fromTo = FROM_TO_PATTERN.matcher(text);
        if (fromTo.find()) {
            return parseAmount(fromTo.group(2));
        }
        Matcher direct = DIRECT_PATTERN.matcher(text);
        if (direct.find()) {
            return parseAmount(direct.group(1));
        }
        Matcher any = PER_LITRE_PATTERN.matcher(text);
        List<Double> matches = new ArrayList<>();
        while (any.find()) {
            Double amount = parseAmount(any.group(1));
            if (amount != null) matches.add(amount);
        }
        if (matches.isEmpty()) return null;
        return preferFirstPrice
                ? matches.get(0)
                : matches.stream().max(Comparator.naturalOrder()).orElse(matches.get(matches.size() - 1));
    }

    /** Returns [diesel, jetA1] from the Dangote combined announcement, or null if absent. */
    public static double[] extractDieselAndJetA1(String text) {
        if (text == null) return null;
        Matcher m = DIESEL_JETA1_PATTERN.matcher(text);
        if (!m.find()) return null;
        Double diesel = parseAmount(m.group(1));
        Double jet = parseAmount(m.group(2));
        if (diesel == null || jet == null) return null;
        return new double[]{diesel, jet};
    }

    /** "1,050.50" → 1050.5; null if unparseable. */
    public static Double parseAmount(String value) {
        if (value == null) return null;
        try {
            return Double.parseDouble(value.replace(",", ""));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** Finds a "September 3, 2024"-style date in the text and renders it as "03 Sep 2024". */
    public static String extractTextualDate(String text) {
        if (text == null) return null;
        Matcher m = TEXTUAL_DATE_PATTERN.matcher(text);
        return m.find() ? toDisplayDate(m.group()) : null;
    }

    /** Finds a /2025/02/26/ segment in a URL and renders it as "26 Feb 2025". */
    public static String extractUrlDate(String url) {
        if (url == null) return null;
        Matcher m = URL_DATE_PATTERN.matcher(url);
        if (!m.find()) return null;
        try {
            return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3))).format(DISPLAY);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static final DateTimeFormatter LONG_DATE = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("MMMM d, yyyy")
            .toFormatter(Locale.ENGLISH);

    public static String toDisplayDate(String value) {
        try {
            return LocalDate.parse(value.trim(), LONG_DATE).format(DISPLAY);
        } catch (DateTimeParseException ignored) {
            return value;
        }
    }

    public static String normalizeWhitespace(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    /** True when a URL looks like a PMS pump-price announcement. */
    public static boolean isPmsPriceArticle(String url) {
        if (url == null) return false;
        String n = url.toLowerCase(Locale.ENGLISH);
        return n.contains("pms") && (n.contains("price") || n.contains("pump") || n.contains("reduction"));
    }
}
