package bus;


import cache.Cache;
import cache.CacheState;
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
        if (extra_cycles > 0 && currentRequest.senderNeedsData()){
            currentRequest.setCyclesToExecute(extra_cycles);
            currentRequest.setDataRequest(true);
        }else{

            Cache sender = caches.stream().filter(c -> c.getId() == currentRequest.getSenderId()).findFirst().get();
            assert sender.getState() == CacheState.IDLE;
            setNewRequest();
        }
    }

    public boolean checkExistenceInAllCaches (int address){
        return caches.stream().anyMatch(c -> c.hasBlock(address));
    }

    public boolean checkExistenceInOtherCaches(int senderId, int address) {
        return caches.stream().anyMatch(c -> c.getId()!=senderId && c.cacheHit(address));
    }

    public void queueUp (Cache cache){
        if (cacheQueue.contains(cache)){
          //
        };
        //System.out.println(cacheQueue);
        assert !cacheQueue.contains(cache);
        if (cacheQueue.isEmpty()&&currentRequest==null){
            this.currentRequest = cache.getRequest();
            this.bus.setCurrentRequest(currentRequest);
            this.currentBusMaster = cache;
        }else{
            this.cacheQueue.add(cache);

        }
    }
    private void setNewRequest(){
        //System.out.println("a");
        if (!cacheQueue.isEmpty()){
            Cache cache = cacheQueue.poll();
           // System.out.println(cache);
            this.currentRequest = cache.getRequest();
            this.bus.setCurrentRequest(currentRequest);
            this.currentBusMaster = cache;
        }else{
            currentRequest=null;
            bus.setCurrentRequest(null);
        }
    }

    public int getBusTraffic() {
        return busTraffic;
    }
}
