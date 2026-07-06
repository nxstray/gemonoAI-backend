CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =====================================================
-- USERS
-- =====================================================

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    email VARCHAR(255) NOT NULL UNIQUE,
    full_name VARCHAR(255),
    avatar_url VARCHAR(500),

    password VARCHAR(255),

    role VARCHAR(20) NOT NULL,

    provider VARCHAR(50),
    provider_id VARCHAR(255),

    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- =====================================================
-- CONVERSATIONS
-- =====================================================

CREATE TABLE conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL,

    title VARCHAR(255) NOT NULL,

    created_at TIMESTAMP,
    updated_at TIMESTAMP,

    CONSTRAINT fk_conversation_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_conversations_user_id
    ON conversations(user_id);

-- =====================================================
-- MESSAGES
-- =====================================================

CREATE TABLE messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    conversation_id UUID NOT NULL,

    role VARCHAR(50) NOT NULL,

    content TEXT NOT NULL,

    attachment_url VARCHAR(1000),
    attachment_type VARCHAR(255),

    agent_steps TEXT,

    created_at TIMESTAMP,

    CONSTRAINT fk_message_conversation
        FOREIGN KEY (conversation_id)
        REFERENCES conversations(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_messages_conversation_id
    ON messages(conversation_id);

-- =====================================================
-- GUEST CONVERSATIONS
-- =====================================================

CREATE TABLE guest_conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    guest_id VARCHAR(255) NOT NULL,

    title VARCHAR(255) NOT NULL,

    merged_to_user_id UUID,

    merged_at TIMESTAMP,

    created_at TIMESTAMP,
    updated_at TIMESTAMP,

    CONSTRAINT fk_guest_conv_user
        FOREIGN KEY (merged_to_user_id)
        REFERENCES users(id)
        ON DELETE SET NULL
);

CREATE INDEX idx_guest_conversations_guest_id
    ON guest_conversations(guest_id);

-- =====================================================
-- GUEST MESSAGES
-- =====================================================

CREATE TABLE guest_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    guest_conversation_id UUID NOT NULL,

    role VARCHAR(50) NOT NULL,

    content TEXT NOT NULL,

    attachment_url VARCHAR(1000),
    attachment_type VARCHAR(255),

    agent_steps TEXT,

    created_at TIMESTAMP,

    CONSTRAINT fk_guest_message_conversation
        FOREIGN KEY (guest_conversation_id)
        REFERENCES guest_conversations(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_guest_messages_conversation_id
    ON guest_messages(guest_conversation_id);