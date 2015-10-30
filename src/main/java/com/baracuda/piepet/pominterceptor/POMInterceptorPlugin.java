package com.baracuda.piepet.pominterceptor;

import groovy.util.XmlSlurper;
import groovy.util.slurpersupport.GPathResult;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Sample {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link POMInterceptorPlugin} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields (like {@link #})
 * to remember the configuration.
 *
 * <p>
 * When a build is performed, the {@link #perform} method will be invoked. 
 *
 * @author Kohsuke Kawaguchi
 */
public class POMInterceptorPlugin extends Builder implements SimpleBuildStep {

    private final String pomElements;
    private final String rootPOM;


    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public POMInterceptorPlugin(String rootPOM, String pomElements) {
        this.rootPOM = rootPOM;
        this.pomElements = pomElements;
    }

    public String getPomElements() {
        return pomElements;
    }

    public String getRootPOM() {
        return rootPOM;
    }

    @Override
    public void perform(Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener) {
        // This is where you 'build' the project.
        // Since this is a dummy, we just say 'hello world' and call that a build.
        String buildProvidedPomElements[]=getPomElements().split(",");
        try {
            EnvVars env = build.getEnvironment(listener);

            String pomFile = build.getExecutor().getCurrentWorkspace().child(getRootPOM()).readToString();
            GPathResult project = new XmlSlurper().parseText(pomFile);
            Object pomVersion=project.getProperty("version");
            if (pomVersion!=null){
                build.addAction(POMInterceptorAction.createShortText(pomVersion.toString()));
            }

            List<ParameterValue> parameterValues = new ArrayList<ParameterValue>();
            for (String buildProvidedPomElement : buildProvidedPomElements) {
                if(buildProvidedPomElement.length()>0) {
                    env.put("POM_" + buildProvidedPomElement.toUpperCase(), project.getProperty(buildProvidedPomElement).toString());
                    StringParameterValue param = new StringParameterValue("POM_" + buildProvidedPomElement.toUpperCase(), project.getProperty(buildProvidedPomElement).toString());
                    parameterValues.add(param);
                }
            }
            if(parameterValues.size()==0){
                StringParameterValue param = new StringParameterValue("POM_VERSION", pomVersion.toString());
                ParametersAction paramAction = new ParametersAction(param);
                build.addAction(paramAction);
            }else {
                ParametersAction paramAction = new ParametersAction(parameterValues);
                build.addAction(paramAction);
            }

        }catch (Exception ex){
            System.out.println(ex.getStackTrace());
        }
  }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link POMInterceptorPlugin}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>src/main/resources/hudson/plugins/hello_world/POMInterceptorPlugin/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        /**
         * To persist global configuration information,
         * simply store it in a field and call save().
         *
         * <p>
         * If you don't want fields to be persisted, use <tt>transient</tt>.
         */
        private boolean addParamatersToJob;

        /**
         * In order to load the persisted global configuration, you have to 
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        /**
         * Performs on-the-fly validation of the form field 'pomElements'.
         *
         * @param value
         *      This parameter receives the value that the user has typed.
         * @return
         *      Indicates the outcome of the validation. This is sent to the browser.
         *      <p>
         *      Note that returning {@link FormValidation#error(String)} does not
         *      prevent the form from being saved. It just means that a message
         *      will be displayed to the user. 
         */
        public FormValidation doCheckPOMelements(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a pomElements");
            if (value.length() < 4)
                return FormValidation.warning("Isn't the pomElements too short?");
            return FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable pomElements is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Maven POM Interceptor";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // set that to properties and call save().

            addParamatersToJob = formData.getBoolean("addParamatersToJob");
            // ^Can also use req.bindJSON(this, formData);
            //  (easier when there are many fields; need set* methods for this, like setUseFrench)
            save();
            return super.configure(req,formData);
        }

        /**
         * This method returns true if the global configuration says we should speak French.
         *
         * The method pomElements is bit awkward because global.jelly calls this method to determine
         * the initial state of the checkbox by the naming convention.
         */
        public boolean getAddParamatersToJob() {
            return addParamatersToJob;
        }
    }
}

