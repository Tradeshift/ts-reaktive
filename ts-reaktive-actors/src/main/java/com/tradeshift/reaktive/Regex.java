package com.tradeshift.reaktive;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javaslang.Tuple;
import javaslang.Tuple2;
import javaslang.control.Option;

/**
 * A regular expression with compile-time known capture groups 
 */
public abstract class Regex<T> {
    private final Pattern regex;
    
    /**
     * Returns a Regex for a regular expression with 1 capture group.
     */
    public static Regex<String> compile1(String regex) {
        return compile1(regex, 0);
    }
    
    /**
     * Returns a Regex for a regular expression with 1 capture group.
     */
    public static Regex<String> compile1(String regex, int flags) {
        return new Regex<String>(regex, flags) {
            @Override
            protected String extract(Matcher m) {
                return m.group(1);
            }
        };
    }
    
    /**
     * Returns a Regex for a regular expression with 2 capture groups.
     */
    public static Regex<Tuple2<String,String>> compile2(String regex) {
        return compile2(regex, 0);
    }
    
    /**
     * Returns a Regex for a regular expression with 2 capture groups.
     */
    public static Regex<Tuple2<String,String>> compile2(String regex, int flags) {
        return new Regex<Tuple2<String,String>>(regex, flags) {
            @Override
            protected Tuple2<String, String> extract(Matcher m) {
                return Tuple.of(m.group(1), m.group(2));
            }
        };
    }
    
    private Regex(String regex, int flags) {
        this.regex = Pattern.compile(regex, flags);
    }
    
    protected Regex() { // constructor for subclasses overriding match()
        this.regex = null;
    }

    /**
     * Returns an Option containing the value of the capture groups, or Option.none() if the 
     * regular expression did not match.
     */
    public Option<T> match(String source) {
        Matcher m = regex.matcher(source);
        if (m.matches()) {
            return Option.of(extract(m));
        } else {
            return Option.none();
        }        
    }
    
    /**
     * Returns a regex that transforms the returned type from T into U, using the supplied function.
     */
    public <U> Regex<U> map(Function<T,U> f) {
        final Regex<T> parent = this;
        return new Regex<U>() {
            @Override
            public Option<U> match(String source) {
                return parent.match(source).map(f);
            }

            @Override
            protected U extract(Matcher m) {
                return null;
            }
        };
    }
    
    /**
     * Returns a regex that transforms the returned type from T into {@code Option<U>} , using the supplied function.
     */
    public <U> Regex<U> flatMap(Function<T,Option<U>> f) {
        final Regex<T> parent = this;
        return new Regex<U>() {
            @Override
            public Option<U> match(String source) {
                return parent.match(source).flatMap(f);
            }

            @Override
            protected U extract(Matcher m) {
                return null;
            }
        };
    }
    
    protected abstract T extract(Matcher m);
}
