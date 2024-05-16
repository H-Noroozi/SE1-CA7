package ir.ramtung.tinyme.domain.entity;

public enum MatchingOutcome {
    EXECUTED,
    ACTIVATED,
    QUEUED_AS_INACTIVE_ORDER,
    NOT_ENOUGH_CREDIT,
    NOT_ENOUGH_POSITIONS,
    NOT_ENOUGH_INITIAL_TRANSACTION,
    AUCTIONED
}
