CREATE TABLE accounts (
    id text PRIMARY KEY CHECK (id IN ('xiaobai', 'xiaojimao')),
    name text NOT NULL CHECK ((id = 'xiaobai' AND name = '小白') OR (id = 'xiaojimao' AND name = '小鸡毛')),
    avatar_id uuid,
    cover_id uuid
);
INSERT INTO accounts (id, name) VALUES ('xiaobai', '小白'), ('xiaojimao', '小鸡毛');

CREATE TABLE media (
    id uuid PRIMARY KEY,
    owner_id text NOT NULL REFERENCES accounts(id),
    width integer NOT NULL CHECK (width > 0),
    height integer NOT NULL CHECK (height > 0),
    bytes bigint NOT NULL CHECK (bytes > 0),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
ALTER TABLE accounts ADD FOREIGN KEY (avatar_id) REFERENCES media(id);
ALTER TABLE accounts ADD FOREIGN KEY (cover_id) REFERENCES media(id);

CREATE TABLE posts (
    id uuid PRIMARY KEY,
    author_id text NOT NULL REFERENCES accounts(id),
    text text NOT NULL CHECK (char_length(text) <= 5000),
    location text CHECK (char_length(location) <= 200),
    location_address text CHECK (char_length(location_address) <= 500),
    visibility text NOT NULL CHECK (visibility IN ('public', 'private')),
    request_id uuid NOT NULL,
    request_body jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (author_id, request_id)
);
CREATE INDEX posts_timeline ON posts (created_at DESC, id DESC);
CREATE INDEX posts_author ON posts (author_id, created_at DESC, id DESC);

CREATE TABLE post_media (
    post_id uuid NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    media_id uuid NOT NULL REFERENCES media(id),
    position smallint NOT NULL CHECK (position BETWEEN 0 AND 8),
    PRIMARY KEY (post_id, position),
    UNIQUE (post_id, media_id)
);
CREATE INDEX post_media_media ON post_media (media_id);

CREATE TABLE likes (
    post_id uuid NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    account_id text NOT NULL REFERENCES accounts(id),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (post_id, account_id)
);

CREATE TABLE comments (
    id uuid PRIMARY KEY,
    post_id uuid NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    author_id text NOT NULL REFERENCES accounts(id),
    text text NOT NULL CHECK (char_length(text) <= 2000),
    media_id uuid REFERENCES media(id),
    reply_to_id uuid,
    request_id uuid NOT NULL,
    request_body jsonb NOT NULL,
    deleted boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (author_id, request_id),
    UNIQUE (post_id, id),
    FOREIGN KEY (post_id, reply_to_id) REFERENCES comments(post_id, id)
);
CREATE INDEX comments_post ON comments (post_id, created_at, id);
CREATE INDEX comments_media ON comments (media_id);

CREATE TABLE notifications (
    id uuid PRIMARY KEY,
    recipient_id text NOT NULL REFERENCES accounts(id),
    actor_id text NOT NULL REFERENCES accounts(id),
    post_id uuid NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    comment_id uuid REFERENCES comments(id) ON DELETE CASCADE,
    kind text NOT NULL CHECK (kind IN ('like', 'comment', 'reply')),
    event_key text NOT NULL,
    read_at timestamptz,
    dismissed boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (recipient_id, event_key),
    CHECK (recipient_id <> actor_id)
);
CREATE INDEX notifications_recipient ON notifications (recipient_id, created_at DESC, id DESC);
