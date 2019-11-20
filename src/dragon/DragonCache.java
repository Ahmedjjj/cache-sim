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
        cacheMiss++;
        memoryCycles = 0;
    }

    @Override
    protected int receiveMessage(Request request){
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
//    @Override
//    protected int receiveMessage(Request request) {
//        DragonCacheBlock dragonCacheBlock = (DragonCacheBlock) this.getCacheBlock(request.getAddress());
//        BusEvent busEvent = request.getBusEvent();
//        int address = request.getAddress();
//        boolean sharedSignal = busController.checkExistenceInOtherCaches(this.id, address);
//        switch (dragonCacheBlock.getState()) {
////            case EXCLUSIVE: //if it's a read stay, if it's a write automatically you should be in M no bus events happened
////                busTransactionOver();
////                break;
////            case SM://same as sc
////            case SC:
////                if (busEvent == BusEvent.BusUpd) {
////                    dragonCacheBlock.setState(sharedSignal ? DragonState.SM : DragonState.MODIFIED);
////                    busTransactionOver();
////                    return sharedSignal ? Constants.BUS_WORD_LATENCY : Constants.MEMORY_LATENCY;
////                }
////                break;
////            case MODIFIED:
////                busTransactionOver();
////                break;
////            case NOT_IN_CACHE:  if (currentType == CacheInstructionType.READ) {
////                if (sharedSignal) {
////                    cacheBlock.setState(DragonState.SC);
////                } else {
////                    // getBus().addRequest(new Request(id, BusEvent.BusRd, address, Constants.BUS_MESSAGE_CYCLES));
////                    cacheBlock.setState(DragonState.EXCLUSIVE);
////                }
////            } else {//write instr
////                if (sharedSignal) {
////                    setRequest(new Request(id, BusEvent.BusUpd, currentAddress, Constants.BUS_WORD_LATENCY, true));// OR JUST MESSAGE LATENCY?
////                    cacheBlock.setState(DragonState.SM);
////                } else {
////                    cacheBlock.setState(DragonState.MODIFIED);
////                }
////            }
//      }
//        return 0;
//    }

    private void busTransactionOver() {
        boolean sharedSignal= (busController.checkExistenceInOtherCaches(this.id,currentAddress));
        DragonCacheBlock cacheBlock = getCacheBlock(currentAddress);
        if (currentType == CacheInstructionType.READ ){
            if(cacheBlock.getState()==DragonState.NOT_IN_CACHE){
                cacheBlock.setState(sharedSignal?DragonState.SC:DragonState.EXCLUSIVE);
        }
        }else{
                cacheBlock.setState(sharedSignal?DragonState.SM:DragonState.MODIFIED);
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
        switch (dragonCacheBlock.getState()) {
            //shouldn't be null since it's this cache that generated the request for it
            case EXCLUSIVE:
                if (busEvent == BusEvent.BusRd)
                    dragonCacheBlock.setState(DragonState.SC);
                break;
            case SM:
                if (busEvent == BusEvent.BusUpd) {
                    dragonCacheBlock.setState(DragonState.SC);
                }
                if (busEvent == BusEvent.BusRd) {
                    //getBus().flushMemory(new DataRequest(this.getId(), BusEvent.Flush, processingRequest.getAddress(), 100));
                    //stays in sm, maybe not flush memory, just send the data on the bus
                    return Constants.MEMORY_LATENCY;
                }
                break;
            case SC:
                break;
            case MODIFIED:
                if (busEvent == BusEvent.BusRd) {
                    // getBus().flushMemory(new DataRequest(this.getId(), BusEvent.Flush, processingRequest.getAddress(), 100));
                    dragonCacheBlock.setState(DragonState.SM);
                    return Constants.MEMORY_LATENCY;
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
                if (memoryCycles == 0) {
                    cacheBlockToEvacuate.setState(DragonState.NOT_IN_CACHE);
                    ask(new CacheInstruction(currentType, currentAddress));
                }
                break;
        }
    }

    public void notify(Request request) {
        DragonCacheBlock dragonCacheBlock = (DragonCacheBlock) this.getCacheBlock(request.getAddress());
        BusEvent busEvent = request.getBusEvent();
        int address = request.getAddress();
        boolean sharedSignal = busController.checkExistenceInOtherCaches(this.id, address);
        if (dragonCacheBlock == null)
            return;
        if (request.getSenderId() == this.getId()) {
            switch (dragonCacheBlock.getState()) {
                case EXCLUSIVE: //if it's a read stay, if it's a write automatically you should be in M no bus events happened
                    break;
                case SM:
                    if (busEvent == BusEvent.BusUpd) {
                        dragonCacheBlock.setState(sharedSignal ? DragonState.SM : DragonState.MODIFIED);
                    }
                    break;
                case SC:
                    if (busEvent == BusEvent.BusUpd)
                        dragonCacheBlock.setState(sharedSignal ? DragonState.SM : DragonState.MODIFIED);//write back so not going to M
                    break;
                case MODIFIED:
                    break;
            }

        } else {//some other cache send the request
            switch (dragonCacheBlock.getState()) {
                //shouldn't be null since it's this cache that generated the request for it
                case EXCLUSIVE:
                    if (busEvent == BusEvent.BusRd)
                        dragonCacheBlock.setState(DragonState.SC);
                    break;
                case SM:
                    if (busEvent == BusEvent.BusUpd) {
                        dragonCacheBlock.setState(DragonState.SC);
                    }
                    if (busEvent == BusEvent.BusRd) {
                        //getBus().flushMemory(new DataRequest(this.getId(), BusEvent.Flush, processingRequest.getAddress(), 100));
                        //stays in sm, maybe not flush memory, just send the data on the bus
                    }
                    break;
                case SC:
                    break;
                case MODIFIED:
                    if (busEvent == BusEvent.BusRd)
                        // getBus().flushMemory(new DataRequest(this.getId(), BusEvent.Flush, processingRequest.getAddress(), 100));
                        dragonCacheBlock.setState(DragonState.SM);
                    break;
            }

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
        DragonCacheBlock cacheBlock = getCacheBlock(currentAddress);
        boolean sharedSignal = busController.checkExistenceInOtherCaches(this.id, currentAddress);
        switch (state) {
            case EXCLUSIVE:

            case MODIFIED:
                this.state = CacheState.WAITING_FOR_CACHE_HIT;
                break;
            case SM:
            case SC: {
                if (currentType == CacheInstructionType.WRITE) {
                    //setRequest(new Request(id, BusEvent.BusUpd, address, Constants.BUS_WORD_LATENCY,true));//send this anyways? according to the diagram
                    busController.queueUp(this);
                    //cacheBlock.setState(sharedSignal ? DragonState.SM : DragonState.MODIFIED);
                    this.state = CacheState.WAITING_FOR_BUS_MESSAGE;
                } else this.state = CacheState.WAITING_FOR_CACHE_HIT;
            }
            break;

            case NOT_IN_CACHE: {
                int blockToEvacuate = lruQueues[line].blockToEvacuate();
                DragonCacheBlock evacuatedCacheBlock = dragonCacheBlocks[line][blockToEvacuate];
                if (evacuatedCacheBlock.getState() == DragonState.MODIFIED){
                    this.cacheBlockToEvacuate = evacuatedCacheBlock;
                    this.memoryCycles = Constants.L1_CACHE_EVICTION_LATENCY;
                    this.state = CacheState.WAITING_FOR_MEMORY;
                }else {
                    lruQueues[line].evacuate();
                    evacuatedCacheBlock.setState(DragonState.NOT_IN_CACHE);
                    evacuatedCacheBlock.setTag(tag);
                    this.busController.queueUp(this);
                    this.state = CacheState.WAITING_FOR_BUS_MESSAGE;
                }
            }
            break;
        }

    }

    @Override
    public boolean cacheHit(int address) {
        return getBlockState(address) != DragonState.NOT_IN_CACHE;
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
        this.state = CacheState.WAITING_FOR_BUS_MESSAGE;
        return new Request(id, event, currentAddress, Constants.BUS_MESSAGE_CYCLES, false);
    }


}
