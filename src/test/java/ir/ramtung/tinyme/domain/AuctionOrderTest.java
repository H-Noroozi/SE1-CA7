package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.domain.service.OrderHandler;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.TradeDTO;
import ir.ramtung.tinyme.messaging.event.OrderExecutedEvent;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static ir.ramtung.tinyme.domain.entity.Side.BUY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
@DirtiesContext
public class AuctionOrderTest {
    @Autowired
    OrderHandler orderHandler;
    @Autowired
    EventPublisher eventPublisher;
    @Autowired
    SecurityRepository securityRepository;
    @Autowired
    BrokerRepository brokerRepository;
    @Autowired
    ShareholderRepository shareholderRepository;
    private Security security;
    private Broker broker;
    private Broker buyerBroker;
    private Shareholder shareholder;
    private OrderBook orderBook;
    private List<Order> orders;
    @Autowired
    private Matcher matcher;

    @BeforeEach
    void setupOrderBook() {
        securityRepository.clear();
        brokerRepository.clear();
        shareholderRepository.clear();

        security = Security.builder().isin("ABC").lastTransactionPrice(-200).build();
        security.changeMatchingState(MatchingState.AUCTION);
        securityRepository.addSecurity(security);

        shareholder = Shareholder.builder().build();
        shareholder.incPosition(security, 100_000);
        shareholderRepository.addShareholder(shareholder);

        broker = Broker.builder().brokerId(1).credit(100_000_000L).build();
        buyerBroker = Broker.builder().brokerId(2).credit(200).build();
        brokerRepository.addBroker(broker);
        brokerRepository.addBroker(buyerBroker);

        orderBook = security.getOrderBook();
        orders = Arrays.asList(
                new Order(3, security, BUY, 3, 50, broker, shareholder, 0),
                new Order(6, security, Side.SELL, 3, 25, broker, shareholder, 0),
                new Order(7, security, Side.SELL, 18, 15, broker, shareholder, 0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
    }

    @Test
    void matcher_match_the_order_with_security_status_on_auctioned() {
        Order order = new Order(11, security, Side.SELL, 3, 15, broker, shareholder, 0);
        orderBook.enqueue(order);
        Trade trade = new Trade(security, 15, 3, orders.get(0), order);
        MatchResult result = matcher.execute(order);
        assertThat(result.remainder().getQuantity()).isEqualTo(0);
        assertThat(result.trades()).containsExactly(trade);
        assertThat(security.getOrderBook().hasOrderOfType(BUY)).isEqualTo(false);
    }

    @Test
    void buy_order_broker_credit_get_rolled_back_when_opening_price_is_less_than_order_price() {
        Order order = new Order(11, security, BUY, 3, 20, broker, shareholder, 0);
        orderBook.enqueue(order);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 11, LocalDateTime.now(), BUY, 3, 20, 2, shareholder.getShareholderId(), 0, 0, 0));
        Trade trade = new Trade(security, order.getPrice(), order.getQuantity(),
                orders.get(2), order);

        assertThat(buyerBroker.getCredit()).isEqualTo(155);
    }
}
