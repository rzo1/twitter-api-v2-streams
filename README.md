# Twitter API v2 Streams Example

This repository contains code related to https://github.com/twitterdev/twitter-api-java-sdk/issues/37


## Command line options / help usage

```bash
Twitter API v2 Streams

Usage: <main class> [-days=<daysToRun>] [-retries=<connectionRetries>]
                    [-threads=<workerThreadPoolSize>] -token=<bearerToken>
                    -hashtags=<hashtags> [-hashtags=<hashtags>]...
Example application for 'twitter-api-java-sdk/37'.

      -days=<daysToRun>      Days to run
      -hashtags=<hashtags>   The Hashtags to follow
      -retries=<connectionRetries>
                             Connection retries
      -threads=<workerThreadPoolSize>
                             Worker Thread Pool Size
      -token=<bearerToken>   The Bearer Token
```

## Usage & Run

1. Build with Maven and Java 17
2. Move to the `target` folder
3. Run the following command (replace hashtags and bearer token accordingly)
```
java -jar twitter-streams-v2-exec-1.0-SNAPSHOT.jar -token 'BEARER_TOKEN' -hashtags 'HASHTAG1' -hashtags 'HASHTAG2' -hashtags 'HASHTAG3'
```
4. Wait ... ;)
