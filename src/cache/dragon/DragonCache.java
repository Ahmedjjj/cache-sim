package cache.dragon;

import bus.BusEvent;
import bus.Request;
import cache.Cache;
import cache.CacheState;
import cache.instruction.CacheInstruction;
import cache.instruction.CacheInstructionType;
import common.Constants;

public class DragonCache extends Cache {


    private CacheInstruction currentInstruction;
    private DragonCacheBlock[][] dragonCacheBlocks;
    private int cacheMiss;
    private int memoryCycles;
    private DragonCacheBlock cacheBlockToEvacuate;
    private int currentAddress;
    private CacheInstructionType currentType;
    private int dataSent;

    public DragonCache(int id, int cacheSize, int blockSize, int associativity) {
        super(id, cacheSize, blockSize, associativity);
        this.dragonCacheBlocks = new DragonCacheBlock[numLines][associativity];
        for (int i = 0; i < numLines; i++) {
            for (int j = 0; j < associativity; j++) {
                dragonCacheBlocks[i][j] = new DragonCacheBlock(blockSize);
            }
        }
        dataSent = 0;
        cacheMiss = 0;
        memoryCycles = 0;
        currentInstruction = null;
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
                    ask(currentInstruction);
                }
                break;
        }
    }

    public DragonState getBlockState(int address) {
        DragonCacheBlock cacheBlock = getCacheBlock(address);
        return cacheBlock == null ? DragonState.NOT_IN_CACHE : cacheBlock.getState();
    }

    @Override
    public void ask(CacheInstruction instruction) {
        this.currentAddress = instruction.getAddress();
        DragonState state = getBlockState(currentAddress);
        this.currentType = instruction.getCacheInstructionType();
        int line = getLineNumber(currentAddress);
        int tag = getTag(currentAddress);
        if (cacheHit(currentAddress)) {
            lruQueues[line].update(getBlockNumber(currentAddress));
            // currentInstruction=instruction;
        }
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
                } else {
                    this.state = CacheState.WAITING_FOR_CACHE_HIT;
                }
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

    @Override
    public int getDataSent() {
        int tmp = dataSent;
        dataSent = 0;
        return tmp;
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

    @Override
    protected int snoopTransition(Request request) {
        DragonCacheBlock dragonCacheBlock = this.getCacheBlock(request.getAddress());
        BusEvent busEvent = request.getBusEvent();
        if (dragonCacheBlock == null)
            return 0;
        int totalCycles = 0;
        switch (dragonCacheBlock.getState()) {
            case EXCLUSIVE:
                dragonCacheBlock.setState(DragonState.SC);
                totalCycles = (blockSize / Constants.BYTES_IN_WORD) * Constants.BUS_WORD_LATENCY;// any request for access, send the block
                dataSent = blockSize;
                if (busEvent == BusEvent.BusUpd) {
                    totalCycles += Constants.BUS_UPD_LATENCY; // assuming it has sent the block, and gets the update
                    dataSent += Constants.BYTES_IN_WORD;
                }
                return totalCycles;
            case SM:
                if (busEvent == BusEvent.BusUpd) {
                    dragonCacheBlock.setState(DragonState.SC);
                    dataSent = Constants.BYTES_IN_WORD;
                    return Constants.BUS_UPD_LATENCY; // only gets the update
                }
                if (busEvent == BusEvent.BusRd) {//stays in sm
                    dataSent = blockSize;
                    return Constants.MEMORY_LATENCY; //flush to memory
                }
                break;
            case SC:
                if (busEvent == BusEvent.BusRd) {
                    dataSent = blockSize;
                    return (blockSize / Constants.BYTES_IN_WORD) * Constants.BUS_WORD_LATENCY;// it sends the data on the bus
                }
                dataSent = Constants.BYTES_IN_WORD;
                return Constants.BUS_UPD_LATENCY; // accounting for the data recieved for the update

            case MODIFIED:
                dataSent = blockSize;
                if (busEvent == BusEvent.BusRd) {
                    dragonCacheBlock.setState(DragonState.SM);
                    if (state == CacheState.WAITING_FOR_MEMORY)
                        return memoryCycles + (blockSize / Constants.BYTES_IN_WORD) * Constants.BUS_WORD_LATENCY;
                    else
                        return Constants.MEMORY_LATENCY;//needs to flush
                } else { // someone is writing to this block
                    dragonCacheBlock.setState(DragonState.SC);
                    return Constants.MEMORY_LATENCY + Constants.BUS_UPD_LATENCY; //must  writeback + get the update
                }
        }
        return 0;
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

    private int getBlockNumber(int address) {
        int tag = super.getTag(address);
        int lineNum = super.getLineNumber(address);

        for (int i = 0; i < associativity; i++) {
            if ((dragonCacheBlocks[lineNum][i].getTag() == tag)) {
                return i;
            }
        }
        return -1;
    }

}


