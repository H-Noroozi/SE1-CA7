package ir.ramtung.tinyme.domain.entity;

import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class StopLimitOrder extends Order{

    protected int StopPrice;
    protected long requestId;

    StopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder, LocalDateTime entryTime, OrderStatus status, int minimumExecutionQuantity, int stopPrice, long requestId) {
        super(orderId, security, side, quantity, price, broker, shareholder, entryTime, status, minimumExecutionQuantity);
        this.StopPrice = stopPrice;
        this.requestId = requestId;
    }

    StopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder, LocalDateTime entryTime, int minimumExecutionQuantity, int stopPrice, long requestId){
        this(orderId, security, side, quantity, price, broker, shareholder, entryTime, OrderStatus.NEW, minimumExecutionQuantity, stopPrice, requestId);
    }

    public StopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder, int minimumExecutionQuantity, int stopPrice, long requestId){
        this(orderId, security, side, quantity, price, broker, shareholder, LocalDateTime.now(), OrderStatus.NEW, minimumExecutionQuantity, stopPrice, requestId);
    }

    @Override
    public boolean queuesBefore(Order order) {
        StopLimitOrder stopLimitOrder = (StopLimitOrder) order;
        if (order.getSide() == Side.BUY) {
            return StopPrice < stopLimitOrder.StopPrice;
        } else {
            return StopPrice > stopLimitOrder.StopPrice;
        }
    }

    public boolean mustBeActive(int lastTransactionPrice){
        if (side == Side.BUY)
            return StopPrice < lastTransactionPrice;
        else
            return StopPrice > lastTransactionPrice;
    }

    @Override
    public void updateFromRequest(EnterOrderRq updateOrderRq) {
        quantity = updateOrderRq.getQuantity();
        price = updateOrderRq.getPrice();
        StopPrice = updateOrderRq.getStopPrice();
    }

    @Override
    public Order snapshot() {
        return new StopLimitOrder(orderId, security, side, quantity, price, broker, shareholder, entryTime, OrderStatus.SNAPSHOT, minimumExecutionQuantity, StopPrice, requestId);
    }

}
