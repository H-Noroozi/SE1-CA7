package ir.ramtung.tinyme.domain.entity;

import lombok.Getter;

import java.util.*;
import static java.lang.Math.min;

@Getter
public class OrderBook {
    private final LinkedList<Order> buyQueue;
    private final LinkedList<Order> sellQueue;

    public OrderBook() {
        buyQueue = new LinkedList<>();
        sellQueue = new LinkedList<>();
    }

    public void enqueue(Order order) {
        List<Order> queue = getQueue(order.getSide());
        ListIterator<Order> it = queue.listIterator();
        while (it.hasNext()) {
            if (order.queuesBefore(it.next())) {
                it.previous();
                break;
            }
        }
        order.queue();
        it.add(order);
    }

    protected LinkedList<Order> getQueue(Side side) {
        return side == Side.BUY ? buyQueue : sellQueue;
    }

    public Order findByOrderId(Side side, long orderId) {
        var queue = getQueue(side);
        for (Order order : queue) {
            if (order.getOrderId() == orderId)
                return order;
        }
        return null;
    }

    public boolean removeByOrderId(Side side, long orderId) {
        var queue = getQueue(side);
        var it = queue.listIterator();
        while (it.hasNext()) {
            if (it.next().getOrderId() == orderId) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    public Order matchWithFirst(Order newOrder) {
        var queue = getQueue(newOrder.getSide().opposite());
        if (newOrder.matches(queue.getFirst()))
            return queue.getFirst();
        else
            return null;
    }

    public void putBack(Order order) {
        LinkedList<Order> queue = getQueue(order.getSide());
        order.queue();
        queue.addFirst(order);
    }

    public void restoreOrder(Order order) {
        removeByOrderId(order.getSide(), order.getOrderId());
        putBack(order);
    }

    public boolean hasOrderOfType(Side side) {
        return !getQueue(side).isEmpty();
    }

    public void removeFirst(Side side) {
        getQueue(side).removeFirst();
    }

    public int totalSellQuantityByShareholder(Shareholder shareholder) {
        return sellQueue.stream()
                .filter(order -> order.getShareholder().equals(shareholder))
                .mapToInt(Order::getTotalQuantity)
                .sum();
    }

    //int sellQuantity = sellQueue.stream().mapToInt(Order::getTotalQuantity).sum();
    public List<OpeningData> findPriceBasedOnMaxTransaction() {
        List<OpeningData> possiblePrices = new ArrayList<OpeningData>();
        int maxTradeQuantity = 0;
        int sellQuantity = 0, buyQuantity = 0;
        ListIterator<Order> buyQueueIt = buyQueue.listIterator();
        ListIterator<Order> sellQueueIt = sellQueue.listIterator();
        while (sellQueueIt.hasNext()) {
            Order order = sellQueueIt.next();
            sellQuantity += order.getTotalQuantity();
        }
        while (sellQueueIt.hasPrevious()) {
            Order sellOrder = sellQueueIt.previous();
            while (buyQueueIt.hasNext()) {
                Order buyOrder = buyQueueIt.next();
                if (sellOrder.getPrice() <= buyOrder.getPrice()) {
                    buyQuantity += buyOrder.getQuantity();
                }
                else
                    break;
            }
            if (min(sellQuantity, buyQuantity) > maxTradeQuantity) {
                maxTradeQuantity = min(sellQuantity, buyQuantity);
                possiblePrices.clear();
                possiblePrices.add(new OpeningData(sellOrder.getPrice(), maxTradeQuantity));
            }
            else if (min(sellQuantity, buyQuantity) == sellOrder.getPrice())
                possiblePrices.add(new OpeningData(sellOrder.getPrice(), maxTradeQuantity));
            sellQuantity -= sellOrder.getQuantity();
        }
        return possiblePrices;
    }

}
