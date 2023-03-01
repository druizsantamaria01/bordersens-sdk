package events;

import com.google.gson.JsonObject;

public interface SynchronizeDataEvent {

    void OnDataSynchronizedIsDone(JsonObject jReponse);
}
