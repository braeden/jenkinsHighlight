package org.jenkinsci.plugins.castHighlight;

import hudson.Launcher;
import hudson.Extension;
import hudson.model.Action;
import hudson.tasks.*;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;

 
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Scanner;
import java.io.PrintWriter;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/*
TODO: 
- Rename artifact id, classes
- Add string queries to all local factors, validate project path, id's etc. 
- Redo the Final display page, just complete or not with return code
- Finalize the calling of the jar OS agnostic from inputs
- Catch the program and throw a nice build error when missing parameters
- Remove extra imports and cleanup 
- Make directories folder selectable !!!
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

    private final String filepath;
    private final String serverurl;
    private final String appid;
    private final String filepathOutput;
    private final String extrafields;

    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public CastHighlightPublisher(String filepath, String serverurl, String appid, String filepathOutput, String extrafields) {
        this.filepath = filepath;
        this.serverurl = serverurl;
        this.appid = appid;
        this.filepathOutput = filepathOutput;
        this.extrafields = extrafields;
        
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
    public String getServerurl() {
        return serverurl;
    }
    public String getExtrafieldsl() {
        return extrafields;
    }

    
/*
/home/braeden/Documents/Test
/home/braeden/Downloads/HighlightOuput
    25080
    
    
    
/home/braeden/Downloads/Highlight-Automation-Command/HighlightAutomation.jar
/home/braeden/Downloads/Highlight-Automation-Command/perl

5091
    
 
*/
    
    
    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        String message="";
        JSONObject formGlobal = getDescriptor().getDetails();
        if (formGlobal != null &&
        formGlobal.getString("clitool") != null &&
        formGlobal.getString("perlpath") != null &&
        filepath != null &&
        filepathOutput != null) {
            String login = formGlobal.getString("login");
            String password = formGlobal.getString("password");
            String compid = formGlobal.getString("compid");
            String clitool = formGlobal.getString("clitool");
            String perlpath = formGlobal.getString("perlpath");

            List<String> commandAddition = new ArrayList<String>();
            File cliFile = new File(clitool);
            File projectDir = new File(filepath);
            File outputDir = new File(filepathOutput);
            File perlDir = new File(perlpath);

            
            if(cliFile.exists() && !cliFile.isDirectory() &&
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
                        
                if (!formGlobal.getBoolean("useOffline")
                && login != null
                && password != null
                && compid != null
                && appid != null
                && serverurl != null) {
                    message = "Running online, check Console Output for results";
                    String[] strs = new String[]{
                        "--login", login, 
                        "--password", password, 
                        "--applicationId", appid,
                        "--companyId", compid,
                        "--serverUrl", serverurl};
                    for (String s : strs) {
                        commandAddition.add(s);
                    }
                    //Online command
                    //Online run tool. 
                        
                } else {
                    if (!formGlobal.getBoolean("useOffline")) {
                        message = "Some fields required for online use are empty, running offline";
                    } else {
                        message = "Running offline, check Console Output for results";
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
                    listener.getLogger().print("> ");
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
                    //listener.getLogger().println(System.getProperty("jenkins.hudsonUrl"));
                } catch(IOException e) {
                    e.printStackTrace();  
                    message = "IO Exception";
                } catch(InterruptedException e) {
                    e.printStackTrace();  
                    message = "Interupt Exception";
                }
            } else {
                message = "One of the specified paths is incorrect.";
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
            message = "Highlight failed to execute, one or more of the mandatory config fields are empty";
        }
        
        
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        //CastHighlightBuildAction buildAction = new CastHighlightBuildAction(message, build);
        //build.addAction(buildAction);

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
        private JSONObject formGlobalOutput;
        private String login;
        private String password;
        private String compid;
        private String clitool;
        private String perlpath;

        private boolean useoffline;

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
            formGlobalOutput = formData;
            login = formData.getString("login");
            password = formData.getString("password");
            compid = formData.getString("compid");
            clitool = formData.getString("clitool");
            useoffline = formData.getBoolean("useOffline");
            perlpath = formData.getString("perlpath");

            
            // ^Can also use req.bindJSON(this, formData);
            //  (easier when there are many fields; need set* methods for this, like setUseFrench)
            save();
            return super.configure(req, formData);
        }

        /**
         * This method returns true if the global configuration says we should speak French.
         * <p/>
         * The method name is bit awkward because global.jelly calls this method to determine
         * the initial state of the checkbox by the naming convention.
         */
        public JSONObject getDetails() {
            return formGlobalOutput;
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
        public String getClitool() {
            return clitool;
        }
        public boolean getUseOffline() {
            return useoffline;
        }
        public String getPerlpath() {
            return perlpath;
        }
    }
}

