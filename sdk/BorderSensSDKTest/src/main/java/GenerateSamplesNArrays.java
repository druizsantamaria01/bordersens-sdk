import com.google.gson.JsonObject;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.URL;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;


public class GenerateSamplesNArrays {
    static ClassLoader classLoader = GenerateSamplesNArrays.class.getClassLoader();
    final static int ARRAY_SIZE = 6;
    public static void main(String[] args) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        URL urlBase = loader.getResource("samples");
        String samplesPath = urlBase.getPath();
        List<JSONObject> jSamples = new ArrayList<>();
        Map<String,JSONObject> mArrayExamples = new HashMap<>();
        for (File f : new File(samplesPath).listFiles()) {
            JSONObject jSample = getJsonContentFromFile(f);
            jSamples.add(jSample);
            mArrayExamples.put(f.getName(),generateNArraysSample(jSample,ARRAY_SIZE));
        }
        writeInFolder(ARRAY_SIZE,mArrayExamples);


    }

    public static JSONObject generateNArraysSample(JSONObject jSample, int nodes) {
        JSONObject jSampleNArray = new JSONObject(jSample.toString());
        JSONArray jNodes = new JSONArray();
        int l = jSample.getJSONArray("nodes").length();
        for (int i = 0 ; i < nodes ; i++) {
            if (i <= l-1) {
                jNodes.put(jSample.getJSONArray("nodes").get(i));
            } else {
                int ind = i%l;
                JSONObject jNode = jSample.getJSONArray("nodes").getJSONObject(ind);
                JSONObject jNodeAux = new JSONObject(jNode.toString());
                jNodeAux.put("id",i);
                jNodes.put(jNodeAux);
            }
        }
        jSampleNArray.put("nodes",jNodes);
        return jSampleNArray;
    }

    public static void writeInFolder(int arraySize,Map<String,JSONObject> newSamples) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        URL urlBase = loader.getResource(".");
        String samplesFolder = urlBase.getPath()+String.format("samples-%s",arraySize);
        try {
            FileUtils.deleteDirectory(new File(samplesFolder));
            Files.createDirectories(new File(samplesFolder).toPath());
            for (Map.Entry<String,JSONObject> e : newSamples.entrySet()) {
                writeSampleFile(samplesFolder+"/"+e.getKey(),e.getValue().toString());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    public static JSONObject getJsonContentFromFile(File f) {
        String fileContent = "";
        StringBuilder contentBuilder = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(f)))
        {

            String sCurrentLine;
            while ((sCurrentLine = br.readLine()) != null)
            {
                contentBuilder.append(sCurrentLine).append("\n");
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        fileContent = contentBuilder.toString();
        return new JSONObject(fileContent);
    }

    public static void writeSampleFile(String path,String content) {
        BufferedWriter output = null;
        try {
            File file = new File(path);
            output = new BufferedWriter(new FileWriter(file));
            output.write(content);
        } catch ( IOException e ) {
            e.printStackTrace();
        } finally {
            if ( output != null ) {
                try {
                    output.close();
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        }
    }


}
