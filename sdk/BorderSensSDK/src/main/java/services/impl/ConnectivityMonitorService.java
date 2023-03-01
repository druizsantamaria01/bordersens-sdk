package services.impl;

import events.InternetStateEvent;

import java.io.IOException;
import java.net.*;
import java.util.*;


public class ConnectivityMonitorService {

    private static ConnectivityMonitorService instance;
    private boolean isConected;

    private List<InternetStateEvent> listeners;

    public static ConnectivityMonitorService getInstance() {
        if (instance == null) {
            instance = new ConnectivityMonitorService();
        }
        return instance;
    }

    public static ConnectivityMonitorService getInstance(InternetStateEvent listener) {
        if (instance == null) {
            instance = new ConnectivityMonitorService();
        }
        instance.listeners.add(listener);
        return instance;
    }

    public ConnectivityMonitorService() {
        this.isConected = false;
        this.listeners = new ArrayList<>();
        new Timer().schedule(new CheckConectivityTask(this),0,2000);
    }

    public void subscribeEvents(InternetStateEvent listener) {
        this.listeners.add(listener);
    }

    public void unsubscribeEvents(InternetStateEvent listener) {
        this.listeners.remove(listener);
    }

    public boolean isConected() {
        if (!isConected)
            isConected = isConnected();
        return isConected;
    }

    public boolean isConnected(){
        boolean connected;
        try {
            InetAddress address = InetAddress.getByName("www.google.es");
            connected = address.isReachable(10000);
        } catch (IOException e) {
            connected = false;
        }
        return connected;
    }

    class CheckConectivityTask extends TimerTask {

        private ConnectivityMonitorService cms;

        public CheckConectivityTask(ConnectivityMonitorService cms) {
            this.cms = cms;
        }

        @Override
        public void run() {
            boolean connected;
            try {
                InetAddress address = InetAddress.getByName("www.google.es");
                connected = address.isReachable(10000);
            } catch (IOException e) {
                connected = false;
            }
            if (connected != this.cms.isConected && listeners.size()>0) {
                for (InternetStateEvent listener : listeners) {
                    listener.onChangeConnectionState(connected);
                }
            }
            this.cms.isConected = connected;
        }


    }
}


