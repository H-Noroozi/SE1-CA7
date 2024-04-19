package ir.ramtung.tinyme.domain.entity;

public class InactiveOrderBook extends OrderBook {
    public InactiveOrderBook() {
        super();
    }

    public StopLimitOrder checkFirstInactiveOrder(Side side, int lastTransactionPrice) {
        var queue = getQueue(side);
        StopLimitOrder stopLimitOrder = (StopLimitOrder) queue.getFirst();
        if (stopLimitOrder.mustBeActive(lastTransactionPrice)){
            return stopLimitOrder;
        }
        else{
            return null;
        }
    }
}
