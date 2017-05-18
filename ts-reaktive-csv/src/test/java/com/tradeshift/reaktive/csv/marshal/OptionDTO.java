package com.tradeshift.reaktive.csv.marshal;

import javaslang.control.Option;

public class OptionDTO {
    private final String text;
    private final Option<Integer> optInt;
    private final Option<Long> optLong;

    public OptionDTO(String text, Option<Integer> optInt, Option<Long> optLong) {
        this.text = text;
        this.optInt = optInt;
        this.optLong = optLong;
    }

    public Option<Integer> getIntOption() {
        return optInt;
    }

    public String getText() {
        return text;
    }

    public Option<Long> getLongOption() {
        return optLong;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((optInt == null) ? 0 : optInt.hashCode());
        result = prime * result + ((optLong == null) ? 0 : optLong.hashCode());
        result = prime * result + ((text == null) ? 0 : text.hashCode());
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
        OptionDTO other = (OptionDTO) obj;
        if (optInt == null) {
            if (other.optInt != null)
                return false;
        } else if (!optInt.equals(other.optInt))
            return false;
        if (optLong == null) {
            if (other.optLong != null)
                return false;
        } else if (!optLong.equals(other.optLong))
            return false;
        if (text == null) {
            if (other.text != null)
                return false;
        } else if (!text.equals(other.text))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "OptionDTO [s=" + text + ", i=" + optInt + ", l=" + optLong + "]";
    }
}
