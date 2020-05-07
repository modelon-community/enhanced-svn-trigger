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
package com.modelon.jenkins.enhancedSVNTrigger;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import com.modelon.jenkins.enhancedSVNTrigger.model.CommitMessageParameterRule;
import com.modelon.jenkins.enhancedSVNTrigger.model.LabelSimplificationRule;
import com.modelon.jenkins.enhancedSVNTrigger.model.ShowLogAction;

import antlr.ANTLRException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Action;
import hudson.model.Item;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.TaskListener;
import hudson.scm.RevisionParameterAction;
import hudson.slaves.NodeProperty;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.StreamTaskListener;
import jenkins.model.Jenkins;
import jenkins.triggers.SCMTriggerItem;
import net.sf.json.JSONObject;

public class BuildTrigger extends Trigger<Item> {

    @DataBoundConstructor
    public BuildTrigger() throws ANTLRException {
        super(getMyDescriptor().getDefaultSchedule());
    }

    public static DescriptorImpl getMyDescriptor() {
        return (DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(BuildTrigger.class);
    }

    public File getLogFile() {
        return new File(job.getRootDir(), "enhancedSVNTrigger.log");
    }

    @Override
    public Collection<? extends Action> getProjectActions() {
        return Collections.singletonList(new ShowLogAction(this));
    }

    public  Job<?, ?> getJob() {
        return (Job<?, ?>) job;
    }

    public SCMTriggerItem getSCMTriggerItem() {
        return SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(job);
    }

    public static EnvVars getGlobalEnvVars(@Nonnull TaskListener listener) throws IOException, InterruptedException {
        EnvVars env = new EnvVars();
        for (NodeProperty<?> nodeProperty: Jenkins.getInstance().getGlobalNodeProperties()) {
            nodeProperty.buildEnvVars(env, listener);
        }
        return env;
    }

    @Override
    public void run() {
        StreamTaskListener listener;
        try {
            listener = new StreamTaskListener(getLogFile());
        } catch (IOException io) {
            return;
        }
        
        // Use a single point in time which all polling is based on
        Date startTime = new Date();
        
        PrintStream log = listener.getLogger();
        log.println("Time is: " + startTime);
        try {
            Job<?, ?> job = getJob();
            RevisionPoller.pollForChanges(job, Collections.singletonList(job), listener);
        } finally {
            try {
                listener.close();
            } catch (IOException e) {
            }
        }
    }

    public static Map<String, String> extractEnvVarsFromMsg(String commitMessage) {
        Map<String, String> res = new LinkedHashMap<String, String>();
        for (CommitMessageParameterRule rule : getMyDescriptor().getCommitMessageParameterRules()) {
            rule.extractParameter(commitMessage, res);
        }
        return res;
    }

    @Extension
    public static class DescriptorImpl extends TriggerDescriptor {

        private String defaultSchedule = "* * * * *";
        private boolean useLabelSimplifications = false;
        private List<LabelSimplificationRule> labelSimplifications = Collections.emptyList();
        private List<CommitMessageParameterRule> commitMessageParameterRules = Collections.emptyList();
        
        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean isApplicable(Item item) {
            return item instanceof Job<?, ?> && SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(item) != null;
        }

        @Override
        public String getDisplayName() {
            return "Extended SVN trigger";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            req.bindJSON(this, formData);
            return super.configure(req,formData);
        }

        public String getDefaultSchedule() {
            return defaultSchedule;
        }

        public void setDefaultSchedule(String defaultSchedule) {
            this.defaultSchedule = defaultSchedule;
            //TODO change all existing triggers
            save();
        }

        public boolean getUseLabelSimplifications() {
            return useLabelSimplifications;
        }

        public void setUseLabelSimplifications(boolean useLabelSimplifications) {
            this.useLabelSimplifications = useLabelSimplifications;
            save();
        }

        public List<LabelSimplificationRule> getLabelSimplifications() {
            return labelSimplifications;
        }

        public void setLabelSimplifications(List<LabelSimplificationRule> labelSimplifications) {
            this.labelSimplifications = Collections.unmodifiableList(new ArrayList<LabelSimplificationRule>(labelSimplifications));
            save();
        }
        
        public List<CommitMessageParameterRule> getCommitMessageParameterRules() {
            return commitMessageParameterRules;
        }
        
        public void setCommitMessageParameterRules(List<CommitMessageParameterRule> commitMessageParameterRules) {
            this.commitMessageParameterRules = Collections.unmodifiableList(new ArrayList<CommitMessageParameterRule>(commitMessageParameterRules));
            save();
        }
        
        public String simplifyLabel(String str) {
            for (LabelSimplificationRule rule : labelSimplifications) {
                str = rule.simplify(str);
            }
            return str;
        }
        
    }
    
    @Initializer(before = InitMilestone.EXTENSIONS_AUGMENTED)
    public static void alias() {
        Items.XSTREAM2.addCompatibilityAlias("com.modelon.jenkins.enhancedSVNTrigger.EnhancedSVNTrigger$State", com.modelon.jenkins.enhancedSVNTrigger.model.JobResourceState.class);
        Items.XSTREAM2.addCompatibilityAlias("com.modelon.jenkins.enhancedSVNTrigger.EnhancedSVNTrigger", com.modelon.jenkins.enhancedSVNTrigger.BuildTrigger.class);
        Items.XSTREAM2.addCompatibilityAlias("com.modelon.jenkins.enhancedSVNTrigger.NewRepoTriggerCause", com.modelon.jenkins.enhancedSVNTrigger.model.NewRepoTriggerCause.class);
        Items.XSTREAM2.addCompatibilityAlias("com.modelon.jenkins.enhancedSVNTrigger.NewRevisionTriggerCause", com.modelon.jenkins.enhancedSVNTrigger.model.NewRevisionTriggerCause.class);
        Items.XSTREAM2.addCompatibilityAlias("com.modelon.jenkins.enhancedSVNTrigger.TriggerCauseLabel", Object.class);
        Items.XSTREAM2.addCompatibilityAlias("com.modelon.jenkins.enhancedSVNTrigger.model.TriggerCauseLabel", Object.class);
        Items.XSTREAM2.addCompatibilityAlias("com.modelon.jenkins.enhancedSVNTrigger.QueueRevisionParameterAction", RevisionParameterAction.class);
        Items.XSTREAM2.addCompatibilityAlias("com.modelon.jenkins.enhancedSVNTrigger.model.QueueRevisionParameterAction", RevisionParameterAction.class);
    }


}
