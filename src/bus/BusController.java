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
    private int busTraffic;

    public BusController() {
        this.caches = new LinkedList<>();
        this.currentRequest = null;
        this.bus = bus;
        this.cacheQueue = new LinkedList<>();
        busTraffic=0;

    }

    public void attachTo ( Bus bus){
        this.bus = bus;
    }
    public void attach(Cache cache){
        this.caches.add(cache);
        cache.linkBusController(this);
    }

    public void alert(){
        assert currentRequest != null;

        int extra_cycles = 0;
        for (Cache c : caches){
            int extra = c.notifyRequestAndGetExtraCycles(currentRequest);
            if (extra > 0){
                extra_cycles = extra;
                busTraffic+=extra;
            }
        }
        if (extra_cycles > 0){
            currentRequest.setCyclesToExecute(extra_cycles);
            currentRequest.setDataRequest(true);
        }else{
            setNewRequest();
        }
    }

    public boolean checkExistenceInAllCaches (int address){
        return caches.stream().anyMatch(c -> c.hasBlock(address));
    }

    public boolean checkExistenceInOtherCaches(int senderId, int address) {
        return caches.stream().anyMatch(c -> c.getId()!=senderId&&c.hasBlock(address));
    }
boolean executing=false;
    public void queueUp (Cache cache){
        assert !cacheQueue.contains(cache);
        int k;
        if(cacheQueue.size()>4)
           k = cacheQueue.size();
           // throw new IllegalStateException();
        if (cacheQueue.isEmpty()&&currentRequest==null){
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
           // System.out.println(cache);
            this.currentRequest = cache.getRequest();
            this.bus.setCurrentRequest(currentRequest);
            this.currentBusMaster = cache;
        }
    }

    public int getBusTraffic() {
        return busTraffic;
    }
}
