package com.github.rzo1.twitter.v2.streams;

import com.twitter.clientlib.model.StreamingTweetResponse;

public interface TweetsQueue {
    StreamingTweetResponse take();

    void put(StreamingTweetResponse streamingTweet);
}
