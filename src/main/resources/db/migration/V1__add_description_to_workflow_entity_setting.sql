-- V1: Add description column to WORKFLOW_ENTITY_SETTING
-- This column is nullable TEXT, allowing a human-readable description for each workflow.
ALTER TABLE WORKFLOW_ENTITY_SETTING
    ADD COLUMN description TEXT NULL;

-- Audit table also needs the column (Envers shadow table)
ALTER TABLE WORKFLOW_ENTITY_SETTING_AUD
    ADD COLUMN description TEXT NULL;
