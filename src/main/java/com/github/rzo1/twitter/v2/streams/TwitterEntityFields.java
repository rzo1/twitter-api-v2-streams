package com.github.rzo1.twitter.v2.streams;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public abstract class TwitterEntityFields {

    private final static Set<String> TWEET_FIELDS = new HashSet<>();
    private final static Set<String> PLACE_FIELDS = new HashSet<>();
    private final static Set<String> MEDIA_FIELDS = new HashSet<>();
    private final static Set<String> USER_FIELDS = new HashSet<>();
    private final static Set<String> EXPANSIONS = new HashSet<>();

    static {
        TWEET_FIELDS.add("author_id");
        TWEET_FIELDS.add("id");
        TWEET_FIELDS.add("created_at");
        TWEET_FIELDS.add("public_metrics");
        TWEET_FIELDS.add("entities");
        TWEET_FIELDS.add("geo");
        TWEET_FIELDS.add("lang");
        TWEET_FIELDS.add("source");
        TWEET_FIELDS.add("referenced_tweets");
        TWEET_FIELDS.add("in_reply_to_user_id");

        PLACE_FIELDS.add("country");
        PLACE_FIELDS.add("geo");
        PLACE_FIELDS.add("name");
        PLACE_FIELDS.add("place_type");
        PLACE_FIELDS.add("contained_within");

        MEDIA_FIELDS.add("duration_ms");
        MEDIA_FIELDS.add("height");
        MEDIA_FIELDS.add("media_key");
        MEDIA_FIELDS.add("preview_image_url");
        MEDIA_FIELDS.add("public_metrics");
        MEDIA_FIELDS.add("width");
        MEDIA_FIELDS.add("url");

        EXPANSIONS.add("author_id");
        EXPANSIONS.add("attachments.media_keys");
        EXPANSIONS.add("geo.place_id");

        USER_FIELDS.add("created_at");
        USER_FIELDS.add("description");
        USER_FIELDS.add("location");
        USER_FIELDS.add("profile_image_url");
        USER_FIELDS.add("protected");
        USER_FIELDS.add("public_metrics");
        USER_FIELDS.add("url");
        USER_FIELDS.add("verified");
    }

    public static Set<String> getTweetFields() {
        return Collections.unmodifiableSet(TWEET_FIELDS);
    }

    public static Set<String> getPlaceFields() {
        return Collections.unmodifiableSet(PLACE_FIELDS);
    }

    public static Set<String> getMediaFields() {
        return Collections.unmodifiableSet(MEDIA_FIELDS);
    }

    public static Set<String> getUserFields() {
        return Collections.unmodifiableSet(USER_FIELDS);
    }

    public static Set<String> getExpansions() {
        return Collections.unmodifiableSet(EXPANSIONS);
    }
}
