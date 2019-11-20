package bus;

public final class Request {

    private final int senderId;
    private final BusEvent busEvent;
    private final int address;
    private boolean isDataRequest;



    private int cyclesToExecute;

    public void setCyclesToExecute(int cyclesToExecute) {
        this.cyclesToExecute = cyclesToExecute;
    }

    public boolean isDataRequest() {
        return isDataRequest;
    }

    public boolean done(){
        return this.cyclesToExecute == 0;
    }
    public void setDataRequest(boolean dataRequest) {
        isDataRequest = dataRequest;
    }

    public Request(int senderId, BusEvent busEvent, int address, int cyclesToExecute, boolean isDataRequest){
        this.senderId=senderId;
        this.address=address;
        this.isDataRequest = isDataRequest;
        this.cyclesToExecute=cyclesToExecute;
        this.busEvent=busEvent;
    }

    public int getCyclesToExecute(){
        return cyclesToExecute;
}
    public int getSenderId() {
        return senderId;
    }
    public void decrementCyclesToExecute(){
        cyclesToExecute--;
    }
    public BusEvent getBusEvent(){ return busEvent;}
    public int getAddress() {
        return address;
    }
}
