package com.baracuda.piepet.pominterceptor;

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
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.servlet.ServletException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * POMInterceptor plugin will intercept POM elements and save them as build paramteters
 * XPATH to select which parameters to be used.
 *
 * An badge will be added to build history with the maven artifact version e.g 4.2.0-SNAPSHOT
 *
 * Paramteters that always will be created are
 * POM_VERSION = artifactVersion
 * POM_STAGING_PROFILE_ID=stagingProfileId retrieved from nexus-staging-maven plugin configuration
 */
public class POMInterceptorPlugin extends Builder implements SimpleBuildStep {

    private final String xPath;
    private final String rootPOM;
    private final static DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();



    /**
     *
     * @param rootPOM
     * @param xPath
     */
    @DataBoundConstructor
    public POMInterceptorPlugin(String rootPOM, String xPath) {
        this.rootPOM = rootPOM;
        this.xPath = xPath;
    }

    public String getXpath() {
        return xPath;
    }

    public String getRootPOM() {
        return rootPOM;
    }

    @Override
    public void perform(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) {
        // This is where you 'build' the project.
        // Since this is a dummy, we just say 'hello world' and call that a build.

        try {

            String pomXMLasString = build.getExecutor().getCurrentWorkspace().child(getRootPOM()).readToString();
            String pomVersion = getArtifactVersion(pomXMLasString);
            if (pomVersion != null && pomVersion.length()>0 && getDescriptor().getaddBadge()) {
                build.addAction(POMInterceptorBadge.createShortText(pomVersion));
            }


            List<ParameterValue> parameterValues = new ArrayList<ParameterValue>();
            List<POMElement> pomElements=getPOMElements(pomXMLasString, getXpath());
            for (POMElement pomElement : pomElements) {
                StringParameterValue param = new StringParameterValue("POM_" + pomElement.getName(),pomElement.getValue());
                parameterValues.add(param);

            }

            if (parameterValues.size() == 0) {
                StringParameterValue paramVersion = new StringParameterValue("POM_VERSION", pomVersion.toString());
                StringParameterValue paramStagingProfileId = new StringParameterValue("POM_STAGING_PROFILE_ID", getNexusStagingProfileId(pomXMLasString));
                parameterValues.add(paramVersion);
                parameterValues.add(paramStagingProfileId);
                ParametersAction parametersAction = new ParametersAction(parameterValues);
                build.addAction(parametersAction);
            } else {
                StringParameterValue paramStagingProfileId = new StringParameterValue("POM_STAGING_PROFILE_ID", getNexusStagingProfileId(pomXMLasString));
                parameterValues.add(paramStagingProfileId);
                ParametersAction paramAction = new ParametersAction(parameterValues);
                build.addAction(paramAction);
            }

        } catch (Exception ex) {
            //TODO Add logging
            System.out.println(ex.getStackTrace());
        }
    }

    private String getArtifactVersion(String pom) {
        NodeList nl = xpathPOM(pom, "/project/*[name()='version']");
        if (nl != null && nl.getLength() > 0) {
            return nl.item(0).getTextContent();
        } else {
            return "";
        }

    }

    private String getNexusStagingProfileId(String pom) {
        NodeList nl = xpathPOM(pom, "/project/build/plugins/*/configuration/stagingProfileId");
        if (nl != null && nl.getLength() > 0) {
            return nl.item(0).getTextContent();
        } else {
            return "";
        }

    }

    private List<POMElement> getPOMElements(String pom, String xPath) {
        NodeList nl = xpathPOM(pom, xPath);
        List<POMElement> elements = new ArrayList<POMElement>();
        if (nl != null) {
            for (int i = 0; i < nl.getLength(); i++) {

                elements.add(new POMElement(nl.item(i).getNodeName(), nl.item(i).getTextContent()));
            }
        }
        return elements;

    }

    private NodeList xpathPOM(String pom, String xPath) {
        NodeList nl=null;
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            InputSource is = new InputSource(new StringReader(pom));
            Document doc = builder.parse(is);
            XPathFactory xPathfactory = XPathFactory.newInstance();
            XPath xpath = xPathfactory.newXPath();
            XPathExpression expr = xpath.compile(xPath);
            nl = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
            return nl;

        } catch (Exception ex) {
            //TODO Add logging
        }
        return nl;
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * Descriptor for {@link POMInterceptorPlugin}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     * <p/>
     * <p/>
     * See <tt>src/main/resources/hudson/plugins/hello_world/POMInterceptorPlugin/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        /**
         * To persist global configuration information,
         * simply store it in a field and call save().
         * <p/>
         * <p/>
         * If you don't want fields to be persisted, use <tt>transient</tt>.
         */
        private boolean addBadge;

        /**
         * In order to load the persisted global configuration, you have to
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        /**
         * Performs on-the-fly validation of the form field 'xpath'.
         *
         * @param value This parameter receives the value that the user has typed.
         * @return Indicates the outcome of the validation. This is sent to the browser.
         * <p/>
         * Note that returning {@link FormValidation#error(String)} does not
         * prevent the form from being saved. It just means that a message
         * will be displayed to the user.
         */
        public FormValidation doCheckXpath(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a xpath");
            if (value.length() < 4)
                return FormValidation.warning("Isn't the xpath too short?");
            return FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable xpath is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Maven POM Interceptor";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // set that to properties and call save().

            addBadge = formData.getBoolean("addBadge");
            // ^Can also use req.bindJSON(this, formData);
            //  (easier when there are many fields; need set* methods for this, like setUseFrench)
            save();
            return super.configure(req, formData);
        }

        /**
         * This method returns true if the global configuration says we should speak French.
         * <p/>
         * The method xpath is bit awkward because global.jelly calls this method to determine
         * the initial state of the checkbox by the naming convention.
         */
        public boolean getaddBadge() {
            return addBadge;
        }
    }
}

