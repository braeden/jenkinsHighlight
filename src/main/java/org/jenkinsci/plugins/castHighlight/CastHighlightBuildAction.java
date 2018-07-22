package org.jenkinsci.plugins.castHighlight;

import hudson.model.AbstractBuild;
import hudson.model.Action;


public class CastHighlightBuildAction implements Action {

    private String message;
    private AbstractBuild<?, ?> build;

    @Override
    public String getIconFileName() {
        return "/plugin/castHighlight/img/build-goals.png";
    }

    @Override
    public String getDisplayName() {
        return "Highlight Results";
    }

    @Override
    public String getUrlName() {
        return "highlight";
    }

    public String getMessage() {
        return this.message;
    }

    public int getBuildNumber() {
        return this.build.number;
    }

    public AbstractBuild<?, ?> getBuild() {
        return build;
    }

    CastHighlightBuildAction(final String message, final AbstractBuild<?, ?> build)
    {
        this.message = message;
        this.build = build;
    }
}
