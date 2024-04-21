package ir.ramtung.tinyme.domain.entity;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class StopLimitOrder extends Order{

    protected int StopPrice;

    StopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder, LocalDateTime entryTime, OrderStatus status, int minimumExecutionQuantity, int stopPrice) {
        super(orderId, security, side, quantity, price, broker, shareholder, entryTime, status, minimumExecutionQuantity);
        this.StopPrice = stopPrice;
    }

    StopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder, LocalDateTime entryTime, int minimumExecutionQuantity, int stopPrice){
        this(orderId, security, side, quantity, price, broker, shareholder, entryTime, OrderStatus.NEW, minimumExecutionQuantity, stopPrice);
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
    public boolean checkIfActive(int lastTransactionPrice){
        if (side == Side.BUY)
            return StopPrice < lastTransactionPrice;
        else
            return StopPrice > lastTransactionPrice;

    }
}
