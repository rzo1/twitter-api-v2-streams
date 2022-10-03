package com.github.rzo1.twitter.v2.streams;


import com.github.rzo1.twitter.v2.streams.exception.TwitterServiceException;
import com.twitter.clientlib.ApiException;
import com.twitter.clientlib.api.TwitterApi;
import com.twitter.clientlib.model.ConnectionExceptionProblem;
import com.twitter.clientlib.model.OperationalDisconnectProblem;
import com.twitter.clientlib.model.Problem;
import com.twitter.clientlib.model.StreamingTweetResponse;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Partly copied from <a href="https://github.com/twitterdev/twitter-api-java-sdk/blob/main/examples/src/main/java/com/twitter/clientlib/TweetsStreamListenersExecutor.java">https://github.com/twitterdev/twitter-api-java-sdk/blob/main/examples/src/main/java/com/twitter/clientlib/TweetsStreamListenersExecutor.java</a>
 */
public class TweetsStreamListenersExecutor {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TweetsStreamListenersExecutor.class);
    private final static long TIMEOUT_MILLIS = 60000;
    private final static long SLEEP_MILLIS = 100;
    private final static long BACKOFF_SLEEP_INTERVAL_MILLIS = 5000;
    private final TweetsQueue tweetsQueue = new LinkedBlockingTweetQueue(20000);
    private final List<TweetsStreamListener> listeners = new ArrayList<>();
    private final int asyncWorkers;
    private final int connectionRetries;
    private final TwitterApi twitterApi;
    private final ExecutorService executorService;
    private final AtomicBoolean isRunning = new AtomicBoolean(true);
    private final AtomicLong tweetStreamedTime = new AtomicLong(0);
    private final List<Future<?>> futures;
    private CountDownLatch watch;


    public TweetsStreamListenersExecutor(TwitterApi twitterApi, int asyncWorkers, int connectionRetries) {
        this.twitterApi = twitterApi;
        this.connectionRetries = connectionRetries;
        this.asyncWorkers = asyncWorkers;
        this.executorService = Executors.newFixedThreadPool(asyncWorkers + 1);
        this.watch = new CountDownLatch(asyncWorkers + 1);
        this.futures = new ArrayList<>();
    }

    public void addListener(TweetsStreamListener toAdd) {
        listeners.add(toAdd);
    }

    public void clearListeners() {
        this.listeners.clear();
    }


    public void listen() throws ApiException {
        this.isRunning.set(true);
        this.watch = new CountDownLatch(asyncWorkers + 1);
        startDispatcher();
        startQueuer(1);
    }

    private void resetTweetStreamedTime() {
        tweetStreamedTime.set(System.currentTimeMillis());
    }

    private boolean isTweetStreamStale() {
        return System.currentTimeMillis() - tweetStreamedTime.get() > TIMEOUT_MILLIS;
    }

    private void startQueuer(int currentConnectionAttempt) {
        if (currentConnectionAttempt <= connectionRetries) {
            currentConnectionAttempt++;
        } else {
            currentConnectionAttempt = 1;
        }
        futures.add(executorService.submit(new TweetsQueuer(twitterApi, connectionRetries, currentConnectionAttempt)));
    }

    private void startDispatcher() {
        for (int i = 0; i < asyncWorkers; i++) {
            futures.add(executorService.submit(new TweetsDispatcher()));
        }
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    public synchronized void stop() {
        isRunning.set(false);
        logger.info("TweetsStreamListenersExecutor is stopping....");
    }

    public synchronized void shutdown() {
        stop();
        executorService.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!executorService.awaitTermination(15, TimeUnit.SECONDS)) {
                executorService.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!executorService.awaitTermination(15, TimeUnit.SECONDS)) {
                    logger.warn("TweetsStreamListenersExecutor did not terminate...");
                }
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            executorService.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }

    public synchronized void reinitialize() throws ApiException {
        logger.debug("Reinitializing TwitterStreamListenerExecutor");
        logger.debug("Clearing listeners...");
        clearListeners();
        logger.debug("Clearing run flag...");
        stop();
        logger.debug("Waiting for termination of running threads...");
        try {
            // cancel all running threads...
            for (Future<?> f : futures) {
                f.cancel(true);
            }
            boolean success = watch.await(30, TimeUnit.SECONDS);
            if (success) {
                logger.debug("All running threads stopped...");
            } else {
                logger.warn("Some threads did not stop...");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        logger.debug("Restarting dispatcher and queuer threads...");
        listen();
        logger.debug("Reinitializing TwitterStreamListenerExecutor [DONE]");
    }

    private class TweetsDispatcher implements Runnable {

        @Override
        public void run() {
            processTweets();
        }

        private void processTweets() {
            while (isRunning.get()) {
                StreamingTweetResponse streamingTweet;
                try {
                    streamingTweet = tweetsQueue.take();
                    if (streamingTweet == null) {
                        continue;
                    }
                    for (TweetsStreamListener listener : listeners) {
                        listener.onStatus(streamingTweet);
                    }

                } catch (Exception e) {
                    for (TweetsStreamListener listener : listeners) {
                        listener.onException(e);
                    }
                }
            }
            watch.countDown();
        }
    }

    private class TweetsQueuer implements Runnable {
        private static final org.slf4j.Logger innerLogger = org.slf4j.LoggerFactory.getLogger(TweetsQueuer.class);
        private final TwitterApi client;
        private final int retries;
        private final int currentConnectionAttempt;

        public TweetsQueuer(TwitterApi client, int connectionRetries, int currentConnectionAttempt) {
            this.client = client;
            this.retries = connectionRetries;
            this.currentConnectionAttempt = currentConnectionAttempt;
        }

        @Override
        public void run() {
            queueTweets();
        }

        private void queueTweets() {
            boolean needsReconnect = false;
            String line;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connect()))) {
                while (isRunning.get() && !needsReconnect) {
                    line = reader.readLine();
                    if (line == null || line.isBlank()) {
                        innerLogger.debug("Waiting to receive a tweet...");
                        Thread.sleep(SLEEP_MILLIS);
                        if (isTweetStreamStale()) {
                            innerLogger.debug("We did not receive a new tweet for {} ms. Stream might be stale. Trigger a reconnect.", TIMEOUT_MILLIS);
                            needsReconnect = true;
                        }
                        continue;
                    }
                    resetTweetStreamedTime(); // we received a tweet, we can reset the timer now...
                    innerLogger.debug("Queuing a tweet...");
                    final StreamingTweetResponse str = StreamingTweetResponse.fromJson(line);

                    needsReconnect = hasReconnectErrors(str);

                    if (!needsReconnect) {
                        tweetsQueue.put(str);
                    }
                }
            } catch (Exception e) {
                innerLogger.warn(e.getLocalizedMessage(), e);
                innerLogger.warn("Lost connection to the Twitter API v2 endpoint due to an unexpected error. Try to reconnect...");
                needsReconnect = true;
            }

            if (needsReconnect) {
                // Wait a bit before starting the TweetsQueuer and calling the API again.
                sleep(BACKOFF_SLEEP_INTERVAL_MILLIS * currentConnectionAttempt);
                startQueuer(currentConnectionAttempt);
            }

            watch.countDown();
        }

        private boolean hasReconnectErrors(StreamingTweetResponse str) {
            // https://github.com/twitterdev/twitter-api-java-sdk/issues/37
            if (str.getErrors() != null) {
                for (Problem problem : str.getErrors()) {
                    if (problem instanceof OperationalDisconnectProblem || problem instanceof ConnectionExceptionProblem) {
                        logger.warn("Experiencing a disconnect: {}. Trying to re-connect!", problem);
                        //force a re-connect
                        return true;
                    }
                }
            }
            return false;
        }

        private InputStream getConnection() throws ApiException {
            return client.tweets().searchStream()
                    .backfillMinutes(0) //we do not have academic access, so no possibility for backfill, see https://developer.twitter.com/en/docs/twitter-api/tweets/filtered-stream/integrate/recovery-and-redundancy-features
                    .expansions(TwitterEntityFields.getExpansions())
                    .mediaFields(TwitterEntityFields.getMediaFields())
                    .placeFields(TwitterEntityFields.getPlaceFields())
                    .userFields(TwitterEntityFields.getUserFields())
                    .tweetFields(TwitterEntityFields.getTweetFields())
                    .execute(retries);

        }

        private InputStream connect() {
            boolean connected = false;
            while (!connected && isRunning.get()) {
                try {
                    InputStream stream = getConnection();
                    connected = true;
                    innerLogger.info("Twitter API V2: Successfully reconnected ...");
                    return stream;
                } catch (Exception e) {
                    long waitingTimeInMs = 30 * 1000;
                    innerLogger.info("Twitter API V2: Reconnect failed due to: '{}'.", e.getLocalizedMessage(), e);
                    innerLogger.info("Twitter API V2: Trying to reconnect...");
                    if (e instanceof ApiException ae) {
                        if (ae.getCode() == 429) {
                            final Map<String, List<String>> headers = ae.getResponseHeaders();
                            //we tried it too often, we will wait 15 min (reset windows) before we try to re-connect ;)
                            waitingTimeInMs = 15 * 60 * 1000;
                            //let's check if we can make this windows more accurate!
                            if (headers != null) {
                                // the remaining window before the rate limit resets, in UTC epoch seconds
                                List<String> xRateLimitReset = headers.get("x-rate-limit-reset");
                                if (xRateLimitReset != null) {
                                    if (!xRateLimitReset.isEmpty()) {
                                        try {
                                            final long now = Instant.now(Clock.systemUTC()).getEpochSecond();
                                            final long reset = Long.parseLong(xRateLimitReset.get(0));
                                            final long wait = 1000 * (reset - now);

                                            waitingTimeInMs = wait <= 0 ? 1000 : wait;

                                        } catch (NumberFormatException ignored) {
                                        }
                                    }
                                }
                            }
                        }
                    }
                    sleep(waitingTimeInMs);
                }
            }
            throw new TwitterServiceException("Could not connect to Twitter API V2");
        }

        private void sleep(long millis) {
            try {
                innerLogger.debug("Twitter API V2: Waiting {} seconds before trying to reconnect.", millis / 1000);
                Thread.sleep(millis);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }
}