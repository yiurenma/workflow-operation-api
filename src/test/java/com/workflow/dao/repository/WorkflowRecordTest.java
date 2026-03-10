package com.workflow.dao.repository;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowRecordTest {

    @Test
    void onCreateShouldPopulateAuditFields() {
        WorkflowRecord record = new WorkflowRecord();
        record.onCreate();

        assertNotNull(record.getCreatedDateTime());
        assertNotNull(record.getLastModifiedDateTime());
    }

    @Test
    void onUpdateShouldUpdateLastModifiedTime() throws InterruptedException {
        WorkflowRecord record = new WorkflowRecord();
        record.onCreate();
        long before = record.getLastModifiedDateTime().getTime();

        Thread.sleep(2);
        record.onUpdate();

        assertTrue(record.getLastModifiedDateTime().getTime() >= before);
    }
}
