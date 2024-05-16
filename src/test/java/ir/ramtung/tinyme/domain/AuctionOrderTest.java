package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Arrays;
import java.util.List;

import static ir.ramtung.tinyme.domain.entity.Side.BUY;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
@DirtiesContext
public class AuctionOrderTest {
    private Security security;
    private Broker broker;
    private Shareholder shareholder;
    private OrderBook orderBook;
    private List<Order> orders;
    @Autowired
    private Matcher matcher;

    @BeforeEach
    void setupOrderBook() {
        security = Security.builder().lastTransactionPrice(12).build();
        broker = Broker.builder().credit(100_000_000L).build();
        shareholder = Shareholder.builder().build();
        shareholder.incPosition(security, 100_000);
        orderBook = security.getOrderBook();
        orders = Arrays.asList(
                new Order(1, security, BUY, 3, 15, broker, shareholder, 0),
                new Order(2, security, BUY, 4, 10, broker, shareholder, 0),
                new Order(3, security, BUY, 2, 50, broker, shareholder, 0),
                new Order(4, security, BUY, 5, 40, broker, shareholder, 0),
                new Order(5, security, BUY, 10, 10, broker, shareholder, 0),
                new Order(6, security, Side.SELL, 3, 25, broker, shareholder, 0),
                new Order(7, security, Side.SELL, 8, 15, broker, shareholder, 0),
                new Order(8, security, Side.SELL, 7, 5, broker, shareholder, 0),
                new Order(9, security, Side.SELL, 5, 1, broker, shareholder, 0),
                new Order(10, security, Side.SELL, 1, 30, broker, shareholder, 0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
    }

    @Test
    void new_sell_order_matches_completely_with_part_of_the_first_buy() {
        security.changeMatchingState(MatchingState.AUCTION);
        Order order = new Order(11, security, Side.SELL, 3, 15, broker, shareholder, 0);
        Trade trade = new Trade(security, 7, 3, orders.get(0), order);
        MatchResult result = matcher.match(order);
        assertThat(result.remainder().getQuantity()).isEqualTo(0);
        assertThat(result.trades()).containsExactly(trade);
        assertThat(security.getOrderBook().getBuyQueue().getFirst().getQuantity()).isEqualTo(204);
    }
}
