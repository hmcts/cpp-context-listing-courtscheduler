package uk.gov.moj.cpp.courtscheduler.api.converter;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.fileToString;

import uk.gov.moj.cpp.courtscheduler.domain.OuCodeMigrateRequest;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("OuCodeMigrateConverter Tests")
class OuCodeMigrateConverterTest {

    @InjectMocks
    private OuCodeMigrateConverter ouCodeMigrateConverter;

    @Nested
    @DisplayName("Successful Conversion Tests")
    class SuccessfulConversionTests {

        @Test
        @DisplayName("Should convert valid JSON payload to OuCodeMigrateRequest")
        void shouldConvertOuCodeMigrateRequest() {
            final String payload = fileToString("/test-data/oucode-migrate-courtscheduler.json");

            final OuCodeMigrateRequest ouCodeMigrateRequest = ouCodeMigrateConverter.convert(payload);

            assertThat(ouCodeMigrateRequest.getOuCodes().size(), is(3));
            final List<String> ouCodes = ouCodeMigrateRequest.getOuCodes();
            assertThat(ouCodes.get(0), is("B12345"));
            assertThat(ouCodes.get(1), is("C12345"));
            assertThat(ouCodes.get(2), is("D12345"));
            assertThat(ouCodeMigrateRequest.isMigrated(), is(true));
        }

        @Test
        @DisplayName("Should convert payload with migrated set to false")
        void shouldConvertWithMigratedFalse() {
            final String payload = "{\"ouCodes\":[\"B12345\"],\"migrated\":false}";

            final OuCodeMigrateRequest ouCodeMigrateRequest = ouCodeMigrateConverter.convert(payload);

            assertThat(ouCodeMigrateRequest.getOuCodes().size(), is(1));
            assertThat(ouCodeMigrateRequest.getOuCodes().get(0), is("B12345"));
            assertThat(ouCodeMigrateRequest.isMigrated(), is(false));
        }

        @Test
        @DisplayName("Should convert payload with single OU code")
        void shouldConvertSingleOuCode() {
            final String payload = "{\"ouCodes\":[\"B12345\"],\"migrated\":true}";

            final OuCodeMigrateRequest ouCodeMigrateRequest = ouCodeMigrateConverter.convert(payload);

            assertThat(ouCodeMigrateRequest.getOuCodes().size(), is(1));
            assertThat(ouCodeMigrateRequest.getOuCodes().get(0), is("B12345"));
            assertThat(ouCodeMigrateRequest.isMigrated(), is(true));
        }

        @Test
        @DisplayName("Should convert payload with empty OU codes list")
        void shouldConvertEmptyOuCodesList() {
            final String payload = "{\"ouCodes\":[],\"migrated\":true}";

            final OuCodeMigrateRequest ouCodeMigrateRequest = ouCodeMigrateConverter.convert(payload);

            assertThat(ouCodeMigrateRequest.getOuCodes().size(), is(0));
            assertThat(ouCodeMigrateRequest.isMigrated(), is(true));
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should throw ConverterException when JSON is invalid")
        void shouldThrowConverterExceptionWhenJsonIsInvalid() {
            final String invalidPayload = "{invalid json}";

            final ConverterException exception = assertThrows(ConverterException.class,
                    () -> ouCodeMigrateConverter.convert(invalidPayload));

            assertThat(exception.getMessage(), is("Error while converting list item {invalid json} to OuCodeMigrateRequest"));
        }

        @Test
        @DisplayName("Should throw ConverterException when payload is empty string")
        void shouldThrowConverterExceptionWhenPayloadIsEmpty() {
            final String emptyPayload = "";

            final ConverterException exception = assertThrows(ConverterException.class,
                    () -> ouCodeMigrateConverter.convert(emptyPayload));

            assertThat(exception.getMessage(), is("Error while converting list item  to OuCodeMigrateRequest"));
        }

        @Test
        @DisplayName("Should throw ConverterException when payload is missing required fields")
        void shouldThrowConverterExceptionWhenPayloadMissingFields() {
            final String invalidPayload = "{\"missing\":\"fields\"}";

            final ConverterException exception = assertThrows(ConverterException.class,
                    () -> ouCodeMigrateConverter.convert(invalidPayload));

            assertThat(exception.getMessage(), is("Error while converting list item {\"missing\":\"fields\"} to OuCodeMigrateRequest"));
        }

        @Test
        @DisplayName("Should throw ConverterException when payload has malformed JSON structure")
        void shouldThrowConverterExceptionWhenPayloadMalformed() {
            final String malformedPayload = "{\"ouCodes\":[,\"migrated\":true}";

            final ConverterException exception = assertThrows(ConverterException.class,
                    () -> ouCodeMigrateConverter.convert(malformedPayload));

            assertThat(exception.getMessage(), is("Error while converting list item {\"ouCodes\":[,\"migrated\":true} to OuCodeMigrateRequest"));
        }
    }
}