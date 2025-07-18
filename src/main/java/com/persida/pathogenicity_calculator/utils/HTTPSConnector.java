package com.persida.pathogenicity_calculator.utils;

import com.persida.pathogenicity_calculator.utils.constants.Constants;
import org.apache.log4j.Logger;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.zip.GZIPInputStream;

public class HTTPSConnector {
    private static Logger logger = Logger.getLogger(HTTPSConnector.class);

    private URL url;
    private HttpsURLConnection con;
    private BufferedReader inputBR;
    private int httpTimeout = 8000;
    private String inputLine;
    private String responseStr;

    public String sendHttpsRequest(String urlString, String method, String parametares, HashMap<String,String> httpProperties){
        if(urlString == null){
            return null;
        }

        byte[] postData = null;
        if(method.equals(Constants.HTTP_POST)){
            postData = parametares.getBytes(StandardCharsets.UTF_8);
        }else if(method.equals(Constants.HTTP_GET) && parametares != null && !parametares.isEmpty()){
            urlString = urlString+"?"+parametares;
        }

        try{
            url = new URL(urlString);
            con = (HttpsURLConnection) url.openConnection();
            con.setRequestMethod(method);
            con.setConnectTimeout(httpTimeout);
            //con.setReadTimeout(httpTimeout+15000);
            if(httpProperties != null && httpProperties.size() > 0){
                setAdditionalRequestProperties(con, httpProperties);
            }
            con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
            con.setRequestProperty("Accept-Charset", "utf-8, iso-8859-1;q=0.5");
            con.setRequestProperty("Accept-Encoding", "gzip, deflate, sdch, compress, identity");
            con.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");

            if(method.equals(Constants.HTTP_POST)){
                configurePostRequaest(con);
            }

            if(method.equals(Constants.HTTP_POST) && postData != null){
                responseStr =  executePostMethod(con, postData);
            }else if(method.equals(Constants.HTTP_GET)){
                //process input stream directly
                responseStr = processInputStream(con);
            }

        }catch(Exception e){
            String stackTraceStr = StackTracePrinter.printStackTrace(e);
            logger.error(stackTraceStr);
            System.out.println(stackTraceStr);
        }finally{
            closeHttpsConnection();
            return responseStr;
        }
    }

    private void setAdditionalRequestProperties(HttpURLConnection con, HashMap<String,String> httpProperties){
        Iterator<String> iter = httpProperties.keySet().iterator();
        while(iter.hasNext()){
            String httpProperty = iter.next();
            String properyValue = httpProperties.get(httpProperty);
            con.setRequestProperty(httpProperty, properyValue);
        }
    }

    private void configurePostRequaest(HttpsURLConnection con) throws Exception{
        con.setDoOutput(true);
        //con.setUseCaches(false);
        //con.setInstanceFollowRedirects(false);
    }

    private String executePostMethod(HttpsURLConnection con, byte[] postData) throws Exception{
        //write post data first
        con.getOutputStream().write(postData);
        return processInputStream(con);
    }

    private String processInputStream(HttpsURLConnection con) throws Exception{
        if(con.getContentEncoding() != null &&
                con.getContentEncoding().equalsIgnoreCase("gzip")){
            inputBR = new BufferedReader(
                    new InputStreamReader(
                            new GZIPInputStream(con.getInputStream()), Constants.UTF8));
        }else{
            inputBR = new BufferedReader(
                    new InputStreamReader(con.getInputStream(), Constants.UTF8));
        }

        int responseCode = con.getResponseCode();

        if(responseCode == 200){
            return getResponseAsString(inputBR, con);
        }else{
            logger.error("Received HTTP code: "+responseCode);
            return null;
        }
    }

    private String getResponseAsString(BufferedReader inputBR, HttpsURLConnection con) throws Exception{
        StringBuffer response = new StringBuffer();
        while((inputLine = inputBR.readLine()) != null){
            response.append(inputLine);
        }
        return response.toString();
    }

    private void closeHttpsConnection(){
        try{
            inputBR.close();
        }catch(Exception e){}
        if(con != null){
            con.disconnect();
        }
    }
}
