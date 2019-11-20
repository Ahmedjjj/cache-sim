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
    protected int receiveMessage(Request request) {
        DragonCacheBlock dragonCacheBlock = (DragonCacheBlock) this.getCacheBlock(request.getAddress());
        BusEvent busEvent = request.getBusEvent();
        int address=request.getAddress();
        boolean sharedSignal = busController.checkExistenceInOtherCaches(this.id, address);
            switch (dragonCacheBlock.getState()) {
                case EXCLUSIVE: //if it's a read stay, if it's a write automatically you should be in M no bus events happened
                    busTransactionOver();
                    break;
                case SM://same as sc
                case SC:
                    if (busEvent == BusEvent.BusUpd) {
                        dragonCacheBlock.setState(sharedSignal ? DragonState.SM : DragonState.MODIFIED);
                        busTransactionOver();
                        return sharedSignal ? Constants.BUS_WORD_LATENCY: Constants.MEMORY_LATENCY;
                    }break;
                case MODIFIED:
                    busTransactionOver();
                    break;

    }
    return 0;
    }

    private void busTransactionOver(){
        this.state = CacheState.IDLE;
        this.cpu.wake();
    }
    @Override
    protected int snoopTransition(Request request) {
        DragonCacheBlock dragonCacheBlock = (DragonCacheBlock) this.getCacheBlock(request.getAddress());
        BusEvent busEvent = request.getBusEvent();
        int address=request.getAddress();
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
        return  0;
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
                    cacheBlockToEvacuate.setState(DragonState.NOT_IN_CACHE);
                    ask(new CacheInstruction(currentType,currentAddress));
                }
                break;
        }
    }
    
    public void notify(Request request) {
        DragonCacheBlock dragonCacheBlock = (DragonCacheBlock) this.getCacheBlock(request.getAddress());
        BusEvent busEvent = request.getBusEvent();
        int address=request.getAddress();
        boolean sharedSignal = busController.checkExistenceInOtherCaches(this.id, address);
        if (dragonCacheBlock == null)
            return;
        if (request.getSenderId() == this.getId()) {
            switch (dragonCacheBlock.getState()) {
                case EXCLUSIVE: //if it's a read stay, if it's a write automatically you should be in M no bus events happened
                    break;
                case SM:if(busEvent==BusEvent.BusUpd){
                    dragonCacheBlock.setState(sharedSignal? DragonState.SM:DragonState.MODIFIED);
                }
                    break;
                case SC:
                    if (busEvent == BusEvent.BusUpd)
                        dragonCacheBlock.setState(sharedSignal?DragonState.SM: DragonState.MODIFIED);//write back so not going to M
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
        int address = instruction.getAddress();
        DragonState state = getBlockState(address);
        CacheInstructionType type = instruction.getCacheInstructionType();
        DragonCacheBlock cacheBlock = getCacheBlock(address);
        boolean sharedSignal = busController.checkExistenceInOtherCaches(this.id, address);
        switch (state) {

            case EXCLUSIVE:
//                {if (type == CacheInstructionType.WRITE)
//                    cacheBlock.setState(DragonState.MODIFIED);
//            }
            case MODIFIED:  this.state = CacheState.WAITING_FOR_CACHE_HIT;
                break;
            case SM: {
                if (type == CacheInstructionType.WRITE) {
                    setRequest(new Request(id, BusEvent.BusUpd, address, Constants.BUS_WORD_LATENCY,true));// OR JUST MESSAGE LATENCY?
                    cacheBlock.setState(sharedSignal ? DragonState.SM : DragonState.MODIFIED);//no need to update if it has only copy, but following the diagram ?
                }

            }
            break;
            case SC: {
                if (type == CacheInstructionType.WRITE) {
                    setRequest(new Request(id, BusEvent.BusUpd, address, Constants.BUS_WORD_LATENCY,true));//send this anyways? according to the diagram
                    cacheBlock.setState(sharedSignal ? DragonState.SM : DragonState.MODIFIED);
                }
            }
            break;

            case NOT_IN_CACHE: {
                if (type == CacheInstructionType.READ) {
                    if (sharedSignal) {
                        cacheBlock.setState(DragonState.SC);
                    } else {
                        // getBus().addRequest(new Request(id, BusEvent.BusRd, address, Constants.BUS_MESSAGE_CYCLES));
                        cacheBlock.setState(DragonState.EXCLUSIVE);
                    }
                } else {//write instr
                    if (sharedSignal) {
                        setRequest(new Request(id, BusEvent.BusUpd, address, Constants.BUS_WORD_LATENCY,true));// OR JUST MESSAGE LATENCY?
                        cacheBlock.setState(DragonState.SM);
                    } else {
                        cacheBlock.setState(DragonState.MODIFIED);
                    }
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



}
