package model;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DataRepository {

    private String name;
    private boolean master;
    private String outerName;

    private String innerIdentifier;

    private String outerIdentifier;

    private List<String> synchronizablesItems;

    private String timestampInnerId;

    private String timestampOuterId;

    public DataRepository(JsonObject jData) {
        this.name = jData.get("name").getAsString();
        this.master = Boolean.parseBoolean(jData.get("master").getAsString());
        this.outerName = jData.get("outerName").getAsString();
        this.innerIdentifier = jData.get("innerIdentifier").getAsString();
        this.outerIdentifier = jData.get("outerIdentifier").getAsString();
        this.synchronizablesItems = new ArrayList<>();
        if (jData.has("synchronizablesItems")) {
            String[] splitedValues = jData.get("synchronizablesItems").getAsString().split(",");
            this.synchronizablesItems.addAll(Arrays.asList(splitedValues));
        }

        if (jData.has("timestampInnerId")) {
            this.timestampInnerId = jData.get("timestampInnerId").getAsString();
        }

        if (jData.has("timestampOuterId")) {
            this.timestampOuterId = jData.get("timestampOuterId").getAsString();
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isMaster() {
        return master;
    }

    public void setMaster(boolean master) {
        this.master = master;
    }

    public String getOuterName() {
        return outerName;
    }

    public void setOuterName(String outerName) {
        this.outerName = outerName;
    }

    public String getInnerIdentifier() {
        return innerIdentifier;
    }

    public void setInnerIdentifier(String innerIdentifier) {
        this.innerIdentifier = innerIdentifier;
    }

    public String getOuterIdentifier() {
        return outerIdentifier;
    }

    public void setOuterIdentifier(String outerIdentifier) {
        this.outerIdentifier = outerIdentifier;
    }

    public List<String> getSynchronizablesItems() {
        return synchronizablesItems;
    }

    public void setSynchronizablesItems(List<String> synchronizablesItems) {
        this.synchronizablesItems = synchronizablesItems;
    }

    public String getTimestampInnerId() {
        return timestampInnerId;
    }

    public void setTimestampInnerId(String timestampInnerId) {
        this.timestampInnerId = timestampInnerId;
    }

    public String getTimestampOuterId() {
        return timestampOuterId;
    }

    public void setTimestampOuterId(String timestampOuterId) {
        this.timestampOuterId = timestampOuterId;
    }


}
