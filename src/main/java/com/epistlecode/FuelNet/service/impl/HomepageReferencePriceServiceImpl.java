package com.epistlecode.FuelNet.service.impl;

import com.epistlecode.FuelNet.response.HomepageReferencePriceResponse;
import com.epistlecode.FuelNet.service.HomepageReferencePriceService;
import com.epistlecode.FuelNet.util.PriceTextParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Scrapes the latest official PMS/Diesel/Jet A-1 announcements from NNPC and
 * Dangote Refinery so the public homepage can show a national reference price
 * next to the station prices FuelNet itself tracks. Results are cached in
 * memory for 30 minutes. All text parsing is delegated to {@link PriceTextParser}.
 */
@Service
public class HomepageReferencePriceServiceImpl implements HomepageReferencePriceService {

    private static final Logger log = LoggerFactory.getLogger(HomepageReferencePriceServiceImpl.class);

    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private static final DateTimeFormatter DISPLAY_DATE =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH).withZone(ZoneId.systemDefault());
    private static final String DEFAULT_SOURCE_LABEL = "Source: latest official announcement";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

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
        return scrapeSinglePriceSource("PMS", "NNPC",
                "https://nnpcgroup.com/insights",
                "https://nnpcgroup.com/insights/nnpc-ltd-releases-estimated-pump-prices-of-pms-from-dangote-refinery-based-on-september-2024-pricing",
                true);
    }

    private Optional<HomepageReferencePriceResponse> scrapeDangotePms() {
        return scrapeSinglePriceSource("PMS", "Dangote Refinery",
                "https://refinery.dangote.com/category/press-release/",
                "https://refinery.dangote.com/2025/02/26/official-statement-on-the-reduction-in-ex-depot-price-of-pms-by-n65/",
                false);
    }

    private List<HomepageReferencePriceResponse> scrapeDangoteDieselAndJetA1() {
        String articleUrl = "https://refinery.dangote.com/2024/04/23/again-dangote-crashes-diesel-and-aviation-fuel-prices-further-to-n940-n980-respectively/";
        try {
            Document article = fetchDocument(articleUrl);
            double[] prices = PriceTextParser.extractDieselAndJetA1(
                    PriceTextParser.normalizeWhitespace(article.body().text()));
            if (prices == null) {
                return List.of();
            }
            String lastUpdated = extractDate(article);
            return List.of(
                    buildResponse("Diesel", "Dangote Refinery", prices[0], lastUpdated, articleUrl),
                    buildResponse("Jet A-1 (Aviation Kerosene)", "Dangote Refinery", prices[1], lastUpdated, articleUrl));
        } catch (Exception e) {
            log.warn("Dangote diesel/Jet A-1 scrape failed: {}", e.getMessage());
            return List.of();
        }
    }

    private Optional<HomepageReferencePriceResponse> scrapeSinglePriceSource(
            String fuelName, String label, String listingUrl, String fallbackArticleUrl, boolean preferFirstPrice) {
        try {
            Document listing = fetchDocument(listingUrl);
            String articleUrl = resolveArticleUrl(listing, listingUrl, fallbackArticleUrl);
            Document article = fetchDocument(articleUrl);
            Double price = PriceTextParser.extractPrice(
                    PriceTextParser.normalizeWhitespace(article.body().text()), preferFirstPrice);
            if (price == null) {
                return Optional.empty();
            }
            return Optional.of(buildResponse(fuelName, label, price, extractDate(article), articleUrl));
        } catch (Exception e) {
            log.warn("{} {} scrape failed: {}", label, fuelName, e.getMessage());
            return Optional.empty();
        }
    }

    private HomepageReferencePriceResponse buildResponse(String fuelName, String stationName, double price,
                                                         String lastUpdated, String sourceUrl) {
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
        return Jsoup.connect(url).userAgent(USER_AGENT).timeout(20000).get();
    }

    private String resolveArticleUrl(Document listing, String baseUrl, String fallbackArticleUrl) {
        return listing.select("a[href]").stream()
                .map(link -> toAbsoluteUrl(baseUrl, link))
                .flatMap(Optional::stream)
                .filter(PriceTextParser::isPmsPriceArticle)
                .findFirst()
                .orElse(fallbackArticleUrl);
    }

    private Optional<String> toAbsoluteUrl(String baseUrl, Element link) {
        String href = link.attr("href").trim();
        if (href.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new URL(new URL(baseUrl), href).toString());
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private String extractDate(Document article) {
        String datetime = article.select("time[datetime]").stream()
                .map(t -> t.attr("datetime"))
                .filter(v -> !v.isBlank())
                .findFirst()
                .orElse("");
        if (!datetime.isBlank()) {
            try {
                return DISPLAY_DATE.format(Instant.parse(datetime));
            } catch (Exception ignored) {
            }
        }

        Optional<String> timeText = article.select("time").stream()
                .map(Element::text)
                .map(PriceTextParser::normalizeWhitespace)
                .filter(v -> !v.isBlank())
                .findFirst();
        if (timeText.isPresent()) {
            return timeText.get();
        }

        String textual = PriceTextParser.extractTextualDate(PriceTextParser.normalizeWhitespace(article.text()));
        if (textual != null) {
            return textual;
        }

        String fromUrl = PriceTextParser.extractUrlDate(article.location());
        return fromUrl != null ? fromUrl : "Latest official announcement";
    }
}
