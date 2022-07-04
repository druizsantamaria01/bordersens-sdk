package model;

public class Counter
{
    protected int num;

    public Counter(int num)
    {
        this.num = num;
    }

    public int get()
    {
        return this.num;
    }

    public void increment()
    {
        this.num++;
    }

    @Override
    public String toString()
    {
        return Integer.toString(this.num);
    }
}
