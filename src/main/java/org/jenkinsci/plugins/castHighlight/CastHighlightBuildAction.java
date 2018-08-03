 
package org.jenkinsci.plugins.castHighlight;

import hudson.model.AbstractBuild;
import hudson.model.Action;
import java.util.Base64;
import java.net.*;
import java.io.*;
import java.util.Set;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import net.sf.json.JSONArray;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class CastHighlightBuildAction implements Action {

    private String user;
    private String pass;
    private String appid;
    private String compid;
    private String serverurl;

    private AbstractBuild<?, ?> build;

    @Override
    public String getIconFileName() {
        return "/plugin/castHighlight/img/build-goals.png";
    }

    @Override
    public String getDisplayName() {
        return "Highlight Results";
    }
    
    public String getHighlightLogo() {
        return("<img src=\"https://www.casthighlight.com/wp-content/uploads/2016/03/CH_Logo_Tagline_Colored_LR.png\" alt=\"CAST Highlight\"><br>");
    }
    
    public String getHighlightResults() {
        String highlightAuth = Base64.getEncoder().encodeToString((user+":"+pass).getBytes()); //Encode for API call
        String outputMessage = "";
        try {
            URL url = new URL(serverurl+"/WS2/domains/"+compid+"/applications/"+appid); //Pray that serverurl is formatted correctly. 
            URLConnection uc = url.openConnection();
            uc.setRequestProperty("X-Requested-With", "Curl");
            uc.setRequestProperty("Authorization", "Basic "+highlightAuth);
            
            BufferedReader in = new BufferedReader(new InputStreamReader(uc.getInputStream()));

            String inputLine;
            while ((inputLine = in.readLine()) != null) { //Read each return line from URL request
                
                JSONObject collectiveJson = JSONObject.fromObject(inputLine);
                JSONObject metrics = JSONArray.fromObject(collectiveJson.get("metrics")).getJSONObject(0); //Everything useful is in metrics
                
                List<String> exclude = Arrays.asList( //Exclude these keys
                    "businessImpact", 
                    "roarIndex", 
                    "technicalDebt", 
                    "maintenanceRecordedFTE", 
                    "cloudReadyScan",
                    "cloudReadyDetail"
                );
                 List<String> mult = Arrays.asList( //Multiply value x100 (For highlight correct scale)
                    "softwareResiliency", 
                    "softwareAgility", 
                    "softwareElegance",
                    "boosters",
                    "blockers",
                    "cloudReady"
                );
                Map<String, String> keyChange = new HashMap<String, String>() { //Exceptions to the camelCase conversion, special key changes
                {
                    put("Cloud Ready", "CloudReady");
                    put("Boosters", "Cloud Boosters");
                    put("Blockers", "Cloud Blockers");
                    put("Roadblocks", "Cloud Roadblocks");
                }};

                final Set<String> keys = metrics.keySet();
                for (final String key : keys) {
                    String value = metrics.getString(key);
                    if (!exclude.contains(key)) {
                        String keyWords = camelCase(key);
                        if (keyChange.get(keyWords) != null) {
                            keyWords = keyChange.get(keyWords);
                        }
                        
                        if (isNumeric(value) && value.contains(".")) { //is a number and has decimal before rounding
                            double doubleValue = Double.parseDouble(value);
                            if (mult.contains(key)) {
                                doubleValue *= 100;
                            }
                            value = String.format("%.1f", doubleValue); //Round to one dec place (all nums with dec place)
                        }
                        outputMessage += formatKeyPairOutput(keyWords, value);
                    }
                }
                
                //CloudReady Details parsing below
                outputMessage += "<hr><h3>CloudReady Details</h3>"; 
                JSONObject cloudDetails = JSONArray.fromObject(metrics.get("cloudReadyDetail")).getJSONObject(0);
                outputMessage += formatKeyPairOutput("Technology", cloudDetails.getString("technology")); //Pull the Technology detected prior to rest of cloud details
                
                JSONArray innerCloudDetailsArray = JSONArray.fromObject(cloudDetails.get("cloudReadyDetails"));
                for (int i=0; i<innerCloudDetailsArray.size(); i++) { //Get every insight sent
                    JSONObject innerCloudDetails = innerCloudDetailsArray.getJSONObject(i); //Turn each insight into a JSONObject
                    if (innerCloudDetails.getBoolean("triggered")) { //If the insight is actually detected as "triggered"
                        JSONObject extendedCloudIdent = JSONObject.fromObject(innerCloudDetails.getString("cloudRequirement")); //Cloud indentification subsection
                        
                        outputMessage += "<br><a href="+extendedCloudIdent.getString("hrefDoc")+">"+
                            extendedCloudIdent.getString("display")+
                            " ["+extendedCloudIdent.getString("ruleType")+"]"+
                            "</a><br>"; //Format link with insight
                        
                        JSONArray filesArray = JSONArray.fromObject(innerCloudDetails.get("files"));
                        for (int f = 0; f < filesArray.size(); f++) {
                            if (!filesArray.getString(f).equals("null")) {
                                outputMessage += "<code>"+filesArray.getString(f)+"</code><br>"; //Print each file that its detected in
                            }
                        }
                        outputMessage += "<hr>"; //Break with line after each insight
                    }
                }

            }
        } catch(MalformedURLException e) {
            System.out.println(e);
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            outputMessage = "<code>"+sw.toString()+"</code>";
        } catch(IOException e) {
            System.out.println(e);
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            outputMessage = "<code>"+sw.toString()+"</code>";
        }
        return outputMessage;
    }
    
    public String camelCase(String input) {
        return(StringUtils.capitalize(StringUtils.join(StringUtils.splitByCharacterTypeCamelCase(input), " ")));
    }
    
    public String formatKeyPairOutput(String key, String value) {
        return("<b>" + key + "</b> : " + value + "<br>");
    }
    
    public static boolean isNumeric(String str) {
        try {
            double d = Double.parseDouble(str);
        } catch (NumberFormatException nfe) {
            return false;
        }
        return true;
    }
    
    @Override
    public String getUrlName() {
        return "highlight";
    }

    public int getBuildNumber() {
        return this.build.number;
    }

    public AbstractBuild<?, ?> getBuild() {
        return build;
    }

    CastHighlightBuildAction(final String user, final String pass, final String appid, final String compid, final String serverurl, final AbstractBuild<?, ?> build)
    {
        this.build = build;
        this.user = user;
        this.pass = pass;
        this.appid = appid;
        this.compid = compid;
        this.serverurl = serverurl;
    }
}