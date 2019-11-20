package cache.mesi;


import bus.BusEvent;
import bus.DataRequest;
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
        memoryCycles = 0;

    }

    @Override
    public void runForOneCycle() {
        switch (this.state){
            case IDLE:
            case WAITING_FOR_BUS_DATA:
            case WAITING_FOR_BUS_MESSAGE:
                break;
            case WAITING_FOR_CACHE_HIT:
                this.state = CacheState.IDLE;
                this.cpu.wake();
                break;
            case WAITING_FOR_MEMORY:
                this.memoryCycles --;
                if(memoryCycles == 0){
                    cacheBlockToEvacuate.setMesiState(MesiState.INVALID);
                    ask(new CacheInstruction(currentType,currentAddress));
                }
                break;
        }
    }

    @Override
    public int notifyRequestAndGetExtraCycles(Request request) {
        boolean isOriginalSender = request.getSenderId() == this.id;

        if (!isOriginalSender){
            return snoopTransition(request);
        }else{
            return receiveMessage(request);
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
        boolean hit = (cacheBlock != null) && (cacheBlock.getMesiState() != MesiState.INVALID);
        if (hit){
            int blockNumber = getBlockNumber(address);
            lruQueues[line].update(blockNumber);
            switch (cacheBlock.getMesiState()){
                case EXCLUSIVE:
                case MODIFIED:
                    this.state = CacheState.WAITING_FOR_CACHE_HIT;
                    break;
                case SHARED:
                    if (instruction.getCacheInstructionType() == CacheInstructionType.WRITE){
                        this.busController.queueUp(this);
                        this.state = CacheState.WAITING_FOR_BUS_MESSAGE;
                    }else {
                        this.state = CacheState.WAITING_FOR_CACHE_HIT;
                    }
                default:
                    break;
            }
        }else{
            int blockToEvacuate = lruQueues[line].blockToEvacuate();
            MesiCacheBlock evacuatedCacheBlock = cacheBlocks[numLines][blockToEvacuate];

            if (evacuatedCacheBlock.getMesiState() == MesiState.MODIFIED){
                this.cacheBlockToEvacuate = evacuatedCacheBlock;
                this.memoryCycles = Constants.L1_CACHE_EVICTION_LATENCY;
                this.state = CacheState.WAITING_FOR_MEMORY;
            }else {
                lruQueues[line].evacuate();
                evacuatedCacheBlock.setMesiState(MesiState.INVALID);
                evacuatedCacheBlock.setTag(tag);
                this.busController.queueUp(this);
                this.state = CacheState.WAITING_FOR_BUS_MESSAGE;
            }
        }
    }

    private void busTransactionOver(){
        MesiCacheBlock cacheBlock = getBlock(currentAddress);
        if (currentType == CacheInstructionType.READ){
            if(busController.checkExistenceInAllCaches(currentAddress)){
                cacheBlock.setMesiState(MesiState.SHARED);
            }else{
                cacheBlock.setMesiState(MesiState.EXCLUSIVE);
            }
        }else{
            cacheBlock.setMesiState(MesiState.MODIFIED);
        }
        this.state = CacheState.IDLE;
        this.cpu.wake();
    }

    private int receiveMessage(Request request){
        if (this.state == CacheState.WAITING_FOR_BUS_DATA){
            if (request.isDataRequest()){
                busTransactionOver();
            }else if (!busController.checkExistenceInAllCaches(request.getAddress())){
                this.state = CacheState.WAITING_FOR_BUS_DATA;
                return Constants.MEMORY_LATENCY;
            }
        }else if (this.state == CacheState.WAITING_FOR_BUS_MESSAGE){
            busTransactionOver();
        }

        return 0;
    }

    private int snoopTransition (Request request) {

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
                    return Constants.BUS_WORD_LATENCY * blockSize;
                case EXCLUSIVE:
                    if (busEvent == BusEvent.BusRd){
                        cacheBlock.setMesiState(MesiState.SHARED);
                    }else{
                        cacheBlock.setMesiState(MesiState.INVALID);
                    }
                    return Constants.BUS_WORD_LATENCY * blockSize;
                case MODIFIED:
                    if (busEvent == BusEvent.BusRd) {
                        cacheBlock.setMesiState(MesiState.SHARED);
                        return Constants.MEMORY_LATENCY + Constants.BUS_WORD_LATENCY * blockSize;

                    } else if (busEvent == BusEvent.BusRdX) {
                        cacheBlock.setMesiState(MesiState.INVALID);
                        return Constants.BUS_WORD_LATENCY * blockSize;
                    }
                    break;
            }
        }
        return 0;
    }

    private MesiCacheBlock getBlock (int address){
        int tag = super.getTag(address);
        int lineNum = super.getLineNumber(address);

        for (int i = 0; i < associativity; i++){
            if ((cacheBlocks[lineNum][i].getTag() == tag)){
                return cacheBlocks[lineNum][i];
            }
        }
        return null;
    }


    public boolean hasBlock (int address){
        return this.getBlock(address) != null;
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

private int getBlockNumber(int address) {        
int tag = super.getTag(address);
        int lineNum = super.getLineNumber(address);

        for (int i = 0; i < associativity; i++){
            if ((cacheBlocks[lineNum][i].getTag() == tag)){
                return i;
            }
        }
        return -1;
    }



    @Override
    public Request getRequest() {

        BusEvent event;
        if (currentType == CacheInstructionType.READ){
            event = BusEvent.BusRd;
        }else {
            event = BusEvent.BusWr;
        }
        this.state = CacheState.WAITING_FOR_BUS_MESSAGE;
        return new Request(id, event, currentAddress, Constants.BUS_MESSAGE_CYCLES,false);
    }
    public int getNbCacheMiss() {
        return cacheMiss;
    }

}
