package cache.mesi;

import bus.BusEvent;
import bus.Request;
import cache.Cache;
import cache.CacheState;
import cache.instruction.CacheInstruction;
import cache.instruction.CacheInstructionType;
import common.Constants;

public final class MesiCache extends Cache {


    private int cacheMiss;
    private int memoryCycles;
    private int currentAddress;
    private CacheInstructionType currentType;
    private CacheInstruction currentInstruction;
    private MesiCacheBlock cacheBlockToEvacuate;
    private final MesiCacheBlock[][] cacheBlocks;

    public MesiCache(int id, int cacheSize, int blockSize, int associativity) {
        super(id, cacheSize, blockSize, associativity);
        this.cacheBlocks = new MesiCacheBlock[numLines][associativity];
        for (int i = 0; i < numLines; i++) {
            for (int j = 0; j < associativity; j++) {
                cacheBlocks[i][j] = new MesiCacheBlock(blockSize);
            }
        }
        this.memoryCycles = 0;
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
                if (memoryCycles == 0) {
                    cacheBlockToEvacuate.setMesiState(MesiState.INVALID);
                    ask(currentInstruction);
                }
                break;
        }
    }



    @Override
    public String toString() {
        return "Cache " + id;
    }

    @Override
    public void ask(CacheInstruction instruction) {

        int address = instruction.getAddress();
        int line = getLineNumber(address);
        int tag = getTag(address);

        this.currentAddress = address;
        this.currentType = instruction.getCacheInstructionType();

        MesiCacheBlock cacheBlock = getBlock(address);
        boolean hit = cacheHit(address);

        if (hit) {
            currentInstruction = instruction;
            int blockNumber = getBlockNumber(address);
            lruQueues[line].update(blockNumber);
            switch (cacheBlock.getMesiState()) {
                case EXCLUSIVE:
                case MODIFIED:
                    privateAccess++;
                    this.state = CacheState.WAITING_FOR_CACHE_HIT;
                    break;
                case SHARED:
                    sharedAccess++;
                    if (instruction.getCacheInstructionType() == CacheInstructionType.WRITE) {
                        this.state = CacheState.WAITING_FOR_BUS_MESSAGE;
                        this.busController.queueUp(this);
                    } else {
                        this.state = CacheState.WAITING_FOR_CACHE_HIT;
                    }
                default:
                    break;
            }
        } else { // miss

            if (instruction != currentInstruction) {
                cacheMiss++;
            }
            currentInstruction = instruction;
            int blockToEvacuate = lruQueues[line].blockToEvacuate();
            MesiCacheBlock evacuatedCacheBlock = cacheBlocks[line][blockToEvacuate];

            if (evacuatedCacheBlock.getMesiState() == MesiState.MODIFIED) {
                this.cacheBlockToEvacuate = evacuatedCacheBlock;
                this.memoryCycles = Constants.L1_CACHE_EVICTION_LATENCY;
                this.state = CacheState.WAITING_FOR_MEMORY;
            } else {
                lruQueues[line].evacuate();
                evacuatedCacheBlock.setMesiState(MesiState.INVALID);
                evacuatedCacheBlock.setTag(tag);
                this.state = CacheState.WAITING_FOR_BUS_DATA;
                this.busController.queueUp(this);
            }
        }
    }

    @Override
    public Request getRequest() {

        BusEvent event;
        if (currentType == CacheInstructionType.READ) {
            event = BusEvent.BusRd;
        } else {
            nbInvalidations++;
            event = BusEvent.BusRdX;
        }
        boolean senderNeedsData;
        senderNeedsData = !cacheHit(currentAddress);
        return new Request(id, event, currentAddress, Constants.BUS_MESSAGE_CYCLES, senderNeedsData);
    }

    @Override
    public boolean cacheHit(int address) {
        return getBlockState(address) != MesiState.INVALID;
    }

    public int getNbCacheMiss() {
        return cacheMiss;
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

        MesiCacheBlock cacheBlock = getBlock(request.getAddress());
        BusEvent busEvent = request.getBusEvent();

        if (cacheBlock != null) {
            switch (cacheBlock.getMesiState()) {
                case INVALID:
                    return 0;
                case SHARED:
                    if (busEvent == BusEvent.BusRdX) {
                        cacheBlock.setMesiState(MesiState.INVALID);
                    }
                    return (blockSize / Constants.BYTES_IN_WORD) * Constants.BUS_WORD_LATENCY;
                case EXCLUSIVE:
                    if (busEvent == BusEvent.BusRd) {
                        cacheBlock.setMesiState(MesiState.SHARED);
                    } else {
                        cacheBlock.setMesiState(MesiState.INVALID);
                    }
                    return (blockSize / Constants.BYTES_IN_WORD) * Constants.BUS_WORD_LATENCY;
                case MODIFIED:
                    if (busEvent == BusEvent.BusRd) {
                        cacheBlock.setMesiState(MesiState.SHARED);
                        if (state == CacheState.WAITING_FOR_MEMORY){
                            return memoryCycles + (blockSize / Constants.BYTES_IN_WORD) * Constants.BUS_WORD_LATENCY;
                        }else {
                            return Constants.MEMORY_LATENCY;
                        }
                    } else if (busEvent == BusEvent.BusRdX) {
                        cacheBlock.setMesiState(MesiState.INVALID);
                        return (blockSize / Constants.BYTES_IN_WORD) * Constants.BUS_WORD_LATENCY;
                    }
                    break;
            }
        }
        return 0;
    }

    private void busTransactionOver() {
        MesiCacheBlock cacheBlock = getBlock(currentAddress);
        if (currentType == CacheInstructionType.READ) {
            if (busController.checkExistenceInOtherCaches(this.id, currentAddress)) {
                cacheBlock.setMesiState(MesiState.SHARED);
            } else {
                cacheBlock.setMesiState(MesiState.EXCLUSIVE);
            }
        } else {
            cacheBlock.setMesiState(MesiState.MODIFIED);
        }
        this.state = CacheState.IDLE;
        this.cpu.wake();
    }

    private MesiCacheBlock getBlock(int address) {
        int tag = super.getTag(address);
        int lineNum = super.getLineNumber(address);

        for (int i = 0; i < associativity; i++) {
            if ((cacheBlocks[lineNum][i].getTag() == tag)) {
                return cacheBlocks[lineNum][i];
            }
        }
        return null;
    }

    private MesiState getBlockState(int address) {
        MesiCacheBlock cacheBlock = getCacheBlock(address);
        return cacheBlock == null ? MesiState.INVALID : cacheBlock.getMesiState();
    }

    private MesiCacheBlock getCacheBlock(int address) {
        int tag = super.getTag(address);
        int lineNum = super.getLineNumber(address);

        for (int i = 0; i < associativity; i++) {
            if ((cacheBlocks[lineNum][i].getTag() == tag)) {
                return cacheBlocks[lineNum][i];
            }
        }
        return null;
    }

    private int getBlockNumber(int address) {
        int tag = super.getTag(address);
        int lineNum = super.getLineNumber(address);

        for (int i = 0; i < associativity; i++) {
            if ((cacheBlocks[lineNum][i].getTag() == tag)) {
                return i;
            }
        }
        return -1;
    }
}
