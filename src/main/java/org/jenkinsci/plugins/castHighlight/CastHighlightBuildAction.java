 
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

    public String getHighlightResults() {
        String highlightAuth = Base64.getEncoder().encodeToString((user+":"+pass).getBytes());
        String outputMessage = "";
        try {
            URL url = new URL(serverurl+"/WS2/domains/"+compid+"/applications/"+appid);
            URLConnection uc = url.openConnection();
            uc.setRequestProperty("X-Requested-With", "Curl");
            uc.setRequestProperty("Authorization", "Basic "+highlightAuth);
            
            BufferedReader in = new BufferedReader(new InputStreamReader(uc.getInputStream()));

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                
                JSONObject a = JSONObject.fromObject(inputLine);
                JSONObject metrics = JSONArray.fromObject(a.get("metrics")).getJSONObject(0);
                //String out = first.getString("totalLinesOfCode");
                //System.out.println(out);
                final Set<String> keys = metrics.keySet();
                List<String> exclude = Arrays.asList( //Exclude these keys
                    "businessImpact", 
                    "roarIndex", 
                    "technicalDebt", 
                    "maintenanceRecordedFTE", 
                    "cloudReadyScan",
                    "cloudReadyDetail"
                );
                 List<String> mult = Arrays.asList( //Multiply value x100
                    "softwareResiliency", 
                    "softwareAgility", 
                    "softwareElegance", 
                    "maintenanceRecordedFTE", 
                    "boosters",
                    "blockers",
                    "cloudReady"
                );
                Map<String, String> keyChange = new HashMap<String, String>() { //Modfiy these from CamelCase conversion
                {
                    put("Cloud Ready", "CloudReady");
                    put("Boosters", "Cloud Boosters");
                    put("Blockers", "Cloud Blockers");
                    put("Roadblocks", "Cloud Roadblocks");
                }};

                for (final String key : keys) {
                    String value = metrics.getString(key);
                    if (!exclude.contains(key)) {
                        String keyWords = StringUtils.capitalize(StringUtils.join(StringUtils.splitByCharacterTypeCamelCase(key), " ")); // Your Camel Case Text
                        if (keyChange.get(keyWords) != null) {
                            keyWords = keyChange.get(keyWords);
                        }
                        if (mult.contains(key)) {
                            double doubleValue = Double.parseDouble(value);
                            doubleValue *= 100;
                            value = String.format("%.1f", doubleValue);
                        }
                        outputMessage += "<b>"+keyWords+"</b> : "+value+"<br>";
                    }
                    System.out.println(key);
                }
            }
        } catch(MalformedURLException e) {
            System.out.println(e);
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            outputMessage = "<tt>"+sw.toString()+"</tt>";
        } catch(IOException e) {
            System.out.println(e);
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            outputMessage = "<tt>"+sw.toString()+"</tt>";
        }
        return outputMessage;
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