/*
    Copyright (C) 2020 Modelon AB

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/
package com.modelon.jenkins.enhancedSVNTrigger.model;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.kohsuke.stapler.export.Exported;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.EnvironmentContributor;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.RevisionParameterAction;
import hudson.scm.SubversionSCM.SvnInfo;

public abstract class TriggerCause extends Cause {

    public static final String TRIGGER_CAUSE_LABEL = "TRIGGER_CAUSE";
    public static final String TRIGGER_CHANGE_LABEL = "TRIGGER_CHANGE";

    private final Map<String, String> extraEnvVars;
    private final List<SvnInfo> revisionInfo;
    
    public TriggerCause(Map<String, String> extraEnvVars, List<SvnInfo> revisionInfo) {
        this.extraEnvVars = extraEnvVars;
        this.revisionInfo = revisionInfo;
    }

    @Exported()
    public abstract String triggerCauseLabel();


    protected void addTriggerChangeLabelIfNewRevision(EnvVars env) {

    }

    public abstract boolean shouldTriggerBuild();
    
    public void contributeEnvVars(EnvVars env) {
        if (!extraEnvVars.containsKey(TRIGGER_CAUSE_LABEL)) {
            env.put(TRIGGER_CAUSE_LABEL, triggerCauseLabel());
        }
        addTriggerChangeLabelIfNewRevision(env);
        env.putAll(extraEnvVars);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void onAddedTo(Run build) {
        build.addAction(new RevisionParameterAction(revisionInfo));
    }

    public List<SvnInfo> getRevisionInfo() {
        return revisionInfo;
    }

    @Exported
    public Map<String, String> getExtraEnvVars() {
        return extraEnvVars;
    }

    @Extension
    public static class DescriptorImpl extends EnvironmentContributor {
        @SuppressWarnings("rawtypes")
        @Override
        public void buildEnvironmentFor(Run run, EnvVars env, TaskListener listener)
                throws IOException, InterruptedException {
            CauseAction action = run.getAction(CauseAction.class);
            if (action == null) {
                return;
            }
            TriggerCause cause = action.findCause(TriggerCause.class);
            if (cause == null) {
                return;
            }
            cause.contributeEnvVars(env);
        }
    }
}
