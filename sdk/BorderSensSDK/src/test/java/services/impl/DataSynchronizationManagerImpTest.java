package services.impl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import events.SynchronizeDataEvent;
import org.junit.jupiter.api.Test;
import services.DataSynchronizationManager;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DataSynchronizationManagerImpTest {

    DataSynchronizationManagerImp dsm = DataSynchronizationManagerImp.getInstance("bs-amsterdam-device-1");

    @Test
    void startSynchronization() throws InterruptedException {
        dsm.synchronizeOnce();
        dsm.startSynchronization(10);
        SynchronizeDataEvent listener = new SynchronizeDataEvent() {
            @Override
            public void OnDataSynchronizedIsDone(JsonObject jReponse) {
                System.out.println("Recibed Synchronization Event: "+ jReponse.toString());
            }
        };
        dsm.subscribeEvents(listener);
        Thread.sleep(150000);
        dsm.stopSynchronization(0);
        dsm.unsubscribeEvents(listener);
    }
}