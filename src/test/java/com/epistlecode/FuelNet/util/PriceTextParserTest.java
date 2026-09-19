package com.epistlecode.FuelNet.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PriceTextParserTest {

    @Nested
    @DisplayName("extractPrice")
    class ExtractPrice {

        @Test
        void prefersFromToPatternAndReturnsNewPrice() {
            String text = "NNPC has reduced the pump price of PMS from N1,030 to N998 per litre effective today.";
            assertThat(PriceTextParser.extractPrice(text, true)).isEqualTo(998.0);
        }

        @Test
        void fallsBackToDirectPattern() {
            String text = "Dangote Refinery announced PMS will be sold at N890 per litre to marketers.";
            assertThat(PriceTextParser.extractPrice(text, false)).isEqualTo(890.0);
        }

        @Test
        void handlesNgnPrefixAndDecimals() {
            String text = "The product is now priced at NGN 1,050.50 per litre.";
            assertThat(PriceTextParser.extractPrice(text, false)).isEqualTo(1050.5);
        }

        @Test
        void bareMatchesPickFirstWhenPreferFirstIsTrue() {
            String text = "Lagos: N865 per litre. Abuja: N880 per litre. Kano: N900 per litre.";
            assertThat(PriceTextParser.extractPrice(text, true)).isEqualTo(865.0);
        }

        @Test
        void bareMatchesPickHighestWhenPreferFirstIsFalse() {
            String text = "Lagos: N865 per litre. Abuja: N880 per litre. Kano: N900 per litre.";
            assertThat(PriceTextParser.extractPrice(text, false)).isEqualTo(900.0);
        }

        @Test
        void returnsNullWhenNoPriceMentioned() {
            assertThat(PriceTextParser.extractPrice("No pricing information in this release.", true)).isNull();
            assertThat(PriceTextParser.extractPrice(null, true)).isNull();
        }
    }

    @Nested
    @DisplayName("extractDieselAndJetA1")
    class DieselJet {

        @Test
        void parsesCombinedDangoteAnnouncement() {
            String text = "Dangote crashes diesel and aviation fuel to N940, N980 per litre respectively.";
            double[] prices = PriceTextParser.extractDieselAndJetA1(text);
            assertThat(prices).containsExactly(940.0, 980.0);
        }

        @Test
        void returnsNullWhenPatternAbsent() {
            assertThat(PriceTextParser.extractDieselAndJetA1("Diesel is N940 per litre.")).isNull();
        }
    }

    @Nested
    @DisplayName("dates")
    class Dates {

        @Test
        void extractsTextualDate() {
            String text = "Press release issued September 3, 2024 in Lagos.";
            assertThat(PriceTextParser.extractTextualDate(text)).isEqualTo("03 Sep 2024");
        }

        @Test
        void extractsDateFromUrl() {
            String url = "https://refinery.dangote.com/2025/02/26/official-statement/";
            assertThat(PriceTextParser.extractUrlDate(url)).isEqualTo("26 Feb 2025");
        }

        @Test
        void returnsNullForUrlWithoutDate() {
            assertThat(PriceTextParser.extractUrlDate("https://nnpcgroup.com/insights")).isNull();
        }

        @Test
        void toDisplayDateReturnsInputWhenUnparseable() {
            assertThat(PriceTextParser.toDisplayDate("last week")).isEqualTo("last week");
        }
    }

    @ParameterizedTest
    @CsvSource({
            "'1,050.50', 1050.5",
            "'865', 865",
            "'998.00', 998",
    })
    void parseAmountStripsThousandsSeparators(String raw, double expected) {
        assertThat(PriceTextParser.parseAmount(raw)).isEqualTo(expected);
    }

    @Test
    void parseAmountReturnsNullOnGarbage() {
        assertThat(PriceTextParser.parseAmount("N/A")).isNull();
    }

    @Test
    void isPmsPriceArticleRequiresPmsAndAPriceKeyword() {
        assertThat(PriceTextParser.isPmsPriceArticle("https://x.com/nnpc-releases-pump-prices-of-pms")).isTrue();
        assertThat(PriceTextParser.isPmsPriceArticle("https://x.com/reduction-in-ex-depot-price-of-pms")).isTrue();
        assertThat(PriceTextParser.isPmsPriceArticle("https://x.com/pms-quality-assurance")).isFalse();
        assertThat(PriceTextParser.isPmsPriceArticle("https://x.com/diesel-price-update")).isFalse();
    }

    @Test
    void normalizeWhitespaceCollapsesRuns() {
        assertThat(PriceTextParser.normalizeWhitespace("  a \n\t b   c ")).isEqualTo("a b c");
        assertThat(PriceTextParser.normalizeWhitespace(null)).isEmpty();
    }
}
