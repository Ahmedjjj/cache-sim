package cache;

public abstract class CacheBlock {

    private int tag;
    private final int size;

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
