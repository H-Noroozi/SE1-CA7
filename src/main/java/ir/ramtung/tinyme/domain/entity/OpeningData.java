package ir.ramtung.tinyme.domain.entity;

import lombok.Getter;

@Getter
public class OpeningData {
    final private int openingPrice;
    final private int tradableQuantity;

    OpeningData(int openingPrice, int tradableQuantity) {
        this.openingPrice = openingPrice;
        this.tradableQuantity = tradableQuantity;
    }
}
