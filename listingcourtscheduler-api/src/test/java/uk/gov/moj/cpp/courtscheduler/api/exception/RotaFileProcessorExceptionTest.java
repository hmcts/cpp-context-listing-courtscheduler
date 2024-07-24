package uk.gov.moj.cpp.courtscheduler.api.exception;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import java.io.PrintWriter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RotaFileProcessorExceptionTest {


    @Spy
    private Throwable throwable;

    private static final String ERROR_MESSAGE = "error message";

    @Test
    void shouldCreateExceptionWithSentMessage() {

        final RotaFileProcessorException rotaFileProcessorException = new RotaFileProcessorException(ERROR_MESSAGE, throwable);

        assertTrue(rotaFileProcessorException.getMessage().contains(ERROR_MESSAGE));
        verify(throwable, atLeastOnce()).printStackTrace(any(PrintWriter.class));
    }

    @Test
    void shouldCreateExceptionWithoutMessage() {
        final RotaFileProcessorException rotaFileProcessorException = new RotaFileProcessorException(throwable);

        assertFalse(rotaFileProcessorException.getMessage().contains(ERROR_MESSAGE));
        verify(throwable, atLeastOnce()).printStackTrace(any(PrintWriter.class));
    }
}
