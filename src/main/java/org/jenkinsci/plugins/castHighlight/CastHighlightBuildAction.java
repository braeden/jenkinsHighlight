 
package org.jenkinsci.plugins.castHighlight;

import hudson.model.AbstractBuild;
import hudson.model.Action;

public class CastHighlightBuildAction implements Action {

    private String pageMessage;

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
        return(pageMessage);
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

    CastHighlightBuildAction(final String pageMessage, final AbstractBuild<?, ?> build)
    {
        this.build = build;
        this.pageMessage = pageMessage;
    }
}