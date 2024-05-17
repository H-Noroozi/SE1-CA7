package ir.ramtung.tinyme.messaging.request;

import ir.ramtung.tinyme.domain.entity.Side;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class ChangeMatchingStateRq {
    private String securityIsin;
    private MatchingState targetState;

    public ChangeMatchingStateRq(String securityIsin, MatchingState targetState) {
        this.securityIsin = securityIsin;
        this.targetState = targetState;
    }
    public static ChangeMatchingStateRq createContinuousStateOrderRq(String securityIsin) {
        return new ChangeMatchingStateRq(securityIsin, MatchingState.CONTINUOUS);
    }

    public static ChangeMatchingStateRq createAuctionStateOrderRq(String securityIsin) {
        return new ChangeMatchingStateRq(securityIsin, MatchingState.AUCTION);
    }
}
