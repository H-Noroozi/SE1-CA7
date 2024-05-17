package ir.ramtung.tinyme.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static java.lang.Math.abs;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public class OpeningRangeData {

    private int minOpeningPrice;
    private int maxOpeningPrice;
    private int tradableQuantity;

    public OpeningData findClosestPriceToLastTransaction(int lastTransactionPrice){
        if (lastTransactionPrice < minOpeningPrice)
            return new OpeningData(minOpeningPrice, tradableQuantity);
        else if (lastTransactionPrice > maxOpeningPrice)
            return new OpeningData(maxOpeningPrice, tradableQuantity);
        else
            return new OpeningData(lastTransactionPrice, tradableQuantity);
    }

}
