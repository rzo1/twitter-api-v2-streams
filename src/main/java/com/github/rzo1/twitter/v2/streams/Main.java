package com.github.rzo1.twitter.v2.streams;

import com.github.rzo1.twitter.v2.streams.exception.RateLimitException;
import com.github.rzo1.twitter.v2.streams.exception.TwitterServiceException;
import com.github.rzo1.twitter.v2.streams.rules.FilteredStreamRulePredicate;
import com.twitter.clientlib.ApiException;
import com.twitter.clientlib.TwitterCredentialsBearer;
import com.twitter.clientlib.api.TwitterApi;
import com.twitter.clientlib.model.*;
import picocli.CommandLine;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


@CommandLine.Command(header = "%nTwitter API v2 Streams%n",
        description = "Example application for 'twitter-api-java-sdk/37'.%n")
public class Main implements Callable<Integer> {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TweetsStreamListenersExecutor.class);
    @CommandLine.Option(names = "-token", description = "The Bearer Token", required = true)
    private String bearerToken;

    @CommandLine.Option(names = "-hashtags", description = "The Hashtags to follow", required = true)
    private List<String> hashtags;

    @CommandLine.Option(names = "-retries", description = "Connection retries", required = false)
    private int connectionRetries = 5;

    @CommandLine.Option(names = "-threads", description = "Worker Thread Pool Size", required = false)
    private int workerThreadPoolSize = 5;

    @CommandLine.Option(names = "-days", description = "Days to run", required = false)
    private int daysToRun = 14;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }


    @Override
    public Integer call() throws Exception {
        final TwitterApi client = initTwitterApiClient();
        initStreamRules(hashtags, client, connectionRetries);
        listen(client, connectionRetries, workerThreadPoolSize);

        //wait ...
        final CountDownLatch cdl = new CountDownLatch(1);
        cdl.await(daysToRun, TimeUnit.DAYS);

        return 0;
    }

    private TwitterApi initTwitterApiClient() {
        return new TwitterApi(new TwitterCredentialsBearer(bearerToken));
    }

    private void listen(final TwitterApi api, final int connectionRetries, final int workerThreadPoolSize) {
        try {
            final TweetsStreamListenersExecutor tsle = new TweetsStreamListenersExecutor(api, workerThreadPoolSize, connectionRetries);
            tsle.addListener(new TweetsStreamListenerImpl());
            tsle.listen();
        } catch (ApiException e) {
            if (e.getCode() == 429) {
                throwRateLimitException("Encountered rate limit while initalization or re-initalization of TwitterService!", e);
            } else {
                throw new TwitterServiceException(e);
            }
        }
    }

    private void initStreamRules(final List<String> hashtags, final TwitterApi api, final int connectionRetries) {

        try {
            //1. Retrieve all (existing) rules
            final RulesLookupResponse response = api
                    .tweets()
                    .getRules()
                    .execute(connectionRetries);

            //2. Delete them
            final List<String> toDelete = response.getData().stream().map(Rule::getId).toList();

            final AddOrDeleteRulesResponse deleteResponse = api
                    .tweets()
                    .addOrDeleteRules(
                            new AddOrDeleteRulesRequest(
                                    new DeleteRulesRequest()
                                            .delete(
                                                    new DeleteRulesRequestDelete()
                                                            .ids(toDelete))))
                    .execute(connectionRetries);


            if (deleteResponse.getErrors() != null) {
                for (Problem problem : deleteResponse.getErrors()) {
                    logger.warn("Could not delete stream rules. Problem is: {}", problem);
                }
            }

            //3. Try to re-create rules
            List<RuleNoId> toCreate = new ArrayList<>(createRules(hashtags));

            if (!toCreate.isEmpty()) {
                final AddOrDeleteRulesResponse addResponse = api
                        .tweets()
                        .addOrDeleteRules(
                                new AddOrDeleteRulesRequest(
                                        new AddRulesRequest()
                                                .add(toCreate)))
                        .execute(connectionRetries);

                if (addResponse.getErrors() != null) {
                    for (Problem problem : addResponse.getErrors()) {
                        logger.warn("Could not add stream rules. Problem is: {}", problem);
                    }
                }
            }

        } catch (ApiException e) {
            if (e.getCode() == 429) {
                throwRateLimitException("Encountered rate limit while initalization or re-initalization of TwitterService!", e);
            } else {
                throw new TwitterServiceException(e);
            }
        }
    }

    private Set<RuleNoId> createRules(List<String> hashtags) {
        final Set<RuleNoId> rules = new HashSet<>();

        final Map<Integer, Set<String>> keywords = getQueryKeywordsForTrackedTerms(hashtags);

        for (Map.Entry<Integer, Set<String>> entry : keywords.entrySet()) {
            FilteredStreamRulePredicate cTermPredicate = null;
            FilteredStreamRulePredicate pTermPredicate = null;
            for (String filterTerm : entry.getValue()) {
                cTermPredicate = FilteredStreamRulePredicate.withKeyword(filterTerm);

                if (pTermPredicate != null) {
                    cTermPredicate = cTermPredicate.or(pTermPredicate);
                }

                pTermPredicate = cTermPredicate;
            }

            if (cTermPredicate != null) {
                //do not allow retweets ;-)
                cTermPredicate = cTermPredicate.capsule().and(FilteredStreamRulePredicate.isRetweet(FilteredStreamRulePredicate.empty()).negate());
                rules.add(new RuleNoId().tag("TrackedTerms-Query-" + entry.getKey()).value(cTermPredicate.toString()));
                logger.info("Creating rule: '{}'", cTermPredicate);
            }
        }
        return rules;
    }

    private Map<Integer, Set<String>> getQueryKeywordsForTrackedTerms(Collection<String> toCheck) {
        return getQueryKeywords(toCheck, " OR ".length() + "() AND -(is:retweet)".length());
    }

    private Map<Integer, Set<String>> getQueryKeywords(Collection<String> toCheck, int opLength) {
        final Map<Integer, Set<String>> queries = new HashMap<>();
        Set<String> entry = new HashSet<>();
        int idx = 0;
        int size = 0;
        for (String t : toCheck) {
            size += (t.length() + opLength);

            if (size <= 512) { //max length according to Twitter API v2
                entry.add(t);
            } else {
                entry = new HashSet<>();
                entry.add(t);
                size = t.length() + opLength;
                idx++;
            }
            queries.putIfAbsent(idx, entry);
        }
        return queries;
    }

    private void throwRateLimitException(String msg, ApiException ae) {
        final Map<String, List<String>> headers = ae.getResponseHeaders();
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
                        throw new RateLimitException(msg, wait <= 0 ? 1000 : wait);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        throw new RateLimitException(msg, 15 * 60 * 1000);
    }

    private static class TweetsStreamListenerImpl implements TweetsStreamListener {
        private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TweetsStreamListenerImpl.class);

        @Override
        public void onStatus(StreamingTweetResponse status) {
            if (status.getErrors() == null) {
                Tweet tweet = status.getData();
                if (tweet != null) {
                    logger.debug("Received a tweet with ID='{}'", tweet.getId());
                } else {
                    logger.warn("Received tweet was NULL.");
                }
            } else {
                for (Problem problem : status.getErrors()) {
                    logger.warn("Caught an error: '{}'.", problem.toString());
                }
            }

        }

        @Override
        public void onException(Exception e) {
            logger.warn(e.getLocalizedMessage(), e);
        }
    }
}
