package ir.ramtung.tinyme.domain;


import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.domain.service.OrderHandler;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
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

@SpringBootTest
@Import(MockedJMSTestConfig.class)
@DirtiesContext
public class StopLimitTest {

    private Security security;
    private Broker broker;
    private Shareholder shareholder;
    @Autowired
    Matcher matcher;

    @BeforeEach
    void setupOrderBook() {
        security = Security.builder().build();
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
}
