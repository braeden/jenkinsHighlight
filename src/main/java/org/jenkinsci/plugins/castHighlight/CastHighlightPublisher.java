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

    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public CastHighlightPublisher(String filepath, String appid, String filepathOutput, String extrafields, String login, String password, String compid, String snapshotlabel, boolean useonline) {
        this.filepath = filepath;
        this.appid = appid;
        this.filepathOutput = filepathOutput;
        this.extrafields = extrafields;
        this.login = login;
        this.password = password;
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
    
/*
/home/braeden/Documents/Test
/home/braeden/Downloads/HighlightOuput
25080
    
/home/braeden/Downloads/Highlight-Automation-Command/HighlightAutomation.jar
/home/braeden/Downloads/Highlight-Automation-Command/perl
b.smith+Jenkins@castsoftware.com
5091
*/
    
    
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
            filepath = env.expand(filepath); //For env interp = https://stackoverflow.com/questions/30512887/variable-substitution-in-jenkins-plugin
            String clitool = formGlobal.getString("clitool");
            String serverurl = formGlobal.getString("serverurl");
            String perlpath = formGlobal.getString("perlpath");

            List<String> commandAddition = new ArrayList<String>();
            File cliFile = new File(clitool);
            File projectDir = new File(filepath);
            File outputDir = new File(filepathOutput);
            File perlDir = new File(perlpath);

            
            if(cliFile.exists() && cliFile.isFile() &&
            projectDir.exists() && projectDir.isDirectory() &&
            outputDir.exists() && outputDir.isDirectory() &&
            perlDir.exists() && perlDir.isDirectory()) { 
                String[] baseString = new String[]{"java", "-jar", clitool, 
                            "--workingDir", filepathOutput,
                            "--sourceDir", filepath,
                            "--analyzerDir", perlpath};
                for (String s : baseString) {
                    commandAddition.add(s);
                }
                        
                if (useonline
                && login != null
                && password != null
                && compid != null
                && appid != null
                && serverurl != null
                && !serverurl.isEmpty()){
                    //ONLINE
                    message = "Ran online";
                    String[] strs = new String[]{
                        "--login", "\""+login+"\"", 
                        "--password", "\""+password+"\"", 
                        "--applicationId", appid,
                        "--companyId", compid,
                        "--serverUrl", "\""+serverurl+"\""};
                    for (String s : strs) {
                        commandAddition.add(s);
                    }
                    if (snapshotlabel != null) {
                        String tempSnapshotlabel = env.expand(snapshotlabel);
                        commandAddition.add("--snapshotLabel");
                        commandAddition.add("\""+tempSnapshotlabel+"\"");
                    }
                    
                        
                } else {
                    if (useonline) {
                        message = "Some fields required for online use are empty, ran offline (and un-enabled checkbox)";
                        useonline = false;
                    } else {
                        message = "Ran offline";
                    }
                    commandAddition.add("--skipUpload");
                    //offline
                }
                
                if (extrafields != null) {
                    //Turn extra fields into an arraylist for the executed PB.
                    for (String s : extrafields.split(" ")) {
                        commandAddition.add(s.trim());
                    }
                }
                /////// Actually run command //////////////////////////
                
                ///////////////////////////////////////////////////////
                
                try {
                    ProcessBuilder pb = new ProcessBuilder(commandAddition);
                    listener.getLogger().println("\nHIGHLIGHT MESSAGE: "+ message);
                    listener.getLogger().print(" > ");
                    for (String s: pb.command()) {
                        //Print the command used
                        listener.getLogger().print(s+" ");
                    }
                    listener.getLogger().print("\n");
                    Process p = pb.start();
                    BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String s = "";
                    while((s = in.readLine()) != null){
                         listener.getLogger().println(s);
                    }
                    int status = p.waitFor();
                    listener.getLogger().println("Exited with status: " + status);
                    if (useonline) {
                        //Start proper post-build action, to get highlight stats
                     
                        CastHighlightBuildAction buildAction = new CastHighlightBuildAction(login, password, appid, compid, serverurl, build);
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
                listener.getLogger().println("\nHIGHLIGHT MESSAGE: One or more of the mandatory paths is incorrect.");
                listener.getLogger().println("Highlight Tool: "+ clitool +" | Exists: " + String.valueOf(cliFile.exists()));
                listener.getLogger().println("Project Path: "+ filepath + " | Exists: " + String.valueOf(projectDir.exists()));
                listener.getLogger().println("Output Path: "+ filepathOutput + " | Exists: " + String.valueOf(outputDir.exists()));
                listener.getLogger().println("Perl Path: "+ perlpath + " | Exists: " +String.valueOf(perlDir.exists()));            

            }
            
        /*
        java -jar HighlightAutomation.jar 
        --workingDir "C:\highlight-myproject"
        --sourceDir "C:\myproject\src" 
        --login "john.doe@acme.com" 
        --password "*******"
        --applicationId 1234
        --companyId 5678
        --serverUrl "https://rpa.casthighlight.com"
        */
          
        } else {
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
        /*public JSONObject stringtoJson(String s) {
            return (JSONObject) JSONSerializer.toJSON(s);
        }*/
    }
}

