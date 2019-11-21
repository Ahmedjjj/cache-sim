package bus;

import cache.Cache;
import common.Clocked;
import common.Constants;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;



public final class Bus implements Clocked {


     private int nbUpdates;
     private int nbInvalidates;
    private BusController busController;
    private Request currentRequest;



    public Bus() {
        this.currentRequest = null;
    }

     public int getBusTraffic() {
         return busController.getBusTraffic();
     }

     public int getNbInvalidates() {
         return nbInvalidates;
     }

     public int getNbUpdates() {
         return nbUpdates;
     }





    public void attachTo (BusController controller){
        this.busController = controller;
    }

    @Override
    public void runForOneCycle() {
        if (currentRequest != null) {
            currentRequest.decrementCyclesToExecute();

            if (currentRequest.done()) {
              if(currentRequest.getBusEvent()==BusEvent.BusUpd)
                  nbUpdates++;
              else if(currentRequest.getBusEvent()==BusEvent.BusRdX)
                  nbInvalidates++;
                busController.alert();
            }
        }
    }


    public void setCurrentRequest(Request request){
        this.currentRequest = request;
    }


}

