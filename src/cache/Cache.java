package cache;

import bus.Bus;
import bus.BusController;
import bus.Request;
import cache.instruction.CacheInstruction;
import cache.lru.LruQueue;
import common.Clocked;
import cpu.Cpu;



public abstract class Cache implements Clocked {

    protected final int cacheSize;
    protected final int blockSize;
    protected final LruQueue[] lruQueues;
    protected final int numLines;
    protected final int associativity;
    protected final int id;

    protected Cpu cpu;
    protected BusController busController;
    protected CacheState state;

    public Cache(int id, int cacheSize, int blockSize, int associativity) {

        this.id=id;
        this.cacheSize = cacheSize;
        this.blockSize = blockSize;
        this.associativity = associativity;
        this.numLines = cacheSize / (blockSize * associativity);
        this.lruQueues = new LruQueue[this.numLines];
        this.state = CacheState.IDLE;
        for (int i = 0; i < numLines; i++) {
            lruQueues[i] = new LruQueue(associativity);
        }
    }
    public void linkCpu(Cpu cpu){
        this.cpu = cpu;
    }

    public abstract int notifyRequestAndGetExtraCycles(Request request);

    public abstract void ask(CacheInstruction instruction);

    public abstract boolean hasBlock(int address);

    public abstract Request getRequest();

    public int getId(){
        return id;
    }

    public int getBlockSize() {
        return blockSize;
    }

    public void linkBus(Bus bus){
        this.bus = bus;
    }
    public Bus getBus(){
        return bus;
    }
    public void linkCpu(Cpu p) {
        this.cpu = p;
    }
    public Cpu getCpu(){
        return cpu;
    }
    public void wakeCpu(){
        cpu.wake();
    }
    public abstract boolean cacheHit(int address);

    protected int getTag(int address) {
        return address / numLines;
    }
    protected int getLineNumber(int address) {
        return address % numLines;
    }
    public abstract int getNbCacheMiss();

    public double getMissRate() {
        double missRate = getNbCacheMiss() / getCpu().getInstructionCount();
        return missRate * 100;
    }
}
