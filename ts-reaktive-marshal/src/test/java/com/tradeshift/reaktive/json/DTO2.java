package com.tradeshift.reaktive.json;

import io.vavr.control.Option;

public class DTO2 {
    private final Option<DTO1> d;
    private final Option<Integer> i;

    public DTO2(Option<DTO1> d, Option<Integer> i) {
        this.d = d;
        this.i = i;
    }
    
    public Option<DTO1> getD() {
        return d;
    }
    
    public Option<Integer> getI() {
        return i;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((d == null) ? 0 : d.hashCode());
        result = prime * result + ((i == null) ? 0 : i.hashCode());
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
        DTO2 other = (DTO2) obj;
        if (d == null) {
            if (other.d != null)
                return false;
        } else if (!d.equals(other.d))
            return false;
        if (i == null) {
            if (other.i != null)
                return false;
        } else if (!i.equals(other.i))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "DTO2 [d=" + d + ", i=" + i + "]";
    }
    
    
}
