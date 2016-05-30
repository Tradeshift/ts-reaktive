package com.tradeshift.reaktive.assertj;

import java.io.PrintWriter;
import java.io.StringWriter;

public class Throwables {
    public static String getStackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        return sw.toString();         
    }
}
