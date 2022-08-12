package com.github.rzo1.twitter.v2.streams;

import com.twitter.clientlib.model.StreamingTweetResponse;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class LinkedBlockingTweetQueue implements TweetsQueue {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(LinkedBlockingTweetQueue.class);
    private final BlockingQueue<StreamingTweetResponse> tweetsQueue;

    private final AtomicInteger currentSize = new AtomicInteger(0);

    public LinkedBlockingTweetQueue(int initialSize) {
        tweetsQueue = new LinkedBlockingQueue<>(initialSize);
    }

    @Override
    public StreamingTweetResponse take() {
        try {
            StreamingTweetResponse str = tweetsQueue.take();
            logger.debug("Returning Tweet from Queue. Current size: {}", currentSize.getAndDecrement());
            return str;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    @Override
    public void put(StreamingTweetResponse streamingTweet) {
        try {
            tweetsQueue.put(streamingTweet);
            currentSize.getAndIncrement();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}