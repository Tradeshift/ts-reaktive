package com.tradeshift.reaktive.json;

import io.vavr.collection.Seq;
import io.vavr.control.Option;

public class DTO1 {
    private final long l;
    private final Option<Integer> i;
    private final Seq<String> s;
    
    public DTO1(long l, Option<Integer> i, Seq<String> s) {
        this.l = l;
        this.i = i;
        this.s = s;
    }
    
    public long getL() {
        return l;
    }
    
    public Option<Integer> getI() {
        return i;
    }
    
    public Seq<String> getS() {
        return s;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((i == null) ? 0 : i.hashCode());
        result = prime * result + (int) (l ^ (l >>> 32));
        result = prime * result + ((s == null) ? 0 : s.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        DTO1 other = (DTO1) obj;
        if (i == null) {
            if (other.i != null)
                return false;
        } else if (!i.equals(other.i))
            return false;
        if (l != other.l)
            return false;
        if (s == null) {
            if (other.s != null)
                return false;
        } else if (!s.equals(other.s))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "DTO1 [l=" + l + ", i=" + i + ", s=" + s + "]";
    }
    
    
}
