package bus;


import cache.Cache;
import common.Constants;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public final class BusController {

    private final List<Cache> caches;
    private Bus bus;
    private Request currentRequest;
    private Cache currentBusMaster;
    private final Queue<Cache> cacheQueue;

    public BusController() {
        this.caches = new LinkedList<>();
        this.currentRequest = null;
        this.bus = bus;
        this.cacheQueue = new LinkedList<>();

    }

    public void attachTo ( Bus bus){
        this.bus = bus;
    }
    public void attach(Cache cache){
        this.caches.add(cache);
    }

    public void alert(){
        assert currentRequest != null;

        int extra_cycles = 0;
        for (Cache c : caches){
            int extra = c.notifyRequestAndGetExtraCycles(currentRequest);
            if (extra != 0){
                extra_cycles = extra;
            }
        }
        if (extra_cycles != 0){
            currentRequest.setCyclesToExecute(extra_cycles);
            currentRequest.setDataRequest(true);
        }else{
            setNewRequest();
        }
    }

    public boolean checkExistenceInAllCaches (int address){
        return caches.stream().anyMatch(c -> c.hasBlock(address));
    }

    public void queueUp (Cache cache){
        assert !cacheQueue.contains(cache);
        if (cacheQueue.isEmpty()){
            this.currentRequest = cache.getRequest();
            this.bus.setCurrentRequest(currentRequest);
            this.currentBusMaster = cache;
        }else{
            this.cacheQueue.add(cache);
        }
    }
    private void setNewRequest(){
        if (!cacheQueue.isEmpty()){
            Cache cache = cacheQueue.poll();
            this.currentRequest = cache.getRequest();
            this.bus.setCurrentRequest(currentRequest);
            this.currentBusMaster = cache;
        }
    }
}
