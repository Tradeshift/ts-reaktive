package com.tradeshift.reaktive.backup;

import java.time.Instant;

public class S3Entry {
    private final String key;
    private final Instant lastModified;
    private final long size;
    
    public S3Entry(String key, Instant lastModified, long size) {
        this.key = key;
        this.lastModified = lastModified;
        this.size = size;
    }
    
    public String getKey() {
        return key;
    }
    
    public Instant getLastModified() {
        return lastModified;
    }
    
    public long getSize() {
        return size;
    }

    @Override
    public String toString() {
        return "S3Entry [key=" + key + ", lastModified=" + lastModified + ", size=" + size + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((key == null) ? 0 : key.hashCode());
        result = prime * result + ((lastModified == null) ? 0 : lastModified.hashCode());
        result = prime * result + (int) (size ^ (size >>> 32));
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
        S3Entry other = (S3Entry) obj;
        if (key == null) {
            if (other.key != null)
                return false;
        } else if (!key.equals(other.key))
            return false;
        if (lastModified == null) {
            if (other.lastModified != null)
                return false;
        } else if (!lastModified.equals(other.lastModified))
            return false;
        if (size != other.size)
            return false;
        return true;
    }
}
