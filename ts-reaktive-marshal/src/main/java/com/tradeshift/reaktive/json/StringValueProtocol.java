package com.tradeshift.reaktive.json;

import com.tradeshift.reaktive.marshal.StringProtocol;

public class StringValueProtocol {
    /**
     * A JSON protocol that reads and writes strings.
     */
    public static StringProtocol<JSONEvent> INSTANCE = new StringProtocol<>(ValueProtocol.STRING, JSONProtocol.locator);
}
