package bus;

public final class Request {

    private boolean isDataRequest;
    private boolean senderNeedsData;
    private int cyclesToExecute;
    private final int senderId;
    private final BusEvent busEvent;
    private final int address;

    public Request(int senderId, BusEvent busEvent, int address, int cyclesToExecute, boolean senderNeedsData) {
        this.senderId = senderId;
        this.address = address;
        this.senderNeedsData = senderNeedsData;
        this.isDataRequest = false;
        this.cyclesToExecute = cyclesToExecute;
        this.busEvent = busEvent;
    }

    public boolean senderNeedsData() {
        return senderNeedsData;
    }

    public void setCyclesToExecute(int cyclesToExecute) {
        this.cyclesToExecute = cyclesToExecute;
    }

    public boolean isDataRequest() {
        return isDataRequest;
    }

    public void setSenderNeedsData(boolean senderNeedsData) {
        this.senderNeedsData = senderNeedsData;
    }

    public boolean done() {
        return this.cyclesToExecute <= 0;
    }

    public void setDataRequest(boolean dataRequest) {
        isDataRequest = dataRequest;
    }

    public int getCyclesToExecute() {
        return cyclesToExecute;
    }

    public int getSenderId() {
        return senderId;
    }

    public void decrementCyclesToExecute() {
        cyclesToExecute--;
    }

    public BusEvent getBusEvent() {
        return busEvent;
    }

    public int getAddress() {
        return address;
    }
}
