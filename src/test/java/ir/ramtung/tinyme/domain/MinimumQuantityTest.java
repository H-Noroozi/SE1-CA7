package ir.ramtung.tinyme.domain;
import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.domain.service.OrderHandler;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.TradeDTO;
import ir.ramtung.tinyme.messaging.event.OrderAcceptedEvent;
import ir.ramtung.tinyme.messaging.event.OrderExecutedEvent;
import ir.ramtung.tinyme.messaging.event.OrderRejectedEvent;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
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
import java.util.List;

import static ir.ramtung.tinyme.domain.entity.Side.BUY;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
@DirtiesContext
public class MinimumQuantityTest {
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
    private Shareholder shareholder;
    private Broker brokerBuyer;
    private Broker brokerSeller;
    @Autowired
    private Matcher matcher;

    @BeforeEach
    void setupOrderBook() {
        securityRepository.clear();
        brokerRepository.clear();
        shareholderRepository.clear();

        security = Security.builder().isin("ABC").build();
        securityRepository.addSecurity(security);

        shareholder = Shareholder.builder().build();
        shareholder.incPosition(security, 100_000);
        shareholderRepository.addShareholder(shareholder);

        brokerBuyer = Broker.builder().brokerId(1).credit(1000).build();
        brokerSeller = Broker.builder().brokerId(2).credit(1000).build();

        brokerRepository.addBroker(brokerBuyer);
        brokerRepository.addBroker(brokerSeller);
    }

    @Test
    void new_buy_order_matches_partially_with_the_first_buy_with_minimum_quantity_less_than_buy_quantity() {
        Order matchingBuyOrder = new Order(1, security, Side.BUY, 5, 5, brokerBuyer, shareholder, 2);
        Order incomingSellOrder = new Order(2, security, Side.SELL, 3, 5, brokerSeller, shareholder, 0);
        security.getOrderBook().enqueue(incomingSellOrder);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 5, 5, 1, shareholder.getShareholderId(), 0, 2));

        Trade trade = new Trade(security, 5, 3, matchingBuyOrder, incomingSellOrder);

        verify(eventPublisher).publish((new OrderAcceptedEvent(1, 1)));
        verify(eventPublisher).publish(new OrderExecutedEvent(1, 1, List.of(new TradeDTO(trade))));
    }
    @Test
    void invalid_buy_order_if_minimum_quantity_more_than_quantity() {
        Order matchingBuyOrder = new Order(1, security, Side.BUY, 5, 5, brokerBuyer, shareholder, 6);
        Order incomingSellOrder = new Order(2, security, Side.SELL, 3, 5, brokerSeller, shareholder, 0);
        security.getOrderBook().enqueue(incomingSellOrder);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 5, 5, 1, shareholder.getShareholderId(), 0, 6));

        Trade trade = new Trade(security, 5, 3, matchingBuyOrder, incomingSellOrder);

        verify(eventPublisher).publish(new OrderRejectedEvent(1, 1, List.of(Message.INVALID_MINIMUM_EXECUTION_QUANTITY)));
    }
    @Test
    void invalid_buy_order_if_minimum_quantity_less_than_zero() {
        Order matchingBuyOrder = new Order(1, security, Side.BUY, 5, 5, brokerBuyer, shareholder, -1);
        Order incomingSellOrder = new Order(2, security, Side.SELL, 3, 5, brokerSeller, shareholder, 0);
        security.getOrderBook().enqueue(incomingSellOrder);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 5, 5, 1, shareholder.getShareholderId(), 0, -1));

        Trade trade = new Trade(security, 5, 3, matchingBuyOrder, incomingSellOrder);

        verify(eventPublisher).publish(new OrderRejectedEvent(1, 1, List.of(Message.MINIMUM_EXECUTION_QUANTITY_NOT_POSITIVE)));
    }
    @Test
    void invalid_buy_order_if_minimum_quantity_more_than_buy_quantity() {
        Order matchingBuyOrder = new Order(1, security, Side.BUY, 5, 5, brokerBuyer, shareholder, 4);
        Order incomingSellOrder = new Order(2, security, Side.SELL, 3, 5, brokerSeller, shareholder, 0);
        security.getOrderBook().enqueue(incomingSellOrder);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 5, 5, 1, shareholder.getShareholderId(), 0, 4));

        verify(eventPublisher).publish(new OrderRejectedEvent(1, 1, List.of(Message.BROKER_HAS_NOT_ENOUGH_INITIAL_TRANSACTION)));
    }
    @Test
    void invalid_update_order_if_minimum_quantity_more_than_quantity() {
        Order matchingBuyOrder = new Order(1, security, Side.BUY, 5, 5, brokerBuyer, shareholder, 2);
        Order incomingSellOrder = new Order(2, security, Side.SELL, 3, 5, brokerSeller, shareholder, 0);
        security.getOrderBook().enqueue(incomingSellOrder);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 5, 5, 1, shareholder.getShareholderId(), 0, 2));

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 1, 5, 1, shareholder.getShareholderId(), 0, 2));

        verify(eventPublisher).publish(new OrderRejectedEvent(1, 1, List.of(Message.INVALID_MINIMUM_EXECUTION_QUANTITY)));
    }
    @Test
    void invalid_update_order_if_minimum_quantity_has_changed() {
        Order matchingBuyOrder = new Order(1, security, Side.BUY, 5, 5, brokerBuyer, shareholder, 2);
        Order incomingSellOrder = new Order(2, security, Side.SELL, 3, 5, brokerSeller, shareholder, 0);
        security.getOrderBook().enqueue(incomingSellOrder);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 5, 5, 1, shareholder.getShareholderId(), 0, 2));

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 5, 5, 1, shareholder.getShareholderId(), 0, 3));

        verify(eventPublisher).publish(new OrderRejectedEvent(1, 1, List.of(Message.CANNOT_CHANGE_MINIMUM_EXECUTION_QUANTITY)));
    }
    @Test
    void update_order_if_minimum_quantity_not_changed() {
        Order matchingBuyOrder = new Order(1, security, Side.BUY, 3, 5, brokerBuyer, shareholder, 2);
        Order incomingSellOrder = new Order(2, security, Side.SELL, 5, 5, brokerSeller, shareholder, 0);
        security.getOrderBook().enqueue(incomingSellOrder);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 3, 5, 1, shareholder.getShareholderId(), 0, 2));

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 1, 5, 1, shareholder.getShareholderId(), 0, 2));

        verify(eventPublisher).publish((new OrderAcceptedEvent(1, 1)));
    }
    @Test
    void new_iceberg_buy_order_matches_with_the_first_buy_with_minimum_quantity_less_than_buy_quantity() {
        Order matchingBuyOrder = new IcebergOrder(1, security, Side.BUY, 5, 5, brokerBuyer, shareholder, 40,2);
        Order incomingSellOrder = new IcebergOrder(2, security, Side.SELL, 3, 5, brokerSeller, shareholder, 40, 0);
        security.getOrderBook().enqueue(incomingSellOrder);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 5, 5, 1, shareholder.getShareholderId(), 0, 2));

        Trade trade = new Trade(security, 5, 3, matchingBuyOrder, incomingSellOrder);

        verify(eventPublisher).publish((new OrderAcceptedEvent(1, 1)));
        verify(eventPublisher).publish(new OrderExecutedEvent(1, 1, List.of(new TradeDTO(trade))));
    }
    @Test
    void invalid_iceberg_buy_order_if_minimum_quantity_more_than_buy_quantity() {
        Order matchingBuyOrder = new IcebergOrder(1, security, Side.BUY, 5, 5, brokerBuyer, shareholder, 40, 4);
        Order incomingSellOrder = new IcebergOrder(2, security, Side.SELL, 3, 5, brokerSeller, shareholder, 40, 0);
        security.getOrderBook().enqueue(incomingSellOrder);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 5, 5, 1, shareholder.getShareholderId(), 0, 4));

        verify(eventPublisher).publish(new OrderRejectedEvent(1, 1, List.of(Message.BROKER_HAS_NOT_ENOUGH_INITIAL_TRANSACTION)));
    }
}
