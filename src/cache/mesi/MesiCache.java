package cache.mesi;

import bus.BusEvent;
import bus.DataRequest;
import bus.Request;
import cache.Cache;
import cache.CacheBlock;
import cache.instruction.CacheInstruction;
import cache.instruction.CacheInstructionType;
import common.Constants;

public final class MesiCache extends Cache {
    private final MesiCacheBlock[][] cacheBlocks;
    private int cacheMiss;


    public MesiCache(int id, int cacheSize, int blockSize, int associativity) {
        super(id, cacheSize, blockSize, associativity);
        this.cacheBlocks = new MesiCacheBlock[numLines][associativity];
        for (int i = 0; i < numLines; i++) {
            for (int j = 0; j < associativity; j++) {
                cacheBlocks[i][j] = new MesiCacheBlock(blockSize);
            }
        }
        cacheMiss=0;

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
                case EXCLUSIVE:// moves to M witout any Bus events
                    break;
                case SHARED:
                    if (busEvent == BusEvent.BusRdX)
                        mesiCacheBlock.setMesiState(MesiState.MODIFIED);
                    break;
                case INVALID:
                    if (busEvent == BusEvent.BusRd) {
                        if(getBus().askOthers(getId(), processingRequest.getAddress())){
                            //checks if this block was present in other caches or it had to go to memory
                            mesiCacheBlock.setMesiState(MesiState.SHARED);
                        }else{
                            //getBus().fetchFromMemory(this.getId(), processingRequest.getAddress());
                            mesiCacheBlock.setMesiState(MesiState.EXCLUSIVE);
                        }
                    }
                    break;
            }
        } else {//some other cache send the request
            switch (mesiCacheBlock.getMesiState()){

                case MODIFIED:
                    if(busEvent==BusEvent.BusRd){
                        getBus().flushMemory(new DataRequest(this.getId(), BusEvent.Flush,processingRequest.getAddress(),Constants.MEMORY_LATENCY));
                        mesiCacheBlock.setMesiState(MesiState.SHARED);
                    }
                    else if(busEvent==BusEvent.BusRdX) {
                        getBus().flushMemory(new DataRequest(this.getId(), BusEvent.Flush,processingRequest.getAddress(),Constants.MEMORY_LATENCY));
                        mesiCacheBlock.setMesiState(MesiState.INVALID);
                    }
                    break;
                case EXCLUSIVE:
                    if(busEvent==BusEvent.BusRd) {
                        getBus().flushClean(new DataRequest(this.getId(),BusEvent.Flush, processingRequest.getAddress(), getBlockSize()/2));
                        mesiCacheBlock.setMesiState(MesiState.SHARED);
                    }
                    else if(busEvent==BusEvent.BusRdX) {
                        getBus().flushClean(new DataRequest(this.getId(), BusEvent.Flush, processingRequest.getAddress(), getBlockSize()/2));
                        mesiCacheBlock.setMesiState(MesiState.INVALID);
                    }
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

        MesiState state = getBlockState(instruction.getAddress());
        CacheInstructionType type = instruction.getCacheInstructionType();
        int address = instruction.getAddress();
        MesiCacheBlock cacheBlock= getCacheBlock(address);
        boolean sharedSignal= getBus().askOthers(id,address);

        switch (state){

            case INVALID: { // cache Miss
                cacheMiss++;
                if (type == CacheInstructionType.READ){
                    Request request = new Request(id,
                                      BusEvent.BusRd,address, Constants.BUS_MESSAGE_CYCLES);
                    getBus().addRequest(request);
                }else {
                    Request request = new Request(id,
                            BusEvent.BusRdX,address, Constants.BUS_MESSAGE_CYCLES);
                    getBus().addRequest(request);
                }
                break;
            }
            case MODIFIED:{
                break;
            }
            case SHARED:{
                if (type == CacheInstructionType.WRITE){
                    Request request = new Request(id,
                            BusEvent.BusRdX,address, Constants.BUS_MESSAGE_CYCLES);
                    getBus().addRequest(request);
                }
                break;
            }
            case EXCLUSIVE:{
                cacheBlock.setMesiState(MesiState.MODIFIED);
                break;
            }


        }

    }

    @Override
    public boolean cacheHit(int address) {
        return getBlockState(address)!=MesiState.INVALID;
    }


    private MesiState getBlockState (int address){
        MesiCacheBlock cacheBlock =getCacheBlock(address);
        return cacheBlock==null? MesiState.INVALID: cacheBlock.getMesiState();
    }

    protected MesiCacheBlock getCacheBlock(int address) {
        int tag = super.getTag(address);
        int lineNum = super.getLineNumber(address);

        for (int i = 0; i < associativity; i++){
            if ((cacheBlocks[lineNum][i].getTag() == tag)){
                return cacheBlocks[lineNum][i];
            }
        }
        return null;
    }

    public int getNbCacheMiss() {
        return cacheMiss;
    }


}
