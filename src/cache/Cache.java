package cache;

import bus.Bus;
import bus.BusController;
import bus.Request;
import cache.instruction.CacheInstruction;
import cache.lru.LruQueue;
import common.Clocked;
import cpu.Cpu;


public abstract class Cache implements Clocked {

    protected int privateAccess;
    protected int sharedAccess;
    protected Cpu cpu;
    protected BusController busController;
    protected Bus bus;
    protected CacheState state;
    protected final int cacheSize;
    protected final int blockSize;
    protected final LruQueue[] lruQueues;
    protected final int numLines;
    protected final int associativity;
    protected final int id;

    public Cache(int id, int cacheSize, int blockSize, int associativity) {

        this.id = id;
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

    public void linkCpu(Cpu cpu) {
        this.cpu = cpu;
    }

    public int notifyRequestAndGetExtraCycles(Request request) {
        boolean isOriginalSender = request.getSenderId() == this.id;

        if (!isOriginalSender) {
            return snoopTransition(request);
        } else {
            return receiveMessage(request);
        }

    }

    public abstract void ask(CacheInstruction instruction);

    public int getPrivateAccess() {
        return privateAccess;
    }

    public int getSharedAccess() {
        return sharedAccess;
    }

    public void linkBusController(BusController busController) {
        this.busController = busController;
    }

    public abstract Request getRequest();

    public int getId() {
        return id;
    }

    public Cpu getCpu() {
        return cpu;
    }

    public abstract boolean cacheHit(int address);

    public abstract int getNbCacheMiss();

    public double getMissRate() {
        double missRate = ((double) getNbCacheMiss()) / getCpu().getCacheInstructionCount();
        return missRate * 100;
    }

    protected abstract int receiveMessage(Request request);

    protected abstract int snoopTransition(Request request);

    protected int getTag(int address) {
        return address / cacheSize;
    }

    protected int getLineNumber(int address) {
        return (address % cacheSize) % (blockSize / 4);
    }
}
