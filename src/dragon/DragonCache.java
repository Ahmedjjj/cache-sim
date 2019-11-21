package dragon;

import bus.BusEvent;
import bus.Request;
import cache.Cache;
import cache.CacheState;
import cache.instruction.CacheInstruction;
import cache.instruction.CacheInstructionType;
import common.Constants;

public class DragonCache extends Cache {
    private DragonCacheBlock[][] dragonCacheBlocks;
    private int cacheMiss;
    private int memoryCycles;
    private DragonCacheBlock cacheBlockToEvacuate;
    private int currentAddress;



    private CacheInstructionType currentType;

    public DragonCache(int id, int cacheSize, int blockSize, int associativity) {
        super(id, cacheSize, blockSize, associativity);
        this.dragonCacheBlocks = new DragonCacheBlock[numLines][associativity];
        for (int i = 0; i < numLines; i++) {
            for (int j = 0; j < associativity; j++) {
                dragonCacheBlocks[i][j] = new DragonCacheBlock(blockSize);
            }
        }
        cacheMiss=0;
        memoryCycles = 0;
    }

    @Override
    protected int receiveMessage(Request request) {
        if (this.state == CacheState.WAITING_FOR_BUS_DATA) {
            if (request.isDataRequest()) {
                busTransactionOver();
            } else if (!busController.checkExistenceInOtherCaches(id, request.getAddress())) {
                this.state = CacheState.WAITING_FOR_BUS_DATA;
                return Constants.MEMORY_LATENCY;
            }
        } else if (this.state == CacheState.WAITING_FOR_BUS_MESSAGE) {
            busTransactionOver();
        }

        return 0;
    }


    private void busTransactionOver() {
        boolean sharedSignal = (busController.checkExistenceInOtherCaches(this.id, currentAddress));
        DragonCacheBlock cacheBlock = getCacheBlock(currentAddress);
        if (currentType == CacheInstructionType.READ) {
            if (cacheBlock.getState() == DragonState.NOT_IN_CACHE) {
                cacheBlock.setState(sharedSignal ? DragonState.SC : DragonState.EXCLUSIVE);
            }
        } else {
            cacheBlock.setState(sharedSignal ? DragonState.SM : DragonState.MODIFIED);
        }
        this.state = CacheState.IDLE;
        this.cpu.wake();
    }

    @Override
    protected int snoopTransition(Request request) {
        DragonCacheBlock dragonCacheBlock = (DragonCacheBlock) this.getCacheBlock(request.getAddress());
        BusEvent busEvent = request.getBusEvent();
        int address = request.getAddress();
        boolean sharedSignal = busController.checkExistenceInOtherCaches(this.id, address);
        if (dragonCacheBlock == null)
            return 0;
        switch (dragonCacheBlock.getState()) {
            //shouldn't be null since it's this cache that generated the request for it
            case EXCLUSIVE:
                if (busEvent == BusEvent.BusRd) {
                    privateAccess++;
                    dragonCacheBlock.setState(DragonState.SC);
                    return blockSize / Constants.BUS_WORD_LATENCY;
                }
                break;
            case SM:
                sharedAccess++;
                if (busEvent == BusEvent.BusUpd) {
                    dragonCacheBlock.setState(DragonState.SC);
                    return Constants.BUS_WORD_LATENCY;
                }
                if (busEvent == BusEvent.BusRd) {
                    //stays in sm, maybe not flush memory, just send the data on the bus
                    return blockSize / Constants.BUS_WORD_LATENCY; //?? isnt it
                }
                break;
            case SC:
                if (busEvent == BusEvent.BusRd) {
                    //stays in sm, maybe not flush memory, just send the data on the bus
                    return blockSize / Constants.BUS_WORD_LATENCY; //?? isnt it
                }
                break;
            case MODIFIED:
                if (busEvent == BusEvent.BusRd) {
                    privateAccess++;
                    dragonCacheBlock.setState(DragonState.SM);
                    return Constants.MEMORY_LATENCY+blockSize / Constants.BUS_WORD_LATENCY;
                }
                break;

        }
        return 0;
    }

    @Override
    public void runForOneCycle() {
        switch (this.state) {
            case IDLE:
            case WAITING_FOR_BUS_DATA:
            case WAITING_FOR_BUS_MESSAGE:
                break;
            case WAITING_FOR_CACHE_HIT:
                this.state = CacheState.IDLE;
                this.cpu.wake();
                break;
            case WAITING_FOR_MEMORY:
                this.memoryCycles--;
                if (memoryCycles <= 0) {
                    cacheBlockToEvacuate.setState(DragonState.NOT_IN_CACHE);
                    ask(new CacheInstruction(currentType, currentAddress));
                }
                break;
        }
    }


    private DragonState getBlockState(int address) {
        DragonCacheBlock cacheBlock = getCacheBlock(address);
        return cacheBlock == null ? DragonState.NOT_IN_CACHE : cacheBlock.getState();
    }

    protected DragonCacheBlock getCacheBlock(int address) {
        int tag = super.getTag(address);
        int lineNum = super.getLineNumber(address);

        for (int i = 0; i < associativity; i++) {
            if ((dragonCacheBlocks[lineNum][i].getTag() == tag)) {
                return dragonCacheBlocks[lineNum][i];
            }
        }
        return null;
    }

    @Override
    public void ask(CacheInstruction instruction) {
        this.currentAddress = instruction.getAddress();
        DragonState state = getBlockState(currentAddress);
        this.currentType = instruction.getCacheInstructionType();
        int line = getLineNumber(currentAddress);
        int tag = getTag(currentAddress);
        switch (state) {
            case EXCLUSIVE:

            case MODIFIED:
                this.state = CacheState.WAITING_FOR_CACHE_HIT;
                privateAccess++;
                break;
            case SM:
            case SC: {
                if (currentType == CacheInstructionType.WRITE) {
                    busController.queueUp(this);
                    this.state = CacheState.WAITING_FOR_BUS_MESSAGE;
                } else this.state = CacheState.WAITING_FOR_CACHE_HIT;
            }
            break;
            case NOT_IN_CACHE: {//miss
                cacheMiss++;
                int blockToEvacuate = lruQueues[line].blockToEvacuate();
                DragonCacheBlock evacuatedCacheBlock = dragonCacheBlocks[line][blockToEvacuate];
                if (evacuatedCacheBlock.getState() == DragonState.MODIFIED) {
                    this.cacheBlockToEvacuate = evacuatedCacheBlock;
                    this.memoryCycles = Constants.L1_CACHE_EVICTION_LATENCY;
                    this.state = CacheState.WAITING_FOR_MEMORY;
                } else {
                    lruQueues[line].evacuate();
                    evacuatedCacheBlock.setState(DragonState.NOT_IN_CACHE);
                    evacuatedCacheBlock.setTag(tag);
                    this.busController.queueUp(this);
                    this.state = CacheState.WAITING_FOR_BUS_DATA;
                }
            }
            break;
        }

    }

    @Override
    public boolean cacheHit(int address) {
        return getBlockState(address) != DragonState.NOT_IN_CACHE;
    }

public String toString(){
        return "Dragon "+id;
}


    @Override
    public boolean hasBlock(int address) {
        return false;
    }


    public int getNbCacheMiss() {
        return cacheMiss;
    }

    @Override
    public Request getRequest() {
        BusEvent event;
        if (currentType == CacheInstructionType.READ) {
            event = BusEvent.BusRd;
        } else {
            event = BusEvent.BusUpd;
        }
        boolean senderNeedsData ;
        if (cacheHit(currentAddress)) {
             this.state = CacheState.WAITING_FOR_BUS_MESSAGE;

            senderNeedsData = false;
        }else{
             this.state = CacheState.WAITING_FOR_BUS_DATA;
            senderNeedsData = true;
        }
        //this.state = CacheState.WAITING_FOR_BUS_MESSAGE;
        return new Request(id, event, currentAddress, Constants.BUS_MESSAGE_CYCLES, senderNeedsData);
    }


}
