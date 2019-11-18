package cache.mesi;

import bus.BusEvent;
import bus.Request;
import cache.Cache;
import cache.instruction.CacheInstruction;

public final class MesiCache extends Cache {

    public MesiCache(int id, int cacheSize, int blockSize, int associativity) {
        super(id, cacheSize, blockSize, associativity);
        this.cacheBlocks = new MesiCacheBlock[numLines][associativity];
        for (int i = 0; i < numLines; i++) {
            for (int j = 0; j < associativity; j++) {
                cacheBlocks[i][j] = new MesiCacheBlock(blockSize);
            }
        }

    }

    public MesiCacheBlock getCacheBlock(int address) {
        //todo
        return null;
    }

    @Override
    public void notifyChange(Request processingRequest) {
        MesiCacheBlock mesiCacheBlock = this.getCacheBlock(processingRequest.getAddress());//might be null if it's not in this cache
        BusEvent busEvent= processingRequest.getBusEvent();
        if(mesiCacheBlock==null)
            return;
        if (processingRequest.getSenderId() == this.getId()) {//this cache sent the request, should transition

            switch (mesiCacheBlock.getMesiState()) {//shouldnt be null since it's this cache that generated the request for it
                case MODIFIED: //nothing happens if you read or write in modified!
                    break;
                case EXCLUSIVE://shuld the bus notify to move to modified on a RdX?, moves to M witout any Bus events
                    if (busEvent == BusEvent.BusRdX)
                        mesiCacheBlock.setMesiState(MesiState.MODIFIED);
                    break;
                case SHARED:
                    if (busEvent == BusEvent.BusRdX)
                        mesiCacheBlock.setMesiState(MesiState.MODIFIED);
                    break;
                case INVALID:
                    if (busEvent == BusEvent.BusRd) {
                        //checks if this block was present in other caches or it had to go to memory
                        mesiCacheBlock.setMesiState(this.getBus().askOthers(processingRequest) ? MesiState.SHARED : MesiState.EXCLUSIVE);
                    }
                    break;
            }
        } else {//some other cache send the request
            switch (mesiCacheBlock.getMesiState()){

                case MODIFIED:
                    if(busEvent==BusEvent.BusRd)
                        mesiCacheBlock.setMesiState(MesiState.SHARED);
                    else if(busEvent==BusEvent.BusRdX)
                        mesiCacheBlock.setMesiState(MesiState.INVALID);
                    break;
                case EXCLUSIVE:
                    if(busEvent==BusEvent.BusRd)
                        mesiCacheBlock.setMesiState(MesiState.SHARED);
                    else if(busEvent==BusEvent.BusRdX)
                        mesiCacheBlock.setMesiState(MesiState.INVALID);
                    break;
                case SHARED:
                    if(busEvent==BusEvent.BusRdX)
                        mesiCacheBlock.setMesiState(MesiState.INVALID);
                    break;
                case INVALID://stay there
                    break;
            }
        }
    }

    @Override
    public void ask(CacheInstruction instruction) {

    }
}
