package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.TradeDTO;
import ir.ramtung.tinyme.messaging.event.*;
import ir.ramtung.tinyme.messaging.request.*;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderHandler {
    SecurityRepository securityRepository;
    BrokerRepository brokerRepository;
    ShareholderRepository shareholderRepository;
    EventPublisher eventPublisher;
    Matcher matcher;

    public OrderHandler(SecurityRepository securityRepository, BrokerRepository brokerRepository, ShareholderRepository shareholderRepository, EventPublisher eventPublisher, Matcher matcher) {
        this.securityRepository = securityRepository;
        this.brokerRepository = brokerRepository;
        this.shareholderRepository = shareholderRepository;
        this.eventPublisher = eventPublisher;
        this.matcher = matcher;
    }

    public void handleEnterOrder(EnterOrderRq enterOrderRq) {
        try {
            validateEnterOrderRq(enterOrderRq);

            Security security = securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());
            Broker broker = brokerRepository.findBrokerById(enterOrderRq.getBrokerId());
            Shareholder shareholder = shareholderRepository.findShareholderById(enterOrderRq.getShareholderId());

            MatchResult matchResult;
            if (enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER)
                matchResult = security.newOrder(enterOrderRq, broker, shareholder, matcher);
            else
                matchResult = security.updateOrder(enterOrderRq, matcher);

            if (matchResult.outcome() == MatchingOutcome.NOT_ENOUGH_CREDIT) {
                eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), List.of(Message.BUYER_HAS_NOT_ENOUGH_CREDIT)));
                return;
            }
            if (matchResult.outcome() == MatchingOutcome.NOT_ENOUGH_POSITIONS) {
                eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), List.of(Message.SELLER_HAS_NOT_ENOUGH_POSITIONS)));
                return;
            }
            if (matchResult.outcome() == MatchingOutcome.NOT_ENOUGH_INITIAL_TRANSACTION) {
                eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), List.of(Message.BROKER_HAS_NOT_ENOUGH_INITIAL_TRANSACTION)));
                return;
            }
            if (enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER) {
                eventPublisher.publish(new OrderAcceptedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId()));
                if (matchResult.outcome() != MatchingOutcome.QUEUED_AS_INACTIVE_ORDER && enterOrderRq.getStopPrice() != 0) {
                    eventPublisher.publish(new OrderActivatedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId()));
                }
            }
            else {
                eventPublisher.publish(new OrderUpdatedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId()));
                if ((enterOrderRq.getStopPrice() != 0) && (matchResult.remainder() != null)) {
                    eventPublisher.publish(new OrderActivatedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId()));
                }
            }
            if (matchResult.outcome() == MatchingOutcome.AUCTIONED) {
                    OpeningData openingData = security.findOpeningData();
                    eventPublisher.publish(new OpeningPriceEvent(security.getIsin(), openingData.getOpeningPrice(), openingData.getTradableQuantity()));
                    LinkedList<MatchResult> results = security.runAuctionedOrders(matcher);
                    for (MatchResult result : results) {
                        Order executedOrder = result.remainder();
                        if (!result.trades().isEmpty()){
                            eventPublisher.publish(new OrderExecutedEvent(69, executedOrder.getOrderId(), result.trades().stream().map(TradeDTO::new).collect(Collectors.toList())));
                        }
                    }
                    if (!results.isEmpty()){
                        security.checkExecutableOrders(results.get(0).trades().getLast().getPrice());
                        LinkedList<MatchResult> matchResults = security.enqueueExecutableOrders();
                        for (MatchResult result : matchResults) {
                            StopLimitOrder activatedOrder = (StopLimitOrder) result.remainder();
                            eventPublisher.publish(new OrderActivatedEvent(activatedOrder.getRequestId(), activatedOrder.getOrderId()));
                        }
                    }
            }
            if (!matchResult.trades().isEmpty()) {
                eventPublisher.publish(new OrderExecutedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), matchResult.trades().stream().map(TradeDTO::new).collect(Collectors.toList())));
                security.checkExecutableOrders(matchResult.trades().getLast().getPrice());
                LinkedList<MatchResult> results = security.runExecutableOrders(matcher);
                for (MatchResult result : results) {
                    StopLimitOrder executedOrder = (StopLimitOrder) result.remainder();
                    eventPublisher.publish(new OrderActivatedEvent(executedOrder.getRequestId(), executedOrder.getOrderId()));
                    if (!result.trades().isEmpty()){
                        eventPublisher.publish(new OrderExecutedEvent(executedOrder.getRequestId(), executedOrder.getOrderId(), result.trades().stream().map(TradeDTO::new).collect(Collectors.toList())));
                    }
                }
            }
        } catch (InvalidRequestException ex) {
            eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), ex.getReasons()));
        }
    }

    public void handleDeleteOrder(DeleteOrderRq deleteOrderRq) {
        try {
            validateDeleteOrderRq(deleteOrderRq);
            Security security = securityRepository.findSecurityByIsin(deleteOrderRq.getSecurityIsin());
            security.deleteOrder(deleteOrderRq);
            eventPublisher.publish(new OrderDeletedEvent(deleteOrderRq.getRequestId(), deleteOrderRq.getOrderId()));
        } catch (InvalidRequestException ex) {
            eventPublisher.publish(new OrderRejectedEvent(deleteOrderRq.getRequestId(), deleteOrderRq.getOrderId(), ex.getReasons()));
        }
    }

    public void handleChangeMatchingState(ChangeMatchingStateRq changeMatchingStateRq) {
        Security security = securityRepository.findSecurityByIsin(changeMatchingStateRq.getSecurityIsin());
        if (security == null){
            return;
            // It must change.
        }
        if (security.getState() == MatchingState.AUCTION){
            LinkedList<MatchResult> results = security.runAuctionedOrders(matcher);
            for (MatchResult result : results) {
                Order executedOrder = result.remainder();
                if (!result.trades().isEmpty()){
                    eventPublisher.publish(new OrderExecutedEvent(69, executedOrder.getOrderId(), result.trades().stream().map(TradeDTO::new).collect(Collectors.toList())));
                }
            }
            security.checkExecutableOrders(results.get(0).trades().getLast().getPrice());
            if (changeMatchingStateRq.getTargetState() == MatchingState.AUCTION){
                LinkedList<MatchResult> matchResults = security.enqueueExecutableOrders();
            }
            else {
                LinkedList<MatchResult> matchResults = security.runExecutableOrders(matcher);
            }
            for (MatchResult result : results) {
                StopLimitOrder executedOrder = (StopLimitOrder) result.remainder();
                eventPublisher.publish(new OrderActivatedEvent(executedOrder.getRequestId(), executedOrder.getOrderId()));
                if (!result.trades().isEmpty()){
                    eventPublisher.publish(new OrderExecutedEvent(executedOrder.getRequestId(), executedOrder.getOrderId(), result.trades().stream().map(TradeDTO::new).collect(Collectors.toList())));
                }
            }
        }
        security.changeMatchingState(changeMatchingStateRq.getTargetState());
        eventPublisher.publish(new SecurityStateChangedEvent(changeMatchingStateRq.getSecurityIsin(), changeMatchingStateRq.getTargetState()));

    }

    private void validateEnterOrderRq(EnterOrderRq enterOrderRq) throws InvalidRequestException {
        List<String> errors = new LinkedList<>();
        if (enterOrderRq.getOrderId() <= 0)
            errors.add(Message.INVALID_ORDER_ID);
        if (enterOrderRq.getQuantity() <= 0)
            errors.add(Message.ORDER_QUANTITY_NOT_POSITIVE);
        if (enterOrderRq.getPrice() <= 0)
            errors.add(Message.ORDER_PRICE_NOT_POSITIVE);
        if (enterOrderRq.getStopPrice() < 0)
            errors.add(Message.ORDER_STOP_PRICE_NEGATIVE);
        Security security = securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());
        if (security == null)
            errors.add(Message.UNKNOWN_SECURITY_ISIN);
        else {
            if (enterOrderRq.getQuantity() % security.getLotSize() != 0)
                errors.add(Message.QUANTITY_NOT_MULTIPLE_OF_LOT_SIZE);
            if (enterOrderRq.getPrice() % security.getTickSize() != 0)
                errors.add(Message.PRICE_NOT_MULTIPLE_OF_TICK_SIZE);
        }
        if (brokerRepository.findBrokerById(enterOrderRq.getBrokerId()) == null)
            errors.add(Message.UNKNOWN_BROKER_ID);
        if (shareholderRepository.findShareholderById(enterOrderRq.getShareholderId()) == null)
            errors.add(Message.UNKNOWN_SHAREHOLDER_ID);
        if (enterOrderRq.getPeakSize() < 0 || enterOrderRq.getPeakSize() >= enterOrderRq.getQuantity())
            errors.add(Message.INVALID_PEAK_SIZE);
        if (enterOrderRq.getMinimumExecutionQuantity() < 0){
            errors.add(Message.MINIMUM_EXECUTION_QUANTITY_NOT_POSITIVE);
        }
        if (enterOrderRq.getMinimumExecutionQuantity() > enterOrderRq.getQuantity()){
            errors.add(Message.INVALID_MINIMUM_EXECUTION_QUANTITY);
        }
        if (enterOrderRq.getStopPrice() != 0 && enterOrderRq.getMinimumExecutionQuantity() != 0) {
            errors.add(Message.CANNOT_SPECIFY_MINIMUM_EXECUTION_QUANTITY_FOR_A_STOP_LIMIT_ORDER);
        }
        if (!errors.isEmpty())
            throw new InvalidRequestException(errors);
    }

    private void validateDeleteOrderRq(DeleteOrderRq deleteOrderRq) throws InvalidRequestException {
        List<String> errors = new LinkedList<>();
        if (deleteOrderRq.getOrderId() <= 0)
            errors.add(Message.INVALID_ORDER_ID);
        if (securityRepository.findSecurityByIsin(deleteOrderRq.getSecurityIsin()) == null)
            errors.add(Message.UNKNOWN_SECURITY_ISIN);
        if (!errors.isEmpty())
            throw new InvalidRequestException(errors);
    }
}
