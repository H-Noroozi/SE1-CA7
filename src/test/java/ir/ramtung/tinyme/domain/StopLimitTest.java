package ir.ramtung.tinyme.domain;


import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.domain.service.OrderHandler;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.TradeDTO;
import ir.ramtung.tinyme.messaging.event.OrderAcceptedEvent;
import ir.ramtung.tinyme.messaging.event.OrderActivatedEvent;
import ir.ramtung.tinyme.messaging.event.OrderExecutedEvent;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
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
import static ir.ramtung.tinyme.domain.entity.Side.SELL;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
@DirtiesContext
public class StopLimitTest {

    private Security security;
    private Broker broker;
    private Shareholder shareholder;
    @Autowired
    Matcher matcher;
    @Autowired
    EventPublisher eventPublisher;

    @BeforeEach
    void setupOrderBook() {
        security = Security.builder().build();
        security.setEventPublisher(eventPublisher);
        broker = Broker.builder().brokerId(1).credit(1_000_000L).build();
        shareholder = Shareholder.builder().shareholderId(0).build();
        shareholder.incPosition(security, 100_000_000);
    }

    @Test
    void new_buy_order_gets_activated_when_stop_limit_is_less() {
        Order matchingSellOrder = new Order(2, security, Side.SELL, 3, 5, broker, shareholder, 0);
        security.getOrderBook().enqueue(matchingSellOrder);

        EnterOrderRq newOrderRq = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 3, 5, 1, shareholder.getShareholderId(), 0, 0, 0);

        assertThatNoException().isThrownBy(() -> security.newOrder(newOrderRq, broker, shareholder, matcher));

        EnterOrderRq stopLimitOrderRq = EnterOrderRq.createNewOrderRq(2, "ABC", 1, LocalDateTime.now(), BUY, 3, 5, 1, shareholder.getShareholderId(), 0, 0, 3);

        assertThatNoException().isThrownBy(() -> security.newOrder(stopLimitOrderRq, broker, shareholder, matcher));
        assertThat(security.getInactiveOrderBook().getBuyQueue()).isEmpty();
        assertThat(security.getLastTransactionPrice()).isEqualTo(5);
    }

    @Test
    void new_sell_order_gets_activated_when_stop_limit_is_less() {
        Order matchingSellOrder = new Order(2, security, Side.SELL, 3, 5, broker, shareholder, 0);
        security.getOrderBook().enqueue(matchingSellOrder);

        EnterOrderRq newOrderRq = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 3, 5, 1, shareholder.getShareholderId(), 0, 0, 0);

        assertThatNoException().isThrownBy(() -> security.newOrder(newOrderRq, broker, shareholder, matcher));

        EnterOrderRq stopLimitOrderRq = EnterOrderRq.createNewOrderRq(2, "ABC", 1, LocalDateTime.now(), SELL, 3, 5, 1, shareholder.getShareholderId(), 0, 0, 7);
        assertThatNoException().isThrownBy(() -> security.newOrder(stopLimitOrderRq, broker, shareholder, matcher));
        assertThat(security.getInactiveOrderBook().getSellQueue()).isEmpty();
        assertThat(security.getLastTransactionPrice()).isEqualTo(5);
    }

    @Test
    void new_buy_order_gets_activated_when_last_transaction_price_became_more_than_stop_limit() {
        Order matchingSellOrder = new Order(2, security, Side.SELL, 3, 5, broker, shareholder, 0);
        security.getOrderBook().enqueue(matchingSellOrder);

        EnterOrderRq newOrderRq = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 3, 5, 1, shareholder.getShareholderId(), 0, 0, 0);

        assertThatNoException().isThrownBy(() -> security.newOrder(newOrderRq, broker, shareholder, matcher));

        EnterOrderRq stopLimitOrderRq = EnterOrderRq.createNewOrderRq(2, "ABC", 1, LocalDateTime.now(), BUY, 3, 5, 1, shareholder.getShareholderId(), 0, 0, 7);

        assertThatNoException().isThrownBy(() -> security.newOrder(stopLimitOrderRq, broker, shareholder, matcher));
        assertThat(security.getInactiveOrderBook().getBuyQueue()).isNotEmpty();

        Order changingStopLimitSellOrder = new Order(3, security, Side.SELL, 3, 10, broker, shareholder, 0);
        security.getOrderBook().enqueue(changingStopLimitSellOrder);

        EnterOrderRq ChangingStopLimitnewOrderRq = EnterOrderRq.createNewOrderRq(3, "ABC", 1, LocalDateTime.now(), BUY, 3, 10, 1, shareholder.getShareholderId(), 0, 0, 0);

        assertThatNoException().isThrownBy(() -> security.newOrder(ChangingStopLimitnewOrderRq, broker, shareholder, matcher));

        security.findExecutableOrders(BUY);
        security.runExecutableOrders(matcher);

        assertThat(security.getInactiveOrderBook().getBuyQueue()).isEmpty();
        assertThat(security.getLastTransactionPrice()).isEqualTo(10);
    }

    @Test
    void new_sell_order_gets_activated_when_last_transaction_price_became_less_than_stop_limit() {
        Order matchingSellOrder = new Order(2, security, Side.SELL, 3, 10, broker, shareholder, 0);
        security.getOrderBook().enqueue(matchingSellOrder);

        EnterOrderRq newOrderRq = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 3, 10, 1, shareholder.getShareholderId(), 0, 0, 0);

        assertThatNoException().isThrownBy(() -> security.newOrder(newOrderRq, broker, shareholder, matcher));
        assertThat(security.getLastTransactionPrice()).isEqualTo(10);

        EnterOrderRq stopLimitOrderRq = EnterOrderRq.createNewOrderRq(2, "ABC", 1, LocalDateTime.now(), SELL, 3, 5, 1, shareholder.getShareholderId(), 0, 0, 7);
        assertThatNoException().isThrownBy(() -> security.newOrder(stopLimitOrderRq, broker, shareholder, matcher));
        assertThat(security.getInactiveOrderBook().getSellQueue()).isNotEmpty();

        Order changingStopLimitSellOrder = new Order(3, security, Side.SELL, 3, 5, broker, shareholder, 0);
        security.getOrderBook().enqueue(changingStopLimitSellOrder);

        EnterOrderRq ChangingStopLimitnewOrderRq = EnterOrderRq.createNewOrderRq(3, "ABC", 1, LocalDateTime.now(), BUY, 3, 5, 1, shareholder.getShareholderId(), 0, 0, 0);

        assertThatNoException().isThrownBy(() -> security.newOrder(ChangingStopLimitnewOrderRq, broker, shareholder, matcher));

        security.findExecutableOrders(SELL);
        security.runExecutableOrders(matcher);

        assertThat(security.getInactiveOrderBook().getSellQueue()).isEmpty();
        assertThat(security.getLastTransactionPrice()).isEqualTo(5);
    }

    @Test
    void two_new_buy_order_one_gets_activated_and_other_one_wont_when_last_transaction_price_is_between_their_stop_limit() {
        Order matchingSellOrder = new Order(2, security, Side.SELL, 3, 5, broker, shareholder, 0);
        security.getOrderBook().enqueue(matchingSellOrder);

        EnterOrderRq newOrderRq = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 3, 5, 1, shareholder.getShareholderId(), 0, 0, 0);

        assertThatNoException().isThrownBy(() -> security.newOrder(newOrderRq, broker, shareholder, matcher));

        EnterOrderRq stopLimitOrderRq1 = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 3, 5, 1, shareholder.getShareholderId(), 0, 0, 7);
        EnterOrderRq stopLimitOrderRq2 = EnterOrderRq.createNewOrderRq(2, "ABC", 2, LocalDateTime.now(), BUY, 3, 5, 1, shareholder.getShareholderId(), 0, 0, 9);

        assertThatNoException().isThrownBy(() -> security.newOrder(stopLimitOrderRq1, broker, shareholder, matcher));
        assertThatNoException().isThrownBy(() -> security.newOrder(stopLimitOrderRq2, broker, shareholder, matcher));
        assertThat(security.getInactiveOrderBook().getBuyQueue()).isNotEmpty();

        Order changingStopLimitSellOrder = new Order(3, security, Side.SELL, 3, 8, broker, shareholder, 0);
        security.getOrderBook().enqueue(changingStopLimitSellOrder);

        EnterOrderRq ChangingStopLimitnewOrderRq = EnterOrderRq.createNewOrderRq(3, "ABC", 1, LocalDateTime.now(), BUY, 3, 8, 1, shareholder.getShareholderId(), 0, 0, 0);

        assertThatNoException().isThrownBy(() -> security.newOrder(ChangingStopLimitnewOrderRq, broker, shareholder, matcher));

        security.findExecutableOrders(BUY);
        security.runExecutableOrders(matcher);

        assertThat(security.getInactiveOrderBook().getBuyQueue().get(0).getOrderId()).isEqualTo(2);
        assertThat(security.getLastTransactionPrice()).isEqualTo(8);
    }

    @Test
    void two_new_sell_order_one_gets_activated_and_other_one_wont_when_last_transaction_price_is_between_their_stop_limit() {
        Order matchingSellOrder = new Order(2, security, Side.SELL, 3, 10, broker, shareholder, 0);
        security.getOrderBook().enqueue(matchingSellOrder);

        EnterOrderRq newOrderRq = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 3, 10, 1, shareholder.getShareholderId(), 0, 0, 0);

        assertThatNoException().isThrownBy(() -> security.newOrder(newOrderRq, broker, shareholder, matcher));

        EnterOrderRq stopLimitOrderRq1 = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), SELL, 3, 5, 1, shareholder.getShareholderId(), 0, 0, 9);
        EnterOrderRq stopLimitOrderRq2 = EnterOrderRq.createNewOrderRq(2, "ABC", 2, LocalDateTime.now(), SELL, 3, 5, 1, shareholder.getShareholderId(), 0, 0, 7);

        assertThatNoException().isThrownBy(() -> security.newOrder(stopLimitOrderRq1, broker, shareholder, matcher));
        assertThatNoException().isThrownBy(() -> security.newOrder(stopLimitOrderRq2, broker, shareholder, matcher));
        assertThat(security.getInactiveOrderBook().getSellQueue()).isNotEmpty();

        Order changingStopLimitSellOrder = new Order(3, security, Side.SELL, 3, 8, broker, shareholder, 0);
        security.getOrderBook().enqueue(changingStopLimitSellOrder);

        EnterOrderRq ChangingStopLimitnewOrderRq = EnterOrderRq.createNewOrderRq(3, "ABC", 1, LocalDateTime.now(), BUY, 3, 8, 1, shareholder.getShareholderId(), 0, 0, 0);

        assertThatNoException().isThrownBy(() -> security.newOrder(ChangingStopLimitnewOrderRq, broker, shareholder, matcher));

        security.findExecutableOrders(SELL);
        security.runExecutableOrders(matcher);

        assertThat(security.getInactiveOrderBook().getSellQueue().get(0).getOrderId()).isEqualTo(2);
        assertThat(security.getLastTransactionPrice()).isEqualTo(8);
    }

    @Test
    void two_new_buy_order_one_gets_executed_and_change_last_transaction_price_but_it_wont_effect_other_one() {
        Order matchingSellOrder = new Order(2, security, Side.SELL, 3, 1, broker, shareholder, 0);
        security.getOrderBook().enqueue(matchingSellOrder);

        EnterOrderRq newOrderRq = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 3, 1, 1, shareholder.getShareholderId(), 0, 0, 0);

        assertThatNoException().isThrownBy(() -> security.newOrder(newOrderRq, broker, shareholder, matcher));

        EnterOrderRq stopLimitOrderRq1 = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 3, 2, 1, shareholder.getShareholderId(), 0, 0, 7);
        EnterOrderRq stopLimitOrderRq2 = EnterOrderRq.createNewOrderRq(2, "ABC", 2, LocalDateTime.now(), BUY, 3, 5, 1, shareholder.getShareholderId(), 0, 0, 9);

        assertThatNoException().isThrownBy(() -> security.newOrder(stopLimitOrderRq1, broker, shareholder, matcher));
        assertThatNoException().isThrownBy(() -> security.newOrder(stopLimitOrderRq2, broker, shareholder, matcher));
        assertThat(security.getInactiveOrderBook().getBuyQueue()).isNotEmpty();

        Order changingStopLimitSellOrder = new Order(3, security, Side.SELL, 3, 10, broker, shareholder, 0);
        security.getOrderBook().enqueue(changingStopLimitSellOrder);

        EnterOrderRq ChangingStopLimitnewOrderRq = EnterOrderRq.createNewOrderRq(3, "ABC", 1, LocalDateTime.now(), BUY, 3, 10, 1, shareholder.getShareholderId(), 0, 0, 0);

        assertThatNoException().isThrownBy(() -> security.newOrder(ChangingStopLimitnewOrderRq, broker, shareholder, matcher));

        Order sellOrder = new Order(34, security, SELL, 3, 2, broker, shareholder, 0);
        security.getOrderBook().enqueue(sellOrder);

        security.findExecutableOrders(BUY);
        security.runExecutableOrders(matcher);

        assertThat(security.getInactiveOrderBook().getBuyQueue()).isEmpty();
        assertThat(security.getLastTransactionPrice()).isEqualTo(2);
    }

    @Test
    void two_new_sell_order_one_gets_executed_and_change_last_transaction_price_but_it_wont_effect_other_one() {
        Order matchingSellOrder = new Order(2, security, Side.SELL, 3, 10, broker, shareholder, 0);
        security.getOrderBook().enqueue(matchingSellOrder);

        EnterOrderRq newOrderRq = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 3, 10, 1, shareholder.getShareholderId(), 0, 0, 0);

        assertThatNoException().isThrownBy(() -> security.newOrder(newOrderRq, broker, shareholder, matcher));

        EnterOrderRq stopLimitOrderRq1 = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), SELL, 3, 15, 1, shareholder.getShareholderId(), 0, 0, 7);
        EnterOrderRq stopLimitOrderRq2 = EnterOrderRq.createNewOrderRq(2, "ABC", 2, LocalDateTime.now(), SELL, 3, 5, 1, shareholder.getShareholderId(), 0, 0, 9);

        assertThatNoException().isThrownBy(() -> security.newOrder(stopLimitOrderRq1, broker, shareholder, matcher));
        assertThatNoException().isThrownBy(() -> security.newOrder(stopLimitOrderRq2, broker, shareholder, matcher));
        assertThat(security.getInactiveOrderBook().getSellQueue()).isNotEmpty();

        Order changingStopLimitSellOrder = new Order(3, security, Side.SELL, 3, 5, broker, shareholder, 0);
        security.getOrderBook().enqueue(changingStopLimitSellOrder);

        EnterOrderRq ChangingStopLimitnewOrderRq = EnterOrderRq.createNewOrderRq(3, "ABC", 1, LocalDateTime.now(), BUY, 3, 5, 1, shareholder.getShareholderId(), 0, 0, 0);

        assertThatNoException().isThrownBy(() -> security.newOrder(ChangingStopLimitnewOrderRq, broker, shareholder, matcher));

        Order incomingBuyOrder = new Order(34, security, BUY, 3, 15, broker, shareholder, 0);
        security.getOrderBook().enqueue(incomingBuyOrder);

        security.findExecutableOrders(SELL);
        security.runExecutableOrders(matcher);

        assertThat(security.getInactiveOrderBook().getSellQueue()).isEmpty();
        assertThat(security.getLastTransactionPrice()).isEqualTo(15);
    }

    @Test
    void two_new_buy_order_one_gets_executed_and_change_last_transaction_price_and_make_other_one_get_activated() {
        Order matchingSellOrder = new Order(2, security, Side.SELL, 3, 1, broker, shareholder, 0);
        security.getOrderBook().enqueue(matchingSellOrder);

        EnterOrderRq newOrderRq = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 3, 1, 1, shareholder.getShareholderId(), 0, 0, 0);

        assertThatNoException().isThrownBy(() -> security.newOrder(newOrderRq, broker, shareholder, matcher));

        EnterOrderRq stopLimitOrderRq1 = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 3, 10, 1, shareholder.getShareholderId(), 0, 0, 5);
        EnterOrderRq stopLimitOrderRq2 = EnterOrderRq.createNewOrderRq(2, "ABC", 2, LocalDateTime.now(), BUY, 3, 5, 1, shareholder.getShareholderId(), 0, 0, 9);

        assertThatNoException().isThrownBy(() -> security.newOrder(stopLimitOrderRq1, broker, shareholder, matcher));
        assertThatNoException().isThrownBy(() -> security.newOrder(stopLimitOrderRq2, broker, shareholder, matcher));
        assertThat(security.getInactiveOrderBook().getBuyQueue()).isNotEmpty();

        Order changingStopLimitSellOrder = new Order(3, security, Side.SELL, 3, 7, broker, shareholder, 0);
        security.getOrderBook().enqueue(changingStopLimitSellOrder);

        EnterOrderRq ChangingStopLimitnewOrderRq = EnterOrderRq.createNewOrderRq(3, "ABC", 1, LocalDateTime.now(), BUY, 3, 7, 1, shareholder.getShareholderId(), 0, 0, 0);

        assertThatNoException().isThrownBy(() -> security.newOrder(ChangingStopLimitnewOrderRq, broker, shareholder, matcher));

        Order sellOrder = new Order(34, security, SELL, 3, 10, broker, shareholder, 0);
        security.getOrderBook().enqueue(sellOrder);

        security.findExecutableOrders(BUY);
        security.runExecutableOrders(matcher);

        assertThat(security.getInactiveOrderBook().getBuyQueue()).isEmpty();
        assertThat(security.getLastTransactionPrice()).isEqualTo(10);
    }

    @Test
    void two_new_sell_order_one_gets_executed_and_change_last_transaction_price_and_make_other_one_get_activated() {
        Order matchingSellOrder = new Order(2, security, Side.SELL, 3, 10, broker, shareholder, 0);
        security.getOrderBook().enqueue(matchingSellOrder);

        EnterOrderRq newOrderRq = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 3, 10, 1, shareholder.getShareholderId(), 0, 0, 0);

        assertThatNoException().isThrownBy(() -> security.newOrder(newOrderRq, broker, shareholder, matcher));

        EnterOrderRq stopLimitOrderRq1 = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), SELL, 3, 2, 1, shareholder.getShareholderId(), 0, 0, 8);
        EnterOrderRq stopLimitOrderRq2 = EnterOrderRq.createNewOrderRq(2, "ABC", 2, LocalDateTime.now(), SELL, 3, 5, 1, shareholder.getShareholderId(), 0, 0, 3);

        assertThatNoException().isThrownBy(() -> security.newOrder(stopLimitOrderRq1, broker, shareholder, matcher));
        assertThatNoException().isThrownBy(() -> security.newOrder(stopLimitOrderRq2, broker, shareholder, matcher));
        assertThat(security.getInactiveOrderBook().getSellQueue()).isNotEmpty();

        Order changingStopLimitSellOrder = new Order(3, security, Side.SELL, 3, 5, broker, shareholder, 0);
        security.getOrderBook().enqueue(changingStopLimitSellOrder);

        EnterOrderRq ChangingStopLimitnewOrderRq = EnterOrderRq.createNewOrderRq(3, "ABC", 1, LocalDateTime.now(), BUY, 3, 5, 1, shareholder.getShareholderId(), 0, 0, 0);

        assertThatNoException().isThrownBy(() -> security.newOrder(ChangingStopLimitnewOrderRq, broker, shareholder, matcher));

        Order incomingBuyOrder = new Order(34, security, BUY, 3, 2, broker, shareholder, 0);
        security.getOrderBook().enqueue(incomingBuyOrder);

        security.findExecutableOrders(SELL);
        security.runExecutableOrders(matcher);

        assertThat(security.getInactiveOrderBook().getSellQueue()).isEmpty();
        assertThat(security.getLastTransactionPrice()).isEqualTo(2);
    }

    @Test
    void new_buy_order_gets_executed_and_make_a_new_sell_order_activated() {
        Order matchingSellOrder = new Order(2, security, Side.SELL, 3, 4, broker, shareholder, 0);
        security.getOrderBook().enqueue(matchingSellOrder);

        EnterOrderRq newOrderRq = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 3, 4, 1, shareholder.getShareholderId(), 0, 0, 0);

        assertThatNoException().isThrownBy(() -> security.newOrder(newOrderRq, broker, shareholder, matcher));

        EnterOrderRq stopLimitOrderRq1 = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 3, 2, 1, shareholder.getShareholderId(), 0, 0, 5);
        EnterOrderRq stopLimitOrderRq2 = EnterOrderRq.createNewOrderRq(2, "ABC", 2, LocalDateTime.now(), SELL, 3, 5, 1, shareholder.getShareholderId(), 0, 0, 3);

        assertThatNoException().isThrownBy(() -> security.newOrder(stopLimitOrderRq1, broker, shareholder, matcher));
        assertThatNoException().isThrownBy(() -> security.newOrder(stopLimitOrderRq2, broker, shareholder, matcher));
        assertThat(security.getInactiveOrderBook().getBuyQueue()).isNotEmpty();
        assertThat(security.getInactiveOrderBook().getSellQueue()).isNotEmpty();

        Order changingStopLimitSellOrder = new Order(3, security, Side.SELL, 3, 7, broker, shareholder, 0);
        security.getOrderBook().enqueue(changingStopLimitSellOrder);

        EnterOrderRq ChangingStopLimitnewOrderRq = EnterOrderRq.createNewOrderRq(3, "ABC", 1, LocalDateTime.now(), BUY, 3, 7, 1, shareholder.getShareholderId(), 0, 0, 0);

        assertThatNoException().isThrownBy(() -> security.newOrder(ChangingStopLimitnewOrderRq, broker, shareholder, matcher));

        Order sellOrder = new Order(34, security, SELL, 3, 2, broker, shareholder, 0);
        security.getOrderBook().enqueue(sellOrder);

        security.findExecutableOrders(BUY);
        security.runExecutableOrders(matcher);
        security.findExecutableOrders(SELL);
        security.runExecutableOrders(matcher);

        assertThat(security.getInactiveOrderBook().getBuyQueue()).isEmpty();
        assertThat(security.getInactiveOrderBook().getSellQueue()).isEmpty();
        assertThat(security.getLastTransactionPrice()).isEqualTo(2);
    }

    @Test
    void new_sell_order_gets_executed_and_make_a_new_buy_order_activated() {
        Order matchingSellOrder = new Order(2, security, Side.SELL, 3, 4, broker, shareholder, 0);
        security.getOrderBook().enqueue(matchingSellOrder);

        EnterOrderRq newOrderRq = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 3, 4, 1, shareholder.getShareholderId(), 0, 0, 0);

        assertThatNoException().isThrownBy(() -> security.newOrder(newOrderRq, broker, shareholder, matcher));

        EnterOrderRq stopLimitOrderRq1 = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 3, 2, 1, shareholder.getShareholderId(), 0, 0, 5);
        EnterOrderRq stopLimitOrderRq2 = EnterOrderRq.createNewOrderRq(2, "ABC", 2, LocalDateTime.now(), SELL, 3, 6, 1, shareholder.getShareholderId(), 0, 0, 3);

        assertThatNoException().isThrownBy(() -> security.newOrder(stopLimitOrderRq1, broker, shareholder, matcher));
        assertThatNoException().isThrownBy(() -> security.newOrder(stopLimitOrderRq2, broker, shareholder, matcher));
        assertThat(security.getInactiveOrderBook().getBuyQueue()).isNotEmpty();
        assertThat(security.getInactiveOrderBook().getSellQueue()).isNotEmpty();

        Order changingStopLimitSellOrder = new Order(3, security, Side.SELL, 3, 2, broker, shareholder, 0);
        security.getOrderBook().enqueue(changingStopLimitSellOrder);

        EnterOrderRq ChangingStopLimitnewOrderRq = EnterOrderRq.createNewOrderRq(3, "ABC", 1, LocalDateTime.now(), BUY, 3, 2, 1, shareholder.getShareholderId(), 0, 0, 0);

        assertThatNoException().isThrownBy(() -> security.newOrder(ChangingStopLimitnewOrderRq, broker, shareholder, matcher));

        Order buyOrder = new Order(34, security, BUY, 3, 6, broker, shareholder, 0);
        security.getOrderBook().enqueue(buyOrder);

        security.findExecutableOrders(SELL);
        security.runExecutableOrders(matcher);
        security.findExecutableOrders(BUY);
        security.runExecutableOrders(matcher);

        assertThat(security.getInactiveOrderBook().getBuyQueue()).isEmpty();
        assertThat(security.getInactiveOrderBook().getSellQueue()).isEmpty();
        assertThat(security.getLastTransactionPrice()).isEqualTo(6);
    }

    @Test
    void new_order_with_both_peak_size_and_stop_limit_fails() {
        EnterOrderRq newOrderRq = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 3, 2, 1, shareholder.getShareholderId(), 1, 0, 5);
        assertThatExceptionOfType(InvalidRequestException.class).isThrownBy(() -> security.updateOrder(newOrderRq, matcher));
    }

    @Test
    void new_order_without_enough_credit_fails() {
        EnterOrderRq newOrderRq = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 3, 2000000, 1, shareholder.getShareholderId(), 1, 0, 5);
        assertThatExceptionOfType(InvalidRequestException.class).isThrownBy(() -> security.updateOrder(newOrderRq, matcher));
    }

    @Test
    void new_order_without_enough_position_fails() {
        shareholder.decPosition(security, 999_999);
        EnterOrderRq newOrderRq = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 3, 2000000, 1, shareholder.getShareholderId(), 1, 0, 5);
        assertThatExceptionOfType(InvalidRequestException.class).isThrownBy(() -> security.updateOrder(newOrderRq, matcher));
    }

    @Test
    void event_publisher_publish_activated_order_when_order_get_activated_when_its_published() {
        Order matchingSellOrder = new Order(2, security, Side.SELL, 3, 5, broker, shareholder, 0);
        security.getOrderBook().enqueue(matchingSellOrder);

        EnterOrderRq newOrderRq = EnterOrderRq.createNewOrderRq(1, "ABC", 3, LocalDateTime.now(), BUY, 3, 5, 1, shareholder.getShareholderId(), 0, 0, 0);

        assertThatNoException().isThrownBy(() -> security.newOrder(newOrderRq, broker, shareholder, matcher));

        EnterOrderRq stopLimitOrderRq = EnterOrderRq.createNewOrderRq(2, "ABC", 1, LocalDateTime.now(), BUY, 3, 5, 1, shareholder.getShareholderId(), 0, 0, 3);

        assertThatNoException().isThrownBy(() -> security.newOrder(stopLimitOrderRq, broker, shareholder, matcher));
        verify(eventPublisher).publish(new OrderActivatedEvent(1));
    }

    @Test
    void event_publisher_publish_activated_order_when_order_get_activated_when_its_stop_limit_is_met() {
        Order matchingSellOrder = new Order(2, security, Side.SELL, 3, 5, broker, shareholder, 0);
        security.getOrderBook().enqueue(matchingSellOrder);

        EnterOrderRq newOrderRq = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 3, 5, 1, shareholder.getShareholderId(), 0, 0, 0);

        assertThatNoException().isThrownBy(() -> security.newOrder(newOrderRq, broker, shareholder, matcher));

        EnterOrderRq stopLimitOrderRq = EnterOrderRq.createNewOrderRq(2, "ABC", 55, LocalDateTime.now(), BUY, 3, 5, 1, shareholder.getShareholderId(), 0, 0, 7);

        assertThatNoException().isThrownBy(() -> security.newOrder(stopLimitOrderRq, broker, shareholder, matcher));

        Order changingStopLimitSellOrder = new Order(3, security, Side.SELL, 3, 10, broker, shareholder, 0);
        security.getOrderBook().enqueue(changingStopLimitSellOrder);

        EnterOrderRq ChangingStopLimitnewOrderRq = EnterOrderRq.createNewOrderRq(3, "ABC", 1, LocalDateTime.now(), BUY, 3, 10, 1, shareholder.getShareholderId(), 0, 0, 0);

        assertThatNoException().isThrownBy(() -> security.newOrder(ChangingStopLimitnewOrderRq, broker, shareholder, matcher));

        security.findExecutableOrders(BUY);
        security.runExecutableOrders(matcher);

        verify(eventPublisher).publish(new OrderActivatedEvent(55));
    }

    @Test
    void event_publisher_publish_executed_order_when_order_get_executed() {
        Order matchingSellOrder = new Order(2, security, Side.SELL, 3, 1, broker, shareholder, 0);
        security.getOrderBook().enqueue(matchingSellOrder);

        EnterOrderRq newOrderRq = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 3, 1, 1, shareholder.getShareholderId(), 0, 0, 0);

        assertThatNoException().isThrownBy(() -> security.newOrder(newOrderRq, broker, shareholder, matcher));

        StopLimitOrder buyStopLimitOrder = new StopLimitOrder(55, security, BUY,3 , 10, broker, shareholder, 0, 5);

        EnterOrderRq stopLimitOrderRq = EnterOrderRq.createNewOrderRq(1, "ABC", 55, LocalDateTime.now(), BUY, 3, 10, 1, shareholder.getShareholderId(), 0, 0, 5);

        assertThatNoException().isThrownBy(() -> security.newOrder(stopLimitOrderRq, broker, shareholder, matcher));
        assertThat(security.getInactiveOrderBook().getBuyQueue()).isNotEmpty();

        Order changingStopLimitSellOrder = new Order(3, security, Side.SELL, 3, 7, broker, shareholder, 0);
        security.getOrderBook().enqueue(changingStopLimitSellOrder);

        EnterOrderRq ChangingStopLimitnewOrderRq = EnterOrderRq.createNewOrderRq(3, "ABC", 4, LocalDateTime.now(), BUY, 3, 7, 1, shareholder.getShareholderId(), 0, 0, 0);

        assertThatNoException().isThrownBy(() -> security.newOrder(ChangingStopLimitnewOrderRq, broker, shareholder, matcher));

        Order sellOrder = new Order(34, security, SELL, 3, 10, broker, shareholder, 0);
        security.getOrderBook().enqueue(sellOrder);

        security.findExecutableOrders(BUY);
        security.runExecutableOrders(matcher);
        Trade trade = new Trade(security, sellOrder.getPrice(), sellOrder.getQuantity(),
                buyStopLimitOrder, sellOrder);

        verify(eventPublisher).publish(new OrderExecutedEvent(0, 55, List.of(new TradeDTO(trade))));
    }

    @Test
    void update_order_invalid_if_order_is_activated_and_we_give_stop_limit() {
        Order matchingSellOrder = new Order(2, security, Side.SELL, 3, 5, broker, shareholder, 0);
        security.getOrderBook().enqueue(matchingSellOrder);

        EnterOrderRq newOrderRq = EnterOrderRq.createNewOrderRq(1, "ABC", 3, LocalDateTime.now(), BUY, 3, 5, 1, shareholder.getShareholderId(), 0, 0, 0);

        assertThatNoException().isThrownBy(() -> security.newOrder(newOrderRq, broker, shareholder, matcher));

        EnterOrderRq stopLimitOrderRq = EnterOrderRq.createNewOrderRq(2, "ABC", 1, LocalDateTime.now(), BUY, 3, 5, 1, shareholder.getShareholderId(), 0, 0, 3);

        assertThatNoException().isThrownBy(() -> security.newOrder(stopLimitOrderRq, broker, shareholder, matcher));

        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(3, security.getIsin(), 1, LocalDateTime.now(), BUY, 2, 7, 1, shareholder.getShareholderId(), 0, 0, 2);
        assertThatExceptionOfType(InvalidRequestException.class).isThrownBy(() -> security.updateOrder(updateOrderRq, matcher));
    }

    @Test
    void update_order_if_order_is_not_activated_and_order_would_not_get_activated_after_update() {
        Order matchingSellOrder = new Order(2, security, Side.SELL, 3, 10, broker, shareholder, 0);
        security.getOrderBook().enqueue(matchingSellOrder);

        EnterOrderRq newOrderRq = EnterOrderRq.createNewOrderRq(1, "ABC", 3, LocalDateTime.now(), BUY, 3, 10, 1, shareholder.getShareholderId(), 0, 0, 0);

        assertThatNoException().isThrownBy(() -> security.newOrder(newOrderRq, broker, shareholder, matcher));

        EnterOrderRq stopLimitOrderRq = EnterOrderRq.createNewOrderRq(2, "ABC", 1, LocalDateTime.now(), BUY, 3, 5, 1, shareholder.getShareholderId(), 0, 0, 13);

        assertThatNoException().isThrownBy(() -> security.newOrder(stopLimitOrderRq, broker, shareholder, matcher));

        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(3, security.getIsin(), 1, LocalDateTime.now(), BUY, 3, 7, 1, shareholder.getShareholderId(), 0, 0, 12);
        assertThatNoException().isThrownBy(() -> security.updateOrder(updateOrderRq, matcher));
        assertThat(security.getInactiveOrderBook().getBuyQueue().get(0).getPrice()).isEqualTo(7);
    }

    @Test
    void update_order_if_order_is_not_activated_and_order_get_activated_after_update() {
        Order matchingSellOrder = new Order(2, security, Side.SELL, 3, 10, broker, shareholder, 0);
        security.getOrderBook().enqueue(matchingSellOrder);

        EnterOrderRq newOrderRq = EnterOrderRq.createNewOrderRq(1, "ABC", 3, LocalDateTime.now(), BUY, 3, 10, 1, shareholder.getShareholderId(), 0, 0, 0);

        assertThatNoException().isThrownBy(() -> security.newOrder(newOrderRq, broker, shareholder, matcher));

        EnterOrderRq stopLimitOrderRq = EnterOrderRq.createNewOrderRq(2, "ABC", 1, LocalDateTime.now(), BUY, 3, 5, 1, shareholder.getShareholderId(), 0, 0, 13);

        assertThatNoException().isThrownBy(() -> security.newOrder(stopLimitOrderRq, broker, shareholder, matcher));

        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(3, security.getIsin(), 1, LocalDateTime.now(), BUY, 3, 7, 1, shareholder.getShareholderId(), 0, 0, 3);
        assertThatNoException().isThrownBy(() -> security.updateOrder(updateOrderRq, matcher));

        assertThat(security.getInactiveOrderBook().getBuyQueue()).isEmpty();
    }

    @Test
    void delete_order_stop_limit_order() {
        Order matchingSellOrder = new Order(2, security, Side.SELL, 3, 10, broker, shareholder, 0);
        security.getOrderBook().enqueue(matchingSellOrder);

        EnterOrderRq newOrderRq = EnterOrderRq.createNewOrderRq(1, "ABC", 3, LocalDateTime.now(), BUY, 3, 10, 1, shareholder.getShareholderId(), 0, 0, 0);

        assertThatNoException().isThrownBy(() -> security.newOrder(newOrderRq, broker, shareholder, matcher));

        EnterOrderRq stopLimitOrderRq = EnterOrderRq.createNewOrderRq(2, "ABC", 1, LocalDateTime.now(), BUY, 3, 5, 1, shareholder.getShareholderId(), 0, 0, 13);

        assertThatNoException().isThrownBy(() -> security.newOrder(stopLimitOrderRq, broker, shareholder, matcher));

        DeleteOrderRq deleteOrderRq = new DeleteOrderRq(3, security.getIsin(), BUY, 1);
        assertThatNoException().isThrownBy(() -> security.deleteOrder(deleteOrderRq));

        assertThat(security.getInactiveOrderBook().getBuyQueue()).isEmpty();
    }
}
