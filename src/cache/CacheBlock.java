package cache;

public abstract class CacheBlock {

    private final int size;

    private int tag;

    public CacheBlock(int size) {
        this.size = size;
    }

    public int getTag() {
        return tag;
    }

    public void setTag(int tag) {
        this.tag = tag;
    }

}
