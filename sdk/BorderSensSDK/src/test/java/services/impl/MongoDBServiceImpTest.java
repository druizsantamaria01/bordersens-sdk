package services.impl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MongoDBServiceImpTest {

    MongoDBServiceImp mongo = MongoDBServiceImp.getInstance();


    @Test
    void getAllItemsInCollection() {
        JsonArray jResults = mongo.getAllItemsInCollection("DeviceTelemetry");
        Assertions.assertTrue(true);
    }

    @Test
    void getItemsInCollectionWithFilter() {
        Map<String,Object> filters = new HashMap<>();
        filters.put("id","device-1-bs");
        filters.put("version","v1");
        JsonArray jResults = mongo.getItemsInCollectionWithFilter("DeviceTelemetry",filters);
        Assertions.assertTrue(true);
    }

    @Test
    void deleteItemById() {
        mongo.deleteItem("DeviceTelemetry","63c93b8264a3d86f157da986");
    }

    @Test
    void CRUDItem() {

        // Create
        String collectionName = "MinMax";
        JsonObject jItem = new Gson().fromJson("{\"id\":\"prueba-borrar\",\"name\":\"created\",\"values\":{\"CH1\":{\"min\":-0.19916353576850243,\"max\":45.82042092210454},\"CH2\":{\"min\":-0.00876349990093396,\"max\":56.490663886953385},\"CH3\":{\"min\":-6.616710965288604,\"max\":10.171093360477684},\"CH4\":{\"min\":-1.8144724203359885,\"max\":12.225809841244775},\"CH5\":{\"min\":-3.1157769031720193,\"max\":17.683852399804564},\"CH6\":{\"min\":-1.6756505047258372,\"max\":34.383788760421865}}}", JsonObject.class);
        Map<String,Object> filters = new HashMap<>();
        filters.put("id","prueba-borrar");
        mongo.upsertItem(collectionName,filters,jItem);
        JsonArray jItems = mongo.getItemsInCollectionWithFilter(collectionName,filters);
        Assertions.assertTrue(jItems.size()>0 && jItems.get(0).getAsJsonObject().has("name") && jItems.get(0).getAsJsonObject().get("name").getAsString().equals(jItem.get("name").getAsString()));
        // Update
        jItem.addProperty("name","updated");
        mongo.upsertItem(collectionName,filters,jItem);
        jItems = mongo.getItemsInCollectionWithFilter(collectionName,filters);
        Assertions.assertTrue(jItems.size()>0 && jItems.get(0).getAsJsonObject().has("name") && jItems.get(0).getAsJsonObject().get("name").getAsString().equals(jItem.get("name").getAsString()));
        // Delete
        int deleted = mongo.deleteItems(collectionName,filters);
        jItems = mongo.getItemsInCollectionWithFilter(collectionName,filters);
        Assertions.assertTrue(deleted > 0 && jItems.size() == 0);
    }
}