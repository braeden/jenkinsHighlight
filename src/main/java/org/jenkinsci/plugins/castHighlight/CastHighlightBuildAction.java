 
package org.jenkinsci.plugins.castHighlight;

import hudson.model.AbstractBuild;
import hudson.model.Action;
import java.util.Base64;
import java.net.*;
import java.io.*;
import java.util.Set;

import java.util.Iterator;
//import org.json.simple.JSONArray;
//import org.json.simple.JSONObject;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import net.sf.json.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

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
                
                for (final String key : keys) {
                    String value = metrics.getString(key);
                    outputMessage += key+" : "+value+"<br>";
                    //System.out.println(key);
                }
                //https://stackoverflow.com/questions/1568762/accessing-members-of-items-in-a-jsonarray-with-java
                //http://json-lib.sourceforge.net/apidocs/net/sf/json/JSONObject.html
                //http://json-lib.sourceforge.net/apidocs/net/sf/json/JSONArray.html
            }
        } catch(MalformedURLException e) {
            System.out.println(e);
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            outputMessage = sw.toString();
        } catch(IOException e) {
            System.out.println(e);
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            outputMessage = sw.toString();
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