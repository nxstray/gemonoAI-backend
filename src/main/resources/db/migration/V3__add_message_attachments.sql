-- Adds the multi-attachment JSON column to messages and guest_messages.
-- attachment_url / attachment_type stay untouched for backward compatibility
-- with older messages that only ever had a single attachment.
ALTER TABLE messages ADD COLUMN IF NOT EXISTS attachments TEXT;
ALTER TABLE guest_messages ADD COLUMN IF NOT EXISTS attachments TEXT;