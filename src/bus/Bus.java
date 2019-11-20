package bus;

import cache.Cache;
import common.Clocked;
import common.Constants;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;



public final class Bus implements Clocked {
// =======
// public class Bus implements Clocked {
//     private boolean isBusy;
//     private List<Cache> caches;
//     private List<Request> requests;
//     private Request processingRequest;
//     private int busTraffic;
//     private int nbUpdates;
//     private int nbInvalidates;

//     public Bus() {
//         caches = new ArrayList<>();
//         requests = new LinkedList<>();
//         isBusy = false;
//         busTraffic = 0;
//         nbUpdates = 0;
//         nbInvalidates = 0;
// >>>>>>> master

    private BusController busController;
    private Request currentRequest;



    public Bus(){
        this.currentRequest = null;

//     public boolean isBusy() {
//         return isBusy;
//     }

//     public int getBusTraffic() {
//         return busTraffic;
//     }

//     public int getNbInvalidates() {
//         return nbInvalidates;
//     }

//     public int getNbUpdates() {
//         return nbUpdates;
//     }

//     public void addCache(Cache cache) {
//         caches.add(cache);
//         cache.linkBus(this);

    }

    public void attachTo (BusController controller){
        this.busController = controller;
    }

    @Override
    public void runForOneCycle() {
        if (currentRequest != null){
            currentRequest.decrementCyclesToExecute();
        }
        if (currentRequest.done()){
            busController.alert();
        }
    }


    public void setCurrentRequest(Request request){
        this.currentRequest = request;
    }

public boolean askOthers(int senderId, int address) {

    for (Cache cache : caches) {
        if (cache.getId() != senderId && cache.cacheHit(address))
            return true;

//     private void executeTransition(Request processingRequest) {
//         boolean sharedSignal = askOthers(processingRequest.getSenderId(), processingRequest.getAddress());
//         int blockSize = caches.get(processingRequest.getSenderId()).getBlockSize();
//         switch (processingRequest.getBusEvent()) {
//             case BusRdX:
//                 if (sharedSignal) {
//                     nbInvalidates++;
//                 }
//             case BusRd:
//             case Flush:
//                 busTraffic += blockSize;
//                 break;
//             case BusUpd:
//                 busTraffic += Constants.BYTES_IN_WORD;
//                 nbUpdates++;
//                 break;
//         }

//         for (Cache cache : caches) {
//             cache.notifyChange(processingRequest);
//         }
//     }

//     public void flushClean(DataRequest request) { //reading data from E
//         requests.add(0, request);
//     }

//     public void flushMemory(DataRequest request) { //reading data from M
//         requests.add(0, request);
//     }

//     public boolean askOthers(int senderId, int address) {

//         for (Cache cache : caches) {
//             if (cache.getId() != senderId && cache.cacheHit(address))
//                 return true;
//         }
//         return false;

    }
    return false;
}

}

