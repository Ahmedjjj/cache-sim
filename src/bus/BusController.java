package bus;


import cache.Cache;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public final class BusController {

    private Bus bus;
    private Request currentRequest;
    private int busTraffic;
    private final List<Cache> caches;
    private final Queue<Cache> cacheQueue;

    public BusController() {
        this.caches = new LinkedList<>();
        this.currentRequest = null;
        this.cacheQueue = new LinkedList<>();
        busTraffic = 0;

    }

    public void attachTo(Bus bus) {
        this.bus = bus;
    }

    public void attach(Cache cache) {
        this.caches.add(cache);
        cache.linkBusController(this);
    }

    public void alert() {
        assert currentRequest != null;

        int extra_cycles = 0;
        for (Cache c : caches) {
            int extra = c.notifyRequestAndGetExtraCycles(currentRequest);
            if (extra > 0) {
                extra_cycles = extra;
            }
        }
        if (extra_cycles > 0 && currentRequest.senderNeedsData()) {
            busTraffic += extra_cycles;
            currentRequest.setCyclesToExecute(extra_cycles);
            currentRequest.setDataRequest(true);
        } else {
            setNewRequest();
        }
    }

    public boolean checkExistenceInOtherCaches(int senderId, int address) {
        return caches.stream().anyMatch(c -> c.getId() != senderId && c.cacheHit(address));
    }

    public void queueUp(Cache cache) {

        assert !cacheQueue.contains(cache);

        if (cacheQueue.isEmpty() && currentRequest == null) {
            this.currentRequest = cache.getRequest();
            this.bus.setCurrentRequest(currentRequest);
        } else {
            this.cacheQueue.add(cache);
        }
    }

    public int getBusTraffic() {
        return busTraffic;
    }

    private void setNewRequest() {

        if (!cacheQueue.isEmpty()) {
            Cache cache = cacheQueue.poll();
            this.currentRequest = cache.getRequest();
            this.bus.setCurrentRequest(currentRequest);
        } else {
            currentRequest = null;
            bus.setCurrentRequest(null);
        }
    }


}
