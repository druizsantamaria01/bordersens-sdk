package services.impl;


import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.DeleteResult;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import sdkutils.Utilities;

import java.util.*;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

public class MongoDBServiceImp {

    private static MongoDBServiceImp instance;
    private MongoClient client;
    private String host;
    private String port;
    private String user;
    private String password;
    private String database;

    private MongoDatabase db;


    public static MongoDBServiceImp getInstance() {
        if (instance == null) {
            instance = new MongoDBServiceImp();
        }
        return instance;
    }

    public MongoDBServiceImp() {
        host = Utilities.readProperty("mongo.host",null);
        port = Utilities.readProperty("mongo.port",null);
        database = Utilities.readProperty("mongo.database",null);
        user = Utilities.readProperty("mongo.user",null);
        password = Utilities.readProperty("mongo.password",null);
        String mongoClientURI = "mongodb://" + user + ":" + password + "@" + host + ":" + port;
        client = new MongoClient(new MongoClientURI("mongodb://"+user+":"+password+"@"+host+":"+port+"/"+user+"?connectTimeoutMS=10000&authSource=admin&authMechanism=SCRAM-SHA-1"));
        db = client.getDatabase(this.database);
    }

    public JsonArray getAllItemsInCollection(String collection) {
        MongoCollection<Document> col = db.getCollection(collection);
        FindIterable<Document> documents = col.find();
        JsonArray jItems = new JsonArray();
        for (Document d: documents) {
            JsonObject jItem = new JsonParser().parse(com.mongodb.util.JSON.serialize(d)).getAsJsonObject();
            jItems.add(jItem);
        }
        return jItems;
    }

    public JsonArray getItemsInCollectionWithFilter(String collection, Map<String,Object> filters) {
        List<Bson> filtersList = new ArrayList<>();
        for (Map.Entry<String, Object> f : filters.entrySet()) {
            filtersList.add(eq(f.getKey(), f.getValue()));
        }
        Bson andBson = and(filtersList);
        MongoCollection<Document> col = db.getCollection(collection);
        FindIterable<Document> documents = col.find(andBson);
        JsonArray jItems = new JsonArray();
        for (Document d: documents) {
            JsonObject jItem = new JsonParser().parse(com.mongodb.util.JSON.serialize(d)).getAsJsonObject();
            jItems.add(jItem);
        }
        return jItems;
    }

    public void deleteItem(String collection, String _id) {
        MongoCollection<Document> col = db.getCollection(collection);
        DeleteResult res = col.deleteOne(new Document("_id", new ObjectId(_id)));
    }

    public void deleteItem(String collection, JsonObject jItem) {
        MongoCollection<Document> col = db.getCollection(collection);
        DeleteResult res = col.deleteOne(new Document("_id", new ObjectId(getObjectId(jItem))));
    }

    public String getObjectId(JsonObject jItem) {
        if (jItem.has("_id") && jItem.get("_id").getAsJsonObject().has("$oid"))
            return jItem.get("_id").getAsJsonObject().get("$oid").getAsString();
        return null;
    }

    public void upsertItem(String collection, Map<String,Object> filters, JsonObject jItem) {
        Document doc = Document.parse(jItem.toString());
        MongoCollection<Document> col = db.getCollection(collection);
        JsonArray jItems = getItemsInCollectionWithFilter(collection,filters);
        if (jItems.size()>0) { // Update
            for (JsonElement it : jItems) {
                String _id = getObjectId(it.getAsJsonObject());
                Document matchFields = new Document();
                matchFields.put("_id", new ObjectId(_id));
                col.updateOne(matchFields,new Document("$set", doc));
            }
        } else {
            ObjectId objId = new ObjectId();
            doc.put("_id", objId);
            col.insertOne(doc);
        }
    }

    public int deleteItems(String collection, Map<String,Object> filters){
        int deleteCount = 0;
        MongoCollection<Document> col = db.getCollection(collection);
        for ( JsonElement jItem : getItemsInCollectionWithFilter(collection,filters)) {
            String _id = getObjectId(jItem.getAsJsonObject());
            DeleteResult result = col.deleteOne(new Document("_id", new ObjectId(_id)));
            deleteCount += result.getDeletedCount();
        }
        return deleteCount;
    }

    public long countDocuments(String collection) {
        MongoCollection<Document> col = db.getCollection(collection);
        return col.countDocuments();
    }


}
