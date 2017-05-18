package com.tradeshift.reaktive.csv.marshal;

import com.tradeshift.reaktive.csv.CsvEvent;
import com.tradeshift.reaktive.marshal.Locator;
import com.tradeshift.reaktive.marshal.StringProtocol;

/**
 * Decorates {@link ValueProtocol} with additional String conversion functions.
 */
public class StringValueProtocol extends StringProtocol<CsvEvent> {
    private static final Locator<CsvEvent> locator = event -> "???";
    
    public static final StringValueProtocol instance = new StringValueProtocol();

    private StringValueProtocol() {
        super(ValueProtocol.instance, locator);
    }
}
