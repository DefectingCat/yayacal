-- 经期记录不分账号，整份文档以 revision 做乐观并发；区间不重叠由服务端整份校验后替换。
CREATE TABLE period_state (
    id boolean PRIMARY KEY DEFAULT true CHECK (id),
    revision bigint NOT NULL DEFAULT 0 CHECK (revision >= 0),
    cycle_length smallint NOT NULL DEFAULT 28 CHECK (cycle_length BETWEEN 15 AND 60),
    period_length smallint NOT NULL DEFAULT 5 CHECK (period_length BETWEEN 2 AND 10),
    luteal_length smallint NOT NULL DEFAULT 14 CHECK (luteal_length BETWEEN 10 AND 16)
);
INSERT INTO period_state DEFAULT VALUES;

CREATE TABLE period_ranges (
    start_date date PRIMARY KEY,
    end_date date CHECK (end_date >= start_date)
);

CREATE TABLE period_notes (
    day date PRIMARY KEY,
    mood text CHECK (mood IN ('happy', 'calm', 'tired', 'low', 'irritable')),
    text text NOT NULL DEFAULT '' CHECK (char_length(text) <= 500),
    CHECK (mood IS NOT NULL OR text <> '')
);
