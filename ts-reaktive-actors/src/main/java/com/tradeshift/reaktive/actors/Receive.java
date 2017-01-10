package com.tradeshift.reaktive.actors;

import static javaslang.control.Option.none;

import javaslang.Function1;
import javaslang.control.Option;
import javaslang.control.Try.CheckedRunnable;
import scala.PartialFunction;
import scala.runtime.AbstractPartialFunction;
import scala.runtime.BoxedUnit;

public class Receive {
    public static PartialFunction<Object,BoxedUnit> asReceive(Function1<Object,Option<BoxedUnit>> f) {
        return new AbstractPartialFunction<Object,BoxedUnit>() {
            private final ThreadLocal<Option<BoxedUnit>> last = new ThreadLocal<Option<BoxedUnit>>() {
                @Override
                protected Option<BoxedUnit> initialValue() {
                    return none();
                }
            };

            @Override
            public boolean isDefinedAt(Object arg0) {
                last.set(f.apply(arg0));
                return last.get().isDefined();
            }
            
            @Override
            public BoxedUnit apply(Object x) {
                Option<BoxedUnit> l = last.get();
                if (l.isDefined()) {
                    last.set(none());
                    return l.get();
                } else {
                    throw new IllegalArgumentException("Unexpected invocation of apply() without preceding isDefinedAt()");
                }
            }
        };
    }
    
    public static BoxedUnit run(CheckedRunnable r) {
        try {
            r.run();
            return BoxedUnit.UNIT;
        } catch (Throwable e) {
            throw new RuntimeException(e); // FIXME sneaky throw instead
        }
    }
}
