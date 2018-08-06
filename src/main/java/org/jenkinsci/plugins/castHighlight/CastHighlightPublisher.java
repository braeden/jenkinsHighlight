package org.jenkinsci.plugins.castHighlight;


import hudson.Launcher;
import hudson.Extension;
import hudson.tasks.*;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest; 
import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import hudson.EnvVars;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.sf.json.JSONArray;
import org.apache.commons.lang.StringUtils;
import java.util.concurrent.TimeUnit;

/*
TODO: 
- Disable inputs fields that shouldn't be used 
- Add string queries to all local factors, validate project path, id's etc. 
- Make directories folder selectable
*/




/**
 * Sample {@link Publisher}.
 * <p/>
 * <p/>
 * When the user configures the project and enables this publisher,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link CastHighlightPublisher} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields (like {@link #name})
 * to remember the configuration.
 * <p/>
 * <p/>
 * When a build is performed and is complete, the {@link #perform(AbstractBuild, Launcher, BuildListener)}
 * method will be invoked.
 *
 * @author Kohsuke Kawaguchi
 */
public class CastHighlightPublisher extends Recorder {

    private String filepath;
    private final String appid;
    private final String filepathOutput;
    private final String extrafields;
    private final String login;
    private final String password;
    private final String compid;
    private String snapshotlabel;
    private boolean useonline;
    private boolean serverreq;

    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public CastHighlightPublisher(String filepath, String appid, String filepathOutput, String extrafields, String login, String password, String compid, String snapshotlabel, boolean serverreq, boolean useonline) {
        this.filepath = filepath;
        this.appid = appid;
        this.filepathOutput = filepathOutput;
        this.extrafields = extrafields;
        this.login = login;
        this.password = password;
        this.serverreq = serverreq;
        this.compid = compid;
        this.useonline = useonline;
        this.snapshotlabel = snapshotlabel;
        
    }

    /**
     * We'll use this from the <tt>config.jelly</tt>.
     */
    public String getFilepath() {
        return filepath;
    }
    public String getFilepathOutput() {
        return filepathOutput;
    }
    public String getAppid() {
        return appid;
    }
    public String getExtrafields() {
        return extrafields;
    }
    public String getLogin() {
        return login;
    }
    public String getPassword() {
        return password;
    }
    public String getCompid() {
        return compid;
    }
    public String getSnapshotlabel() {
        return snapshotlabel;
    }
    public boolean getUseonline() {
        return useonline;
    }
    public boolean getPullserver() {
        return serverreq;
    }
    
    
    
    public String highlightResults(final String user, final String pass, final String appid, final String compid, final String serverurl, BuildListener listener) {
        String highlightAuth = Base64.getEncoder().encodeToString((user+":"+pass).getBytes()); //Encode for API call - base64(user:pass)
        String outputMessage = "";
        
        try {
            listener.getLogger().println("-----\nPulling Highlight Results from server:");
            JSONObject metrics = new JSONObject(true); //Initiate a null JSON object (for while() loop)
            int attempts = 1;
            int waitTime = 1;
            int maxAttempts = 15;
            while(metrics.isNullObject() && attempts < maxAttempts) { //Server is slow on it's processing the metrics we need. Keep trying till max...
                try { 
                    TimeUnit.SECONDS.sleep(waitTime);
                    listener.getLogger().println("Waiting "+waitTime+" second(s) for server... ["+attempts+"]");
                } catch(InterruptedException e) {
                    System.out.println(e);
                }
                URL url = new URL(serverurl+"/WS2/domains/"+compid+"/applications/"+appid); //Pray that serverurl is formatted correctly. 
                URLConnection uc = url.openConnection();
                uc.setRequestProperty("X-Requested-With", "Curl"); //Spoof a curl request...
                uc.setRequestProperty("Authorization", "Basic "+highlightAuth);

                BufferedReader in = new BufferedReader(new InputStreamReader(uc.getInputStream()));

                String inputLine;
                while ((inputLine = in.readLine()) != null) { //Read each return line from URL request

                    JSONObject collectiveJson = JSONObject.fromObject(inputLine);
                    metrics = JSONArray.fromObject(collectiveJson.get("metrics")).getJSONObject(0); //Everything useful is in metric
                    //System.out.println(collectiveJson);
                }
                attempts++;
            }           

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
            if (!metrics.isNullObject()) {  //If attempts are overtried, make sure not to point to a null object
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
                JSONArray cloudDetailsWhole = JSONArray.fromObject(metrics.get("cloudReadyDetail"));
                for (int j=0; j<cloudDetailsWhole.size(); j++) { //Iterate though each technology (I think)
                    JSONObject cloudDetails = cloudDetailsWhole.getJSONObject(j);

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
            } else {
                //Server failed to respond with any metric data after 15 tries over 15 secs.
                outputMessage += "Server failed to respond with valid metrics after " +maxAttempts+ " attempts";
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

    public static boolean isNumeric(String str) { //Thank you stackoverflow
        try {
            double d = Double.parseDouble(str);
        } catch (NumberFormatException nfe) {
            return false;
        }
        return true;
    }

    
    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        String message="";
        final EnvVars env = build.getEnvironment(listener);

        JSONObject formGlobal = (JSONObject) JSONSerializer.toJSON(getDescriptor().getDetails());
        if (formGlobal != null &&
        formGlobal.getString("clitool") != null &&
        formGlobal.getString("perlpath") != null &&
        filepath != null &&
        filepathOutput != null) {
            //Minimum configs are filled out
            filepath = env.expand(filepath); //For enviormental variable interp = https://stackoverflow.com/questions/30512887/variable-substitution-in-jenkins-plugin
            String clitool = formGlobal.getString("clitool");
            String serverurl = formGlobal.getString("serverurl");
            String perlpath = formGlobal.getString("perlpath");

            List<String> commandAddition = new ArrayList<String>();
            
            //Spawn files for java to check existance
            File cliFile = new File(clitool);
            File projectDir = new File(filepath);
            File outputDir = new File(filepathOutput);
            File perlDir = new File(perlpath);

            
            if(cliFile.exists() && cliFile.isFile() &&
            projectDir.exists() && projectDir.isDirectory() &&
            outputDir.exists() && outputDir.isDirectory() &&
            perlDir.exists() && perlDir.isDirectory()) { 
                //All files/dirs exist according to java
                String[] baseString = new String[]{"java", "-jar", clitool, 
                            "--workingDir", filepathOutput,
                            "--sourceDir", filepath,
                            "--analyzerDir", perlpath};
                for (String s : baseString) {
                    commandAddition.add(s); //Append start of command pattern, dirs/file
                } 
                        
                if (useonline
                && login != null
                && password != null
                && compid != null
                && appid != null
                && serverurl != null
                && !serverurl.isEmpty()){
                    //Selected "Use Online" and fields are populated
                    message = "Ran online";
                    String[] strs = new String[]{
                        "--login", login, 
                        "--password", password, 
                        "--applicationId", appid,
                        "--companyId", compid,
                        "--serverUrl", serverurl};
                    for (String s : strs) {
                        commandAddition.add(s); //Additional online commands appended
                    }
                    if (snapshotlabel != null) { //Optional snapshot label appended
                        String tempSnapshotlabel = env.expand(snapshotlabel);
                        commandAddition.add("--snapshotLabel");
                        commandAddition.add(tempSnapshotlabel);
                    }
                    
                        
                } else {
                    if (useonline) {
                        message = "Some fields required for online use are empty, ran offline (and un-enabled checkbox)";
                        useonline = false; //Switch sign of checkbox if stuff is missing
                    } else {
                        message = "Ran offline";
                    }
                    commandAddition.add("--skipUpload"); //Override online stuff, if forced offline with empty fields or user choice
                }
                
                if (extrafields != null) {
                    //Turn extra fields into an arraylist for the executed PB. 
                    for (String s : extrafields.split(" ")) {
                        commandAddition.add(s.trim());
                    }
                }
                
                //ATTEMPT TO EXECUTE collected command
                try {
                    ProcessBuilder pb = new ProcessBuilder(commandAddition);
                    listener.getLogger().println("\nHIGHLIGHT MESSAGE: "+ message);
                    listener.getLogger().print(" > ");
                    for (String s: pb.command()) {
                        //Print the command used to "Console Output"
                        listener.getLogger().print(s+" ");
                    }
                    listener.getLogger().print("\n");
                    Process p = pb.start();
                    BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String s = "";
                    while((s = in.readLine()) != null){
                         listener.getLogger().println(s); //Read in STDOUT for command and append to console (highlight details)
                    }
                    int status = p.waitFor();
                    listener.getLogger().println("Exited with status: " + status); //See exit status of highlight (somehow the highlight tool never throws the right code w/ errors - always 0)
                    if (useonline && serverreq) {
                        //Start proper post-build action, to get highlight stats
                        String pageMessage = highlightResults(login, password, appid, compid, serverurl, listener);
                        CastHighlightBuildAction buildAction = new CastHighlightBuildAction(pageMessage, build);
                        build.addAction(buildAction);
                    }
                } catch(IOException e) {
                    e.printStackTrace();  
                    message = "IO Exception";
                } catch(InterruptedException e) {
                    e.printStackTrace();  
                    message = "Interupt Exception";
                }
            } else {
                //Get paths from what java thinks, debugging stuff 
                listener.getLogger().println("\nHIGHLIGHT MESSAGE: One or more of the mandatory paths is incorrect.");
                listener.getLogger().println("Highlight Tool: "+ cliFile.getPath() +" | Exists: " + String.valueOf(cliFile.exists()));
                listener.getLogger().println("Project Path: "+ projectDir.getPath() + " | Exists: " + String.valueOf(projectDir.exists()));
                listener.getLogger().println("Output Path: "+ outputDir.getPath() + " | Exists: " + String.valueOf(outputDir.exists()));
                listener.getLogger().println("Perl Path: "+ perlDir.getPath() + " | Exists: " +String.valueOf(perlDir.exists()));
            }
            
        } else {
            //TODO, fill out useful debugging info here, 
            listener.getLogger().println("\nHIGHLIGHT MESSAGE: Highlight failed to execute, one or more of the mandatory config fields are empty");
        }
        
        return true;
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }



    /**
     * Descriptor for {@link CastHighlightPublisher}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     * <p/>
     * <p/>
     * See <tt>src/main/resources/org/jenkinsci/plugins/castHighlight/CastHighlightPublisher/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        /**
         * To persist global configuration information,
         * simply store it in a field and call save().
         * <p/>
         * <p/>
         * If you don't want fields to be persisted, use <tt>transient</tt>.
         */
        private String formGlobalOutput;
        private String clitool;
        private String perlpath;
        private String serverurl;

        

        /**
         * In order to load the persisted global configuration, you have to
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }


        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Run CAST Highlight";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // set that to properties and call save().
            clitool = formData.getString("clitool");
            perlpath = formData.getString("perlpath");
            serverurl = formData.getString("serverurl");

            formGlobalOutput = formData.toString();
            
            save();
            return super.configure(req, formData);
        }

        /**
         * This method returns true if the global configuration says we should speak French.
         * <p/>
         * The method name is bit awkward because global.jelly calls this method to determine
         * the initial state of the checkbox by the naming convention.
         */
        public String getDetails() {
            return formGlobalOutput;
        }
        public String getClitool() {
            return clitool;
        }
        public String getServerurl() {
            return serverurl;
        }
        public String getPerlpath() {
            return perlpath;
        }
    }
}

