package bus;

import common.Clocked;


public final class Bus implements Clocked {

    private BusController busController;
    private Request currentRequest;

    public Bus() {
        this.currentRequest = null;
    }

    public void attachTo(BusController controller) {
        this.busController = controller;
    }

    @Override
    public void runForOneCycle() {
        if (currentRequest != null) {
            currentRequest.decrementCyclesToExecute();

            if (currentRequest.done()) {

                busController.alert();
            }
        }
    }

    public void setCurrentRequest(Request request) {
        this.currentRequest = request;
    }


}

