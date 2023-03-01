package services.impl;

import events.InternetStateEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConnectivityMonitorServiceTest {

    @Test
    void getInstance() {
        ConnectivityMonitorService.getInstance().subscribeEvents(new InternetStateEvent() {
            @Override
            public void onChangeConnectionState(boolean isConnected) {
                System.out.println("Change connection State:" + isConnected);
            }
        });
        try {
            Thread.sleep(200000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}