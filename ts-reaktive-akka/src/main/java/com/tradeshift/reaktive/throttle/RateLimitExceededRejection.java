package com.tradeshift.reaktive.throttle;

import static akka.http.javadsl.server.Directives.complete;

import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.CustomRejection;
import akka.http.javadsl.server.RejectionHandler;

/**
 * Rejection that indicates a rate limit has been exceeded.
 */
public class RateLimitExceededRejection implements CustomRejection {
    public static final RateLimitExceededRejection instance = new RateLimitExceededRejection();
    
    public static RejectionHandler defaultHandler = RejectionHandler.newBuilder()
        .handle(RateLimitExceededRejection.class, rejection ->
            complete(StatusCodes.ENHANCE_YOUR_CALM, "API Rate Limit Exceeded for this user.")
        )
        .build();
    
    private RateLimitExceededRejection() {}
}