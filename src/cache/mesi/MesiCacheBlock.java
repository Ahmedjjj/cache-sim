package cache.mesi;

import bus.BusEvent;
import cache.CacheBlock;

public final class MesiCacheBlock extends CacheBlock {

    private MesiState mesiState;

    public MesiCacheBlock(int size) {
        super(size);
        mesiState = MesiState.INVALID;
    }

    public MesiState getMesiState() {
        return mesiState;
    }

    public void setMesiState(MesiState mesiState) {
        this.mesiState = mesiState;
    }

    @Override
    public void update(BusEvent busEvent) {
    }

}
