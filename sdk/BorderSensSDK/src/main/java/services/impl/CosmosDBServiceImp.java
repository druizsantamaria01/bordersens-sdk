package services.impl;

import com.azure.cosmos.*;
import com.azure.cosmos.models.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import events.InternetStateEvent;
import org.json.JSONObject;
import sdkutils.Utilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CosmosDBServiceImp {


    private static  CosmosDBServiceImp instance;

    private CosmosClient client;

    private ConnectivityMonitorService connectivityMonitorService;

    private boolean isConnected = false;
    private Map<String,CosmosDatabase> databases;
    private Map<String,Map<String,CosmosContainer>> containers;

    public static CosmosDBServiceImp getInstance() {
        if (instance == null) {
            instance = new CosmosDBServiceImp();
        }
        return instance;
    }

    public CosmosDBServiceImp() {
        this.databases = new HashMap<>();
        this.containers = new HashMap<>();

        this.connectivityMonitorService = ConnectivityMonitorService.getInstance(new InternetStateEvent() {
            @Override
            public void onChangeConnectionState(boolean connected) {
                isConnected = connected;
                if (isConnected) {
                    connectClient();
                }
            }
        });
        this.isConnected = connectClient();
    }

    private boolean connectClient() {
        if (isConnected && this.connectivityMonitorService.isConected() && this.client == null) {
            try {
                this.client = new CosmosClientBuilder()
                        .endpoint(Utilities.readProperty("cosmos.host", null))
                        .key(Utilities.readProperty("cosmos.key", null))
                        .consistencyLevel(ConsistencyLevel.EVENTUAL)
                        .buildClient();
                isConnected = true;
            } catch (Exception e) {
                isConnected = false;
            }
        }

        return isConnected;
    }

    public CosmosDatabase getCosmosDatabase(String database, boolean createIfNotExist) {
        if (connectClient()) {
            if (!databases.containsKey(database)) {
                if (createIfNotExist == true) {
                    CosmosDatabaseResponse cdr = client.createDatabaseIfNotExists(database);
                    CosmosDatabase db = client.getDatabase(cdr.getProperties().getId());
                    databases.put(database, db);
                    containers.put(database, new HashMap<>());
                } else {
                    CosmosDatabase db = client.getDatabase(database);
                    if (db != null) {
                        databases.put(database, db);
                        containers.put(database, new HashMap<>());
                    }
                }
            }
            return databases.get(database);
        } else
            return null;
    }

    public CosmosContainer getCosmosContainer(String database, String container, boolean createIFNotExist) {
        if (connectClient()) {
            if (containers.containsKey(database) && containers.get(database).containsKey(container) && containers.get(database).get(container) != null) {
                return containers.get(database).get(container);
            } else {
                CosmosDatabase db = getCosmosDatabase(database, createIFNotExist);
                if (db != null) {
                    CosmosContainerProperties properties = new CosmosContainerProperties(container, "/value");
                    if (createIFNotExist) {
                        CosmosContainerResponse cosmosContainerResponse = db.createContainerIfNotExists(properties);
                    }
                    CosmosContainer cosmosContainer = db.getContainer(container);
                    if (cosmosContainer != null) {
                        containers.get(database).put(container, cosmosContainer);
                        return cosmosContainer;
                    } else {
                        return null;
                    }
                } else
                    return null;
            }
        } else {
            return null;
        }
    }

    public JsonArray getAllItemsByFilter(String database, String container, Map<String,String> filter) {
        if (connectClient()) {
            JsonArray jResponse = new JsonArray();
            CosmosContainer cc = getCosmosContainer(database, container, false);
            String sql = "SELECT * FROM c";
            if (filter != null && !filter.isEmpty()) {
                List<String> filterChunk = new ArrayList<>();
                for (Map.Entry<String, String> e : filter.entrySet()) {
                    filterChunk.add("c['" + e.getKey() + "'] = '" + e.getValue() + "'");
                }
                sql = sql + " WHERE " + String.join(" and ", filterChunk);
            }
            CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
            options.setMaxBufferedItemCount(100);
            options.setMaxDegreeOfParallelism(1000);
            options.setQueryMetricsEnabled(false);

            int error_count = 0;
            int error_limit = 10;

            String continuationToken = null;
            do {

                for (FeedResponse<JsonNode> pageResponse :
                        cc.queryItems(sql, options, JsonNode.class).iterableByPage(continuationToken, 1000)) {

                    continuationToken = pageResponse.getContinuationToken();

                    for (JsonNode item : pageResponse.getElements()) {
                        jResponse.add(new Gson().fromJson(item.toString(), JsonObject.class));
                    }
                }

            } while (continuationToken != null);

            return jResponse;
        } else
            return null;
    }

    public JsonArray upsertItem(String database, String container, Map<String,String> filter, JsonObject jItem) {
        if (connectClient()) {
            Map<String, Object> itemMap = new JSONObject(jItem.toString()).toMap();
            // JsonArray jItems = getAllItemsByFilter(database,container,filter);
            CosmosContainer c = getCosmosContainer(database, container, false);
            String partitionKey = itemMap.get("value").toString();


            JsonArray jItems = getAllItemsByFilter(database, container, filter);
            if (jItems.size() == 0) { // Creation
                c.createItem(itemMap);
            } else { // Update
                for (JsonElement jIt : jItems) {
                    String etag = jIt.getAsJsonObject().get("_etag").getAsString();
                    CosmosItemRequestOptions requestOptions = new CosmosItemRequestOptions();
                    requestOptions.setIfMatchETag(etag);
                    c.upsertItem(itemMap).getItem();

                }
            }
            return getAllItemsByFilter(database, container, filter);
        } else
            return null;
    }

}
