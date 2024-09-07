package uk.gov.moj.cpp.courtscheduler.api.converter;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.fileToString;

import uk.gov.moj.cpp.courtscheduler.domain.OuCodeMigrateRequest;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OuCodeMigrateConverterTest {

    @InjectMocks
    private OuCodeMigrateConverter ouCodeMigrateConverter;

    @Test
    public void shouldConvertProvisionalSlot() {
        final String payload = fileToString("/test-data/oucode-migrate-courtscheduler.json");

        final OuCodeMigrateRequest ouCodeMigrateRequest = ouCodeMigrateConverter.convert(payload);

        assertThat(ouCodeMigrateRequest.getOuCodes().size(),is(3));
        final List<String> ouCodes = ouCodeMigrateRequest.getOuCodes();
        assertThat(ouCodes.get(0), is("B12345"));
    }
}