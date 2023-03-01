package events;

public interface InternetStateEvent {

    void onChangeConnectionState(boolean isConnected);
}
