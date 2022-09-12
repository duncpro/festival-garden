CREATE TABLE IF NOT EXISTS festival (
    "name" VARCHAR NOT NULL,
    id VARCHAR NOT NULL PRIMARY KEY,
    start_date BIGINT NOT NULL,
    end_date BIGINT NOT NULL,
    url VARCHAR NOT NULL,
    longitude DECIMAL NOT NULL,
    latitude DECIMAL NOT NULL,
    region_name VARCHAR,
    municipality_name VARCHAR
);

CREATE TABLE IF NOT EXISTS performing_artist (
    "name" VARCHAR NOT NULL,
    spotify_id VARCHAR NOT NULL,
    spotify_genres VARCHAR NOT NULL,
    spotify_popularity INT NOT NULL,
    festival_id VARCHAR NOT NULL,
    smallest_image_url VARCHAR,
    PRIMARY KEY (spotify_id, festival_id)
);

CREATE TABLE IF NOT EXISTS anonymous_user (
    id VARCHAR NOT NULL PRIMARY KEY,
    token VARCHAR NOT NULL,
    token_expiration BIGINT NOT NULL,
    spotify_state_arg VARCHAR,
    has_written_library_index BOOLEAN NOT NULL
);

CREATE TABLE IF NOT EXISTS spotify_user_credentials (
    spotify_access_token VARCHAR NOT NULL,
    spotify_refresh_token VARCHAR NOT NULL,
    spotify_access_token_expiration BIGINT NOT NULL,
    fg_user_id VARCHAR NOT NULL PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS user_liked_artist (
    spotify_artist_id VARCHAR NOT NULL,
    user_id VARCHAR NOT NULL,
    song_count INT NOT NULL DEFAULT 1,
    PRIMARY KEY (spotify_artist_id, user_id)
);

/* The indexer will insert all pages for some user */
/* If a conflict occurs, then another indexer is already running, one fails permanently */
/* The other continues */
CREATE TABLE IF NOT EXISTS user_library_page (
    user_id VARCHAR NOT NULL,
    page_start_track_offset INT NOT NULL,
    page_id VARCHAR NOT NULL,
    PRIMARY KEY (user_id, page_start_track_offset)
);

/* If no row in this table exists for some user_library_page then it is pending */
CREATE TABLE IF NOT EXISTS user_library_page_result (
    user_id VARCHAR NOT NULL,
    page_id VARCHAR NOT NULL PRIMARY KEY,
    /* true = Processed normally */
    /* false = Processing for this page failed permanently */
    was_successful BOOLEAN NOT NULL
);