package com.epistlecode.FuelNet.service.impl;

import com.epistlecode.FuelNet.response.HomepageReferencePriceResponse;
import com.epistlecode.FuelNet.service.interfac.HomepageReferencePriceService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class HomepageReferencePriceServiceImpl implements HomepageReferencePriceService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private static final DateTimeFormatter DISPLAY_DATE =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH).withZone(ZoneId.systemDefault());
    private static final String DEFAULT_SOURCE_LABEL = "Source: latest official announcement";

    private static final Pattern FROM_TO_PATTERN = Pattern.compile(
            "from\\s+N(?:GN)?\\s*([\\d,.]+)\\s+to\\s+N(?:GN)?\\s*([\\d,.]+)\\s+per litre",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DIRECT_PATTERN = Pattern.compile(
            "(?:price of|priced at|sell for|sold at|at)\\s+N(?:GN)?\\s*([\\d,.]+)\\s+per litre",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PER_LITRE_PATTERN = Pattern.compile(
            "N(?:GN)?\\s*([\\d,.]+)\\s+per litre",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DIESEL_JETA1_PATTERN = Pattern.compile(
            "diesel\\s+and\\s+aviation\\s+fuel\\s+to\\s+N\\s*([\\d,.]+)\\s*,\\s*N\\s*([\\d,.]+)\\s+per litre\\s+respectively",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TEXTUAL_DATE_PATTERN = Pattern.compile(
            "\\b(JANUARY|FEBRUARY|MARCH|APRIL|MAY|JUNE|JULY|AUGUST|SEPTEMBER|OCTOBER|NOVEMBER|DECEMBER)\\s+\\d{1,2},\\s+\\d{4}\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern URL_DATE_PATTERN = Pattern.compile("/(20\\d{2})/(\\d{2})/(\\d{2})/");

    private volatile List<HomepageReferencePriceResponse> cachedPrices = List.of();
    private volatile Instant cachedAt;

    @Override
    public synchronized List<HomepageReferencePriceResponse> getLatestReferencePrices() {
        if (cachedAt != null && Instant.now().isBefore(cachedAt.plus(CACHE_TTL)) && !cachedPrices.isEmpty()) {
            return cachedPrices;
        }

        List<HomepageReferencePriceResponse> prices = new ArrayList<>();
        scrapeNnpcPms().ifPresent(prices::add);
        scrapeDangotePms().ifPresent(prices::add);
        prices.addAll(scrapeDangoteDieselAndJetA1());

        cachedPrices = prices;
        cachedAt = Instant.now();

        return prices;
    }

    private Optional<HomepageReferencePriceResponse> scrapeNnpcPms() {
        String listingUrl = "https://nnpcgroup.com/insights";
        String fallbackUrl = "https://nnpcgroup.com/insights/nnpc-ltd-releases-estimated-pump-prices-of-pms-from-dangote-refinery-based-on-september-2024-pricing";
        return scrapeSinglePriceSource("PMS", "NNPC", listingUrl, fallbackUrl, true);
    }

    private Optional<HomepageReferencePriceResponse> scrapeDangotePms() {
        String listingUrl = "https://refinery.dangote.com/category/press-release/";
        String fallbackUrl = "https://refinery.dangote.com/2025/02/26/official-statement-on-the-reduction-in-ex-depot-price-of-pms-by-n65/";
        return scrapeSinglePriceSource("PMS", "Dangote Refinery", listingUrl, fallbackUrl, false);
    }

    private List<HomepageReferencePriceResponse> scrapeDangoteDieselAndJetA1() {
        String articleUrl = "https://refinery.dangote.com/2024/04/23/again-dangote-crashes-diesel-and-aviation-fuel-prices-further-to-n940-n980-respectively/";

        try {
            Document articleDocument = fetchDocument(articleUrl);
            String articleText = normalizeWhitespace(articleDocument.body().text());
            Matcher matcher = DIESEL_JETA1_PATTERN.matcher(articleText);

            if (!matcher.find()) {
                return List.of();
            }

            Double dieselPrice = parseAmount(matcher.group(1));
            Double jetA1Price = parseAmount(matcher.group(2));

            if (dieselPrice == null || jetA1Price == null) {
                return List.of();
            }

            String lastUpdated = extractDate(articleDocument);

            List<HomepageReferencePriceResponse> responses = new ArrayList<>();
            responses.add(buildResponse("Diesel", "Dangote Refinery", dieselPrice, lastUpdated, articleUrl));
            responses.add(buildResponse("Jet A-1 (Aviation Kerosene)", "Dangote Refinery", jetA1Price, lastUpdated, articleUrl));
            return responses;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private Optional<HomepageReferencePriceResponse> scrapeSinglePriceSource(
            String fuelName,
            String label,
            String listingUrl,
            String fallbackArticleUrl,
            boolean preferFirstPrice
    ) {
        try {
            Document listingDocument = fetchDocument(listingUrl);
            String articleUrl = resolveArticleUrl(listingDocument, listingUrl, fallbackArticleUrl);
            Document articleDocument = fetchDocument(articleUrl);
            String articleText = normalizeWhitespace(articleDocument.body().text());
            Double price = extractPrice(articleText, preferFirstPrice);

            if (price == null) {
                return Optional.empty();
            }

            return Optional.of(buildResponse(fuelName, label, price, extractDate(articleDocument), articleUrl));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private HomepageReferencePriceResponse buildResponse(
            String fuelName,
            String stationName,
            double price,
            String lastUpdated,
            String sourceUrl
    ) {
        return HomepageReferencePriceResponse.builder()
                .fuelName(fuelName)
                .price(price)
                .stationName(stationName)
                .stationAddress(DEFAULT_SOURCE_LABEL)
                .lastUpdated(lastUpdated)
                .sourceUrl(sourceUrl)
                .build();
    }

    private Document fetchDocument(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                .timeout(20000)
                .get();
    }

    private String resolveArticleUrl(Document listingDocument, String baseUrl, String fallbackArticleUrl) {
        return listingDocument.select("a[href]").stream()
                .map(link -> toAbsoluteUrl(baseUrl, link))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(this::isPmsPriceArticle)
                .findFirst()
                .orElse(fallbackArticleUrl);
    }

    private Optional<String> toAbsoluteUrl(String baseUrl, Element link) {
        String href = link.attr("href").trim();
        if (href.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(new java.net.URL(new java.net.URL(baseUrl), href).toString());
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private boolean isPmsPriceArticle(String url) {
        String normalized = url.toLowerCase(Locale.ENGLISH);
        return normalized.contains("pms")
                && (normalized.contains("price") || normalized.contains("pump") || normalized.contains("reduction"));
    }

    private String extractDate(Document articleDocument) {
        String datetime = articleDocument.select("time[datetime]").stream()
                .map(time -> time.attr("datetime"))
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse("");

        if (!datetime.isBlank()) {
            try {
                return DISPLAY_DATE.format(Instant.parse(datetime));
            } catch (Exception ignored) {
            }
        }

        Optional<String> timeText = articleDocument.select("time").stream()
                .map(Element::text)
                .map(this::normalizeWhitespace)
                .filter(value -> !value.isBlank())
                .findFirst();

        if (timeText.isPresent()) {
            return timeText.get();
        }

        String fullText = normalizeWhitespace(articleDocument.text());
        Matcher textualDateMatcher = TEXTUAL_DATE_PATTERN.matcher(fullText);
        if (textualDateMatcher.find()) {
            return toDisplayDate(textualDateMatcher.group());
        }

        String canonicalUrl = articleDocument.location();
        Matcher urlDateMatcher = URL_DATE_PATTERN.matcher(canonicalUrl);
        if (urlDateMatcher.find()) {
            try {
                LocalDate date = LocalDate.of(
                        Integer.parseInt(urlDateMatcher.group(1)),
                        Integer.parseInt(urlDateMatcher.group(2)),
                        Integer.parseInt(urlDateMatcher.group(3))
                );
                return date.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH));
            } catch (Exception ignored) {
            }
        }

        return "Latest official announcement";
    }

    private String toDisplayDate(String value) {
        try {
            LocalDate date = LocalDate.parse(
                    value.toUpperCase(Locale.ENGLISH),
                    DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)
            );
            return date.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH));
        } catch (DateTimeParseException ignored) {
            return value;
        }
    }

    private Double extractPrice(String text, boolean preferFirstPrice) {
        Matcher fromToMatch = FROM_TO_PATTERN.matcher(text);
        if (fromToMatch.find()) {
            return parseAmount(fromToMatch.group(2));
        }

        Matcher directMatch = DIRECT_PATTERN.matcher(text);
        if (directMatch.find()) {
            return parseAmount(directMatch.group(1));
        }

        Matcher matcher = PER_LITRE_PATTERN.matcher(text);
        List<Double> matches = new ArrayList<>();
        while (matcher.find()) {
            Double amount = parseAmount(matcher.group(1));
            if (amount != null) {
                matches.add(amount);
            }
        }

        if (matches.isEmpty()) {
            return null;
        }

        return preferFirstPrice
                ? matches.get(0)
                : matches.stream().max(Comparator.naturalOrder()).orElse(matches.get(matches.size() - 1));
    }

    private Double parseAmount(String value) {
        try {
            return Double.parseDouble(value.replace(",", ""));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizeWhitespace(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
