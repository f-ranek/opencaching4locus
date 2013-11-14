package org.bogus.domowygpx.utils;

public class Pair<T1, T2> implements java.io.Serializable, Cloneable
{
    private static final long serialVersionUID = 6096987212061912841L;

    public T1 first;
    public T2 second;

    public Pair()
    {
        
    }
    
    public Pair(T1 first, T2 second)
    {
        this.first = first;
        this.second = second;
    }

    public static <T1, T2> Pair<T1, T2> makePair(T1 first, T2 second)
    {
        return new Pair<T1, T2>(first, second);
    }
    
    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((first == null) ? 0 : first.hashCode());
        result = prime * result + ((second == null) ? 0 : second.hashCode());
        return result;
    }
    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (!(obj instanceof Pair))
            return false;
        Pair<?,?> other = (Pair<?,?>)obj;
        if (first == null) {
            if (other.first != null)
                return false;
        } else if (!first.equals(other.first))
            return false;
        if (second == null) {
            if (other.second != null)
                return false;
        } else if (!second.equals(other.second))
            return false;
        return true;
    }
    @Override
    public String toString()
    {
        return "Pair[first=" + first + ", second=" + second + "]";
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public Pair<T1, T2> clone()
    {
        try{
            return (Pair<T1, T2>)super.clone();
        }catch(CloneNotSupportedException cnse){
            // wont happen 
            throw new RuntimeException(cnse);
        }
    }
}
