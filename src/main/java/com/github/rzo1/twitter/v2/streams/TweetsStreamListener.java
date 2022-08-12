package com.github.rzo1.twitter.v2.streams;

import com.twitter.clientlib.model.StreamingTweetResponse;

public interface TweetsStreamListener {
    void onStatus(StreamingTweetResponse status);

    void onException(Exception e);
}
