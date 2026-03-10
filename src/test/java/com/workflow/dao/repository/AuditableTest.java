package com.workflow.dao.repository;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditableTest {

    @Test
    void onCreateShouldSetCreatedAndUpdatedTime() {
        Auditable auditable = new Auditable();
        auditable.onCreate();

        assertNotNull(auditable.getCreatedDateTime());
        assertNotNull(auditable.getLastModifiedDateTime());
    }

    @Test
    void onUpdateShouldRefreshUpdatedTime() throws InterruptedException {
        Auditable auditable = new Auditable();
        auditable.onCreate();
        long before = auditable.getLastModifiedDateTime().getTime();

        Thread.sleep(2);
        auditable.onUpdate();

        assertTrue(auditable.getLastModifiedDateTime().getTime() >= before);
    }
}
