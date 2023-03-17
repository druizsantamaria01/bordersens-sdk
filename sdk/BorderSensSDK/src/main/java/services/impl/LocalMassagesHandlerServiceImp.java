package services.impl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.microsoft.azure.sdk.iot.provisioning.security.SecurityProvider;
import okhttp3.*;
import org.apache.commons.lang3.exception.ExceptionUtils;
import services.LocalMassagesHandlerService;
import sdkutils.Utilities;

import java.util.HashMap;
import java.util.Map;

public class LocalMassagesHandlerServiceImp implements LocalMassagesHandlerService {

    private static LocalMassagesHandlerServiceImp instance;

    private static final String KO = "KO";
    private static final String OK = "OK";

    public static LocalMassagesHandlerServiceImp getInstance(){
        if (instance == null)
            instance = new LocalMassagesHandlerServiceImp();
        return instance;
    }

    private LocalMassagesHandlerServiceImp()
    {

    }

    @Override
    public Map<String, Object> sendSyncMessage(SecurityProvider securityProvider, String idDevice, String message) {
        Gson gson = new Gson();
        DockerContainerManagerImp.isDoingInference = true;
        JsonObject jMessage = gson.fromJson(message,JsonObject.class);
        Map<String, Object> finalResponse = new HashMap<>();
        String etlHost = Utilities.readProperty("local.service.etl.url","http://localhost:5001");
        String inferenceHost = Utilities.readProperty("local.service.inference.url","http://localhost:5002");
        OkHttpClient client = new OkHttpClient().newBuilder().build();
        MediaType mediaType = MediaType.parse("application/json");
        // Request ETL
        RequestBody bodyDevice = RequestBody.create(mediaType,message);
        Request request = new Request.Builder()
                .url(etlHost+"/bordersens-etl")
                .method("POST", bodyDevice)
                .addHeader("Content-Type", "application/json")
                .build();
        try {
            Response responseEtl = client.newCall(request).execute();
            if (responseEtl.code() == 200) {
                JsonObject jETLData = new Gson().fromJson(responseEtl.body().string(),JsonObject.class);
                if (jETLData.has("response") && jETLData.get("response").getAsJsonObject().has("status") && jETLData.get("response").getAsJsonObject().get("status").getAsString().toUpperCase().equals("OK")) {
                    JsonObject jETLResponse = jETLData.get("response").getAsJsonObject();
                    RequestBody bodyInference = RequestBody.create(mediaType,jETLResponse.toString());
                    Request requestInference = new Request.Builder()
                            .url(inferenceHost+"/bordersens-inference")
                            .method("POST", bodyInference)
                            .addHeader("Content-Type", "application/json")
                            .build();
                    try {
                        Response responseInference = client.newCall(requestInference).execute();
                        if (responseInference.code() == 200) {
                            JsonObject jInferenceData = gson.fromJson(responseInference.body().string(),JsonObject.class);
                            Map<String, Object> response = gson.fromJson(jInferenceData.toString(), Map.class);
                            finalResponse = response;
                        } else {
                            Map<String,Object> errorMessage = buildResponseError(null, "INFERENCE","APIResponseError","Error: Fail in request to INFERENCE API in offline mode","");
                            finalResponse = buildResponseIfError(jMessage,errorMessage);
                        }
                    } catch (Exception e) {
                        Map<String,Object> errorMessage = buildResponseError(e, "INFERENCE","APIResponseError","Error: Fail in request to INFERENCE API in offline mode","");
                        finalResponse = buildResponseIfError(jMessage,errorMessage);
                    }
                } else {
                    JsonObject jErrorEtlData = gson.fromJson(jETLData.get("response_handler").toString(),JsonObject.class);
                    Map<String, Object> response = gson.fromJson(jErrorEtlData.toString(), Map.class);
                    finalResponse = response;
                }
            } else { // Error API ETL
                Map<String,Object> errorMessage = buildResponseError(null, "TRANSFORM","APIResponseError","Error: Fail in request to ETL API in offline mode","");
                finalResponse = buildResponseIfError(jMessage,errorMessage);
            }

        } catch (Exception e) {
            Map<String,Object> errorMessage = buildResponseError(e, "TRANSFORM","APIResponseError","Error: Fail in request to ETL API in offline mode","");
            finalResponse = buildResponseIfError(jMessage,errorMessage);
        }
        DockerContainerManagerImp.isDoingInference = false;
        return finalResponse;
    }


    private Map<String, Object> buildResponseError(Exception e,String errorType,String errorName, String errorDetail, String cause) {
        Map<String, Object> responseError = new HashMap<>();
        responseError.put("errorType",errorType);
        if (e != null) { // Si es una excepción
            responseError.put("errorName",e.toString());
            Map<String, Object> errorDetails = new HashMap<>();
            errorDetails.put("error",e.getMessage());
            errorDetails.put("cause",cause);
            errorDetails.put("trace", ExceptionUtils.getStackTrace(e));
            responseError.put("details",errorDetails);
        } else { // Si no es una excepción
            responseError.put("errorName",errorName);
            Map<String, Object> errorDetails = new HashMap<>();
            errorDetails.put("error",errorDetail);
            errorDetails.put("cause",cause);
            errorDetails.put("trace", null);
        }
        return responseError;
    };

    private Map<String, Object> buildResponseIfError(JsonObject jData, Map<String, Object> errorMessage) {
        Map<String, Object> response = new HashMap<>();
        response.put("error",errorMessage);
        if (jData.has("sensor")) {
            response.put("id_device",jData.get("sensor").getAsString());
        }
        if (jData.has("value")) {
            response.put("id_sample",jData.get("value").getAsString());
        }
        response.put("inference",null);
        response.put("status","KO");
        if (jData.has("steps")) {
            JsonObject jSteps = jData.get("steps").getAsJsonObject();
            if (jSteps!=null) {
                response.put("steps",new Gson().fromJson(jSteps,Map.class));
            } else {
                response.put("steps",null);
            }
        } else {
            response.put("steps",null);
        }
        return response;
    }
}
