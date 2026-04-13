package com.workflow.common.configuration.logging;

import lombok.extern.slf4j.Slf4j;
import org.zalando.logbook.Correlation;
import org.zalando.logbook.HttpLogWriter;
import org.zalando.logbook.Precorrelation;

/**
 * Writes Logbook request/response entries at INFO level instead of TRACE,
 * so they appear in production logs without changing the root log level.
 */
@Slf4j
public class InfoHttpLogWriter implements HttpLogWriter {

    @Override
    public boolean isActive() {
        return log.isInfoEnabled();
    }

    @Override
    public void write(Precorrelation precorrelation, String request) {
        log.info(request);
    }

    @Override
    public void write(Correlation correlation, String response) {
        log.info(response);
    }
}
