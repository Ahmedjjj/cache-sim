package cache.dragon;

import cache.CacheBlock;

public class DragonCacheBlock extends CacheBlock {

    private DragonState state;

    public DragonCacheBlock(int size) {
        super(size);
        this.state = DragonState.NOT_IN_CACHE;
    }

    public DragonState getState() {
        return state;
    }

    public void setState(DragonState state) {
        this.state = state;
    }


}
