package cache.dragon;

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
        cacheMiss = 0;
        memoryCycles = 0;
    }

    @Override
    protected int receiveMessage(Request request) {
        if (this.state == CacheState.WAITING_FOR_BUS_DATA) {
            if (request.isDataRequest()) {
                request.setSenderNeedsData(false);
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
        int bytesSend = 0;
        switch (dragonCacheBlock.getState()) {
            //shouldn't be null since it's this cache that generated the request for it
            case EXCLUSIVE:
                //privateAccess++;
                dragonCacheBlock.setState(DragonState.SC);
                bytesSend = (blockSize / Constants.BYTES_IN_WORD) * Constants.BUS_WORD_LATENCY;// any request for access
                if (busEvent == BusEvent.BusUpd) {
                    bytesSend += Constants.BUS_UPD_LATENCY; // assuming it has sent the block, and gets the update
                }
                return bytesSend;
            case SM:
                //sharedAccess++;
                if (busEvent == BusEvent.BusUpd) {
                    dragonCacheBlock.setState(DragonState.SC);
                    return Constants.BUS_UPD_LATENCY; // only gets the update
                }
                if (busEvent == BusEvent.BusRd) {
                    //stays in sm
                    return (blockSize / Constants.BYTES_IN_WORD) * Constants.BUS_WORD_LATENCY; //sends the whole block to the requesting cache
                }
                break;
            case SC:
                if (busEvent == BusEvent.BusRd) {
                    return (blockSize / Constants.BYTES_IN_WORD) * Constants.BUS_WORD_LATENCY;// it sends the data on the bus
                } else
                    return Constants.BUS_UPD_LATENCY; // accounting for the data recieved for the update

            case MODIFIED:
                // privateAccess++;
                if (busEvent == BusEvent.BusRd) {
                    dragonCacheBlock.setState(DragonState.SM);
                    return Constants.MEMORY_LATENCY; //needs to flush
                } else { // someone is writing to this block
                    dragonCacheBlock.setState(DragonState.SC);
                    return Constants.MEMORY_LATENCY + Constants.BUS_UPD_LATENCY;
                    //must  writeback + get the update
                }
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


    public DragonState getBlockState(int address) {
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

    int num = 0;
    int cacheHit = 0;
    CacheInstruction currentInstruction;

    @Override
    public void ask(CacheInstruction instruction) {
        if (currentInstruction != instruction) {
            num++;
        }
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
                if (state == DragonState.SM)
                    sharedAccess++;
                if (currentType == CacheInstructionType.WRITE) {
                    this.state = CacheState.WAITING_FOR_BUS_MESSAGE;
                    busController.queueUp(this);
                } else this.state = CacheState.WAITING_FOR_CACHE_HIT;
            }
            break;
            case NOT_IN_CACHE: {//miss
                if (instruction != currentInstruction)
                    cacheMiss++;
                currentInstruction = instruction;
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
                    this.state = CacheState.WAITING_FOR_BUS_DATA;
                    this.busController.queueUp(this);
                }
            }
            break;
        }

    }

    @Override
    public boolean cacheHit(int address) {
        return getBlockState(address) != DragonState.NOT_IN_CACHE;
    }

    public String toString() {
        return "Dragon " + id;
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
            nbInvalidations++;
            event = BusEvent.BusUpd;
        }
        boolean senderNeedsData = !cacheHit(currentAddress);
        return new Request(id, event, currentAddress, Constants.BUS_MESSAGE_CYCLES, senderNeedsData);
    }

}

