package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.ListIterator;
import ir.ramtung.tinyme.messaging.request.MatchingState;

@Service
public class Matcher {
    public MatchResult match(Order newOrder) {
        OrderBook orderBook = newOrder.getSecurity().getOrderBook();
        MatchingState state = newOrder.getSecurity().getState();
        OpeningData openingData = newOrder.getSecurity().findOpeningData();
        LinkedList<Trade> trades = new LinkedList<>();

        while (orderBook.hasOrderOfType(newOrder.getSide().opposite()) && newOrder.getQuantity() > 0) {
            Order matchingOrder = orderBook.matchWithFirst(newOrder);
            if (matchingOrder == null)
                break;

            int price;
            if (state == MatchingState.CONTINUOUS)
                price = matchingOrder.getPrice();
            else
                price = openingData.getOpeningPrice();

            Trade trade = new Trade(newOrder.getSecurity(), price, Math.min(newOrder.getQuantity(), matchingOrder.getQuantity()), newOrder, matchingOrder);
            if (newOrder.getSide() == Side.BUY && state == MatchingState.CONTINUOUS) {
                if (trade.buyerHasEnoughCredit())
                    trade.decreaseBuyersCredit();
                else {
                    rollbackTrades(newOrder, trades);
                    return MatchResult.notEnoughCredit();
                }
            }
            trade.increaseSellersCredit();
            trades.add(trade);

            if (newOrder.getQuantity() >= matchingOrder.getQuantity()) {
                newOrder.decreaseQuantity(matchingOrder.getQuantity());
                orderBook.removeFirst(matchingOrder.getSide());
                if (matchingOrder instanceof IcebergOrder icebergOrder) {
                    icebergOrder.decreaseQuantity(matchingOrder.getQuantity());
                    icebergOrder.replenish();
                    if (icebergOrder.getQuantity() > 0)
                        orderBook.enqueue(icebergOrder);
                }
            } else {
                matchingOrder.decreaseQuantity(newOrder.getQuantity());
                newOrder.makeQuantityZero();
            }
        }
        return MatchResult.executed(newOrder, trades);
    }

    private void rollbackTrades(Order newOrder, LinkedList<Trade> trades) {
        if (newOrder.getSide() == Side.BUY) {
            newOrder.getBroker().increaseCreditBy(trades.stream().mapToLong(Trade::getTradedValue).sum());
            trades.forEach(trade -> trade.getSell().getBroker().decreaseCreditBy(trade.getTradedValue()));
        }
        else
            newOrder.getBroker().decreaseCreditBy(trades.stream().mapToLong(Trade::getTradedValue).sum());

        ListIterator<Trade> it = trades.listIterator(trades.size());
        while (it.hasPrevious()) {
            if (newOrder.getSide() == Side.BUY)
                newOrder.getSecurity().getOrderBook().restoreOrder(it.previous().getSell());
            else
                newOrder.getSecurity().getOrderBook().restoreOrder(it.previous().getBuy());
        }
    }

    public MatchResult execute(Order order) {
        int initialQuantity = order.getQuantity();
        OpeningData openingData = order.getSecurity().findOpeningData();
        MatchResult result = match(order);
        if (result.outcome() == MatchingOutcome.NOT_ENOUGH_CREDIT)
            return result;


        if (result.remainder().getQuantity() > 0) {
            if (order.getSide() == Side.BUY) {
                if(order.getSecurity().getState() == MatchingState.CONTINUOUS) {
                    if (!order.getBroker().hasEnoughCredit(order.getValue())) {
                        rollbackTrades(order, result.trades());
                        return MatchResult.notEnoughCredit();
                    }

                    order.getBroker().decreaseCreditBy(order.getValue());
                }
                else
                    order.getBroker().increaseCreditBy(order.getValue() - openingData.getOpeningPrice());
            }

            if (order.isNew() && order.getMinimumExecutionQuantity() > (initialQuantity - result.remainder().getQuantity())){
                rollbackTrades(order, result.trades());
                return MatchResult.notEnoughInitialTransaction();
            }

            order.getSecurity().getOrderBook().enqueue(result.remainder());
        }
        if (!result.trades().isEmpty()) {
            for (Trade trade : result.trades()) {
                trade.getBuy().getShareholder().incPosition(trade.getSecurity(), trade.getQuantity());
                trade.getSell().getShareholder().decPosition(trade.getSecurity(), trade.getQuantity());
            }
        }
        return result;
    }

}
