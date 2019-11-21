package cache.mesi;


import bus.BusEvent;
import bus.Request;
import cache.Cache;
import cache.CacheState;
import cache.instruction.CacheInstruction;
import cache.instruction.CacheInstructionType;
import common.Constants;

public final class MesiCache extends Cache {

    private final MesiCacheBlock[][] cacheBlocks;

    private int cacheMiss;

    private int currentAddress;
    private CacheInstructionType currentType;

    private int memoryCycles;
    private MesiCacheBlock cacheBlockToEvacuate;

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
                    ask(new CacheInstruction(currentType, currentAddress));
                }
                break;
        }
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

            int blockNumber = getBlockNumber(address);
            lruQueues[line].update(blockNumber);
            switch (cacheBlock.getMesiState()) {
                case EXCLUSIVE:
                case MODIFIED:
                    this.state = CacheState.WAITING_FOR_CACHE_HIT;
                    break;
                case SHARED:
                    if (instruction.getCacheInstructionType() == CacheInstructionType.WRITE) {
                        this.state = CacheState.WAITING_FOR_BUS_MESSAGE;
                        this.busController.queueUp(this);
                    } else {
                        this.state = CacheState.WAITING_FOR_CACHE_HIT;
                    }
                default:
                    break;
            }
        } else { //miss
            cacheMiss++;
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

    @Override
    protected int receiveMessage(Request request) {
        assert request == this.request;
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
        assert request != this.request;

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
                    return blockSize / Constants.BUS_WORD_LATENCY;
                case EXCLUSIVE:
                    if (busEvent == BusEvent.BusRd) {
                        cacheBlock.setMesiState(MesiState.SHARED);
                    } else {
                        cacheBlock.setMesiState(MesiState.INVALID);
                    }
                    return blockSize / Constants.BUS_WORD_LATENCY;
                case MODIFIED:
                    if (busEvent == BusEvent.BusRd) {
                        cacheBlock.setMesiState(MesiState.SHARED);
                        return Constants.MEMORY_LATENCY + blockSize / Constants.BUS_WORD_LATENCY;

                    } else if (busEvent == BusEvent.BusRdX) {
                        cacheBlock.setMesiState(MesiState.INVALID);
                        return blockSize / Constants.BUS_WORD_LATENCY;
                    }
                    break;
            }
        }
        return 0;
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

    public boolean hasBlock(int address) {
        return cacheHit(address);
    }

    @Override
    public boolean cacheHit(int address) {
        return getBlockState(address) != MesiState.INVALID;
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

    Request request;
    @Override
    public Request getRequest() {

        BusEvent event;
        if (currentType == CacheInstructionType.READ) {
            event = BusEvent.BusRd;
        } else {
            event = BusEvent.BusRdX;
        }

        boolean senderNeedsData ;
        if (cacheHit(currentAddress)) {
            assert this.state == CacheState.WAITING_FOR_BUS_MESSAGE;
            senderNeedsData = false;
        }else{
            assert this.state == CacheState.WAITING_FOR_BUS_DATA;
            senderNeedsData = true;
        }
    request = new Request(id, event, currentAddress, Constants.BUS_MESSAGE_CYCLES, senderNeedsData);
        return request;
    }

    public String toString() {
        return "Cache " + id;
    }

    public int getNbCacheMiss() {
        return cacheMiss;
    }

}
