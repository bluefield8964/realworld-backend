package realworld_backend.commerce.model.subscription.enums;

public enum SubscriptionStatus {
    CREATED, //Subscription order generated
    INITIAL_FAIL,//Subscription order generated fail
    PENDING, //Checkout session created, waiting for customer action

    PAYING, //Checkout confirmed, waiting for provider subscription/invoice settlement
    CHECKOUT_FAIL, //Checkout finished but async confirmation failed or customer can retry
    CHECKOUT_EXPIRED, //Checkout session expired before subscription activation

    PAST_DUE,//subscription prolonging
    PAUSED,  //subscription paused
    CANCELED,//subscription canceled
    ACTIVE,  //Currently subscribing
    TRIALING//trialing


}
