package ir.ramtung.tinyme.domain.entity;

import ir.ramtung.tinyme.messaging.TradeDTO;
import ir.ramtung.tinyme.messaging.event.OrderAcceptedEvent;
import ir.ramtung.tinyme.messaging.event.OrderActivatedEvent;
import ir.ramtung.tinyme.messaging.event.OrderExecutedEvent;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.messaging.Message;
import lombok.Setter;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.springframework.jms.core.JmsTemplate;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import ir.ramtung.tinyme.messaging.EventPublisher;

@Getter
@Builder
public class Security {
    private String isin;
    @Builder.Default
    private int tickSize = 1;
    @Builder.Default
    private int lotSize = 1;
    @Builder.Default
    private OrderBook orderBook = new OrderBook();
    @Builder.Default
    private InactiveOrderBook inactiveOrderBook = new InactiveOrderBook();
    @Builder.Default
    private int lastTransactionPrice = 0;
    @Builder.Default
    private LinkedList<Order> executableOrders = new LinkedList<>();
    @Setter
    private EventPublisher eventPublisher;


    public MatchResult newOrder(EnterOrderRq enterOrderRq, Broker broker, Shareholder shareholder, Matcher matcher) throws InvalidRequestException {
        if (enterOrderRq.getSide() == Side.SELL &&
                !shareholder.hasEnoughPositionsOn(this,
                orderBook.totalSellQuantityByShareholder(shareholder) + enterOrderRq.getQuantity()))
            return MatchResult.notEnoughPositions();
        Order order;
        if (enterOrderRq.getPeakSize() == 0 && enterOrderRq.getStopPrice() == 0)
            order = new Order(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder, enterOrderRq.getEntryTime(), enterOrderRq.getMinimumExecutionQuantity());
        else if (enterOrderRq.getPeakSize() != 0 && enterOrderRq.getStopPrice() == 0)
            order = new IcebergOrder(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                    enterOrderRq.getEntryTime(), enterOrderRq.getPeakSize(), enterOrderRq.getMinimumExecutionQuantity());
        else if (enterOrderRq.getStopPrice() != 0 && enterOrderRq.getPeakSize() == 0) {
            StopLimitOrder stopLimitOrder = new StopLimitOrder(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                    enterOrderRq.getEntryTime(), enterOrderRq.getMinimumExecutionQuantity(),
                    enterOrderRq.getStopPrice());
            if ( (stopLimitOrder.getSide() == Side.BUY && stopLimitOrder.getStopPrice() <= lastTransactionPrice) || (stopLimitOrder.getSide() == Side.SELL && stopLimitOrder.getStopPrice() >= lastTransactionPrice) ){
                order = stopLimitOrder;
                eventPublisher.publish(new OrderActivatedEvent(stopLimitOrder.orderId));
            }
            else{
                if (stopLimitOrder.getSide() == Side.BUY) {
                    if (!stopLimitOrder.getBroker().hasEnoughCredit(stopLimitOrder.getValue())) {
                        return MatchResult.notEnoughCredit();
                    }
                }
                inactiveOrderBook.enqueue(stopLimitOrder);
                return MatchResult.queuedAsInactiveOrder();
            }
        }
        else
            throw new InvalidRequestException(Message.ORDER_CANNOT_BE_BOTH_A_STOP_LIMIT_AND_AN_ICEBERG);
        MatchResult matchResult = matcher.execute(order);
        if((matchResult.outcome() == MatchingOutcome.EXECUTED) && (!matchResult.trades().isEmpty())) {
            lastTransactionPrice = matchResult.trades().getLast().getPrice();
        }
        return matchResult;
    }

    public void deleteOrder(DeleteOrderRq deleteOrderRq) throws InvalidRequestException {
        Order order = orderBook.findByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
        if (order == null)
            throw new InvalidRequestException(Message.ORDER_ID_NOT_FOUND);
        if (order.getSide() == Side.BUY)
            order.getBroker().increaseCreditBy(order.getValue());
        orderBook.removeByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
    }

    public MatchResult updateOrder(EnterOrderRq updateOrderRq, Matcher matcher) throws InvalidRequestException {
        Order order = orderBook.findByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
        if (order == null)
            throw new InvalidRequestException(Message.ORDER_ID_NOT_FOUND);
        if ((order instanceof IcebergOrder) && updateOrderRq.getPeakSize() == 0)
            throw new InvalidRequestException(Message.INVALID_PEAK_SIZE);
        if (!(order instanceof IcebergOrder) && updateOrderRq.getPeakSize() != 0)
            throw new InvalidRequestException(Message.CANNOT_SPECIFY_PEAK_SIZE_FOR_A_NON_ICEBERG_ORDER);
        if (order.getMinimumExecutionQuantity() != updateOrderRq.getMinimumExecutionQuantity())
            throw new InvalidRequestException(Message.CANNOT_CHANGE_MINIMUM_EXECUTION_QUANTITY);
        order.markAsUpdated();

        if (updateOrderRq.getSide() == Side.SELL &&
                !order.getShareholder().hasEnoughPositionsOn(this,
                orderBook.totalSellQuantityByShareholder(order.getShareholder()) - order.getQuantity() + updateOrderRq.getQuantity()))
            return MatchResult.notEnoughPositions();

        boolean losesPriority = order.isQuantityIncreased(updateOrderRq.getQuantity())
                || updateOrderRq.getPrice() != order.getPrice()
                || ((order instanceof IcebergOrder icebergOrder) && (icebergOrder.getPeakSize() < updateOrderRq.getPeakSize()));

        if (updateOrderRq.getSide() == Side.BUY) {
            order.getBroker().increaseCreditBy(order.getValue());
        }
        Order originalOrder = order.snapshot();
        order.updateFromRequest(updateOrderRq);
        if (!losesPriority) {
            if (updateOrderRq.getSide() == Side.BUY) {
                order.getBroker().decreaseCreditBy(order.getValue());
            }
            return MatchResult.executed(null, List.of());
        }
        else
            order.markAsNew();

        orderBook.removeByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
        MatchResult matchResult = matcher.execute(order);
        if (matchResult.outcome() != MatchingOutcome.EXECUTED) {
            orderBook.enqueue(originalOrder);
            if (updateOrderRq.getSide() == Side.BUY) {
                originalOrder.getBroker().decreaseCreditBy(originalOrder.getValue());
            }
        }
        else
            if(!matchResult.trades().isEmpty())
                lastTransactionPrice = matchResult.trades().getLast().getPrice();

        return matchResult;
    }

    public void checkExecutableOrders(MatchResult matchResult) {
        int previousTransactionPrice = lastTransactionPrice;
        lastTransactionPrice = matchResult.trades().getLast().getPrice();
        if (lastTransactionPrice == previousTransactionPrice) {
            return;
        }
        Side targetSide = (lastTransactionPrice - previousTransactionPrice) > 0 ? Side.BUY : Side.SELL;
        findExecutableOrders(targetSide);
    }

    public void findExecutableOrders(Side side){
        while (inactiveOrderBook.hasOrderOfType(side)){
            StopLimitOrder stopLimitOrder = inactiveOrderBook.checkFirstInactiveOrder(side, lastTransactionPrice);
            if(stopLimitOrder == null){
                return;
            }
            executableOrders.add(stopLimitOrder);
            eventPublisher.publish(new OrderActivatedEvent(stopLimitOrder.orderId));
            inactiveOrderBook.removeFirst(side);
        }
    }

    public LinkedList<MatchResult> runExecutableOrders(Matcher matcher){
        LinkedList<MatchResult> results = new LinkedList<>();
        while (!executableOrders.isEmpty()){
            long orderId = executableOrders.getFirst().getOrderId();
            MatchResult matchResult = matcher.execute(executableOrders.removeFirst());
            if (!matchResult.trades().isEmpty()) {
                checkExecutableOrders(matchResult);
                eventPublisher.publish(new OrderExecutedEvent(0, orderId, matchResult.trades().stream().map(TradeDTO::new).collect(Collectors.toList())));
            }
            results.add(matchResult);
        }
        return results;
    }
}


