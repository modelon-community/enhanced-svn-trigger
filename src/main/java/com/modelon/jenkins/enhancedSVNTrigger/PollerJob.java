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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import org.acegisecurity.Authentication;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import com.modelon.jenkins.enhancedSVNTrigger.model.JobResourceState;

import hudson.Extension;
import hudson.cli.declarative.CLIMethod;
import hudson.model.BuildAuthorizationToken;
import hudson.model.BuildableItem;
import hudson.model.Cause;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.ResourceList;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.Queue.Executable;
import hudson.model.Queue.FlyweightTask;
import hudson.model.Queue.Task;
import hudson.model.listeners.ItemListener;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.SubTask;
import hudson.search.SearchIndexBuilder;
import hudson.security.ACL;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.CaseInsensitiveComparator;
import hudson.util.DescribableList;
import jenkins.model.BlockedBecauseOfBuildInProgress;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.model.ParameterizedJobMixIn.ParameterizedJob;
import jenkins.model.lazy.LazyBuildMixIn;
import jenkins.model.lazy.LazyBuildMixIn.LazyLoadingJob;
import jenkins.triggers.SCMTriggerItem;
import jenkins.util.TimeDuration;
import net.sf.json.JSONObject;

/**
 * Job which triggers other jobs by checking their SVN repositories for
 * changes.
 * 
 * This class must implement ParameterizedJob. Otherwise Timed triggers won't
 * work. See {@link hudson.triggers.Trigger#checkTriggers(java.util.Calendar)}.
 * it only checks AbstractProjects and ParameterizedJob.
 */
public class PollerJob extends Job<PollerJob, PollerRun> implements TopLevelItem, BuildableItem, LazyLoadingJob<PollerJob, PollerRun>, Task, FlyweightTask, ParameterizedJob {
    
    private transient LazyBuildMixIn<PollerJob, PollerRun> buildMixIn;
    
    private DescribableList<Trigger<?>,TriggerDescriptor> triggers = new DescribableList<Trigger<?>,TriggerDescriptor>(this);
    private SortedSet<String> jobsToTrigger = new TreeSet<String>(CaseInsensitiveComparator.INSTANCE);
    private boolean disabled = false;

    @SuppressWarnings("rawtypes")
    protected PollerJob(ItemGroup parent, String name) {
        super(parent, name);
        buildMixIn = createBuildMixIn();
    }

    @Override
    public void onCreatedFromScratch() {
        super.onCreatedFromScratch();
        buildMixIn.onCreatedFromScratch();
    }

    @Override
    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        super.onLoad(parent, name);
        
        if (buildMixIn == null) {
            buildMixIn = createBuildMixIn();
        }
        buildMixIn.onLoad(parent, name);
        
        if (triggers == null) {
            triggers = new DescribableList<Trigger<?>,TriggerDescriptor>(this);
        } else {
            triggers.setOwner(this);
        }
        for (Trigger t : triggers) {
            t.start(this, Items.currentlyUpdatingByXml());
        }
    }

    @Override
    protected void submit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, FormException {
        super.submit(req, rsp);
        
        JSONObject form = req.getSubmittedForm();
        
        setDisabled(form.getBoolean("disable"));
        
        for (Trigger t : triggers) {
            t.stop();
        }
        triggers.rebuild(req, form, Trigger.for_(this));
        for (Trigger t : triggers) {
            t.start(this, true);
        }
        SortedSet<String> newJobsToTrigger = new TreeSet<String>(CaseInsensitiveComparator.INSTANCE);
        for (Item item : ((ItemGroup<Item>)getParent()).getItems()) {
            String itemName = item.getFullName();
            if (req.getParameter(itemName) != null) {
                newJobsToTrigger.add(itemName);
            }
        }
        synchronized (this) {
            jobsToTrigger = newJobsToTrigger;
        }
        
    }
    
    public synchronized void setJobsToTrigger(Collection<String> newJobsToTrigger) {
        jobsToTrigger.clear();
        jobsToTrigger.addAll(newJobsToTrigger);
    }

    private LazyBuildMixIn<PollerJob, PollerRun> createBuildMixIn() {
        return new LazyBuildMixIn<PollerJob, PollerRun>() {

            @Override
            protected PollerJob asJob() {
                return PollerJob.this;
            }

            @Override
            protected Class<PollerRun> getBuildClass() {
                return PollerRun.class;
            }
            
        };
    }

    private ParameterizedJobMixIn<PollerJob, PollerRun> createParameterizedJobMixIn() {
        return new ParameterizedJobMixIn<PollerJob,PollerRun>() {
            @Override protected PollerJob asJob() {
                return PollerJob.this;
            }
        };
    }

    @Override
    public TopLevelItemDescriptor getDescriptor() {
        return (DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(PollerJob.class);
    }

    @Override
    public boolean isBuildable() {
        return !disabled;
    }

    @Override
    protected SortedMap<Integer, PollerRun> _getRuns() {
        return buildMixIn._getRuns();
    }

    @Override
    protected void removeRun(PollerRun run) {
        buildMixIn.removeRun(run);
    }

    @Override
    public boolean isBuildBlocked() {
        // TODO: This isn't very efficient, should be changed.
        return getCauseOfBlockage() != null;
    }

    @Override
    public CauseOfBlockage getCauseOfBlockage() {
        if (isLogUpdated() && !isConcurrentBuild()) { // Are we still building the last build?
            return new BlockedBecauseOfBuildInProgress(getLastBuild());
            
        }
        return null;
    }

    @Override
    public String getWhyBlocked() {
        CauseOfBlockage cause = getCauseOfBlockage();
        if (cause == null) {
            return null;
        } else {
            return cause.getShortDescription();
        }
    }

    @Override
    public Collection<? extends SubTask> getSubTasks() {
        return Collections.singleton(this);
    }

    @Override
    public Map<TriggerDescriptor, Trigger<?>> getTriggers() {
        return triggers.toMap();
    }
    
    @Override
    public Authentication getDefaultAuthentication() {
        return ACL.SYSTEM;
    }

    @Override
    public Authentication getDefaultAuthentication(Queue.Item item) {
        return getDefaultAuthentication();
    }

    @Override
    public void checkAbortPermission() {
        checkPermission(CANCEL);
    }

    @Override
    public boolean hasAbortPermission() {
        return hasPermission(CANCEL);
    }

    @Override
    public boolean isConcurrentBuild() {
        return false;
    }

    @Override
    public Label getAssignedLabel() {
        Jenkins instance = Jenkins.getInstance();
        if (instance == null) {
            return null;
        } else {
            return instance.getSelfLabel();
        }
    }

    @Override
    public Node getLastBuiltOn() {
        return Jenkins.getInstance();
    }

    @Override
    public Executable createExecutable() throws IOException {
        return buildMixIn.newBuild();
    }

    @Override
    public Task getOwnerTask() {
        return this;
    }

    @Override
    public Object getSameNodeConstraint() {
        return null;
    }

    @Override
    public ResourceList getResourceList() {
        return ResourceList.EMPTY;
    }

    @Override
    public boolean scheduleBuild() {
        return createParameterizedJobMixIn().scheduleBuild();
    }

    @Override
    public boolean scheduleBuild(Cause cause) {
        return createParameterizedJobMixIn().scheduleBuild(cause);
    }

    @Override
    public boolean scheduleBuild(int quietPeriod) {
        return createParameterizedJobMixIn().scheduleBuild(quietPeriod);
    }

    @Override
    public boolean scheduleBuild(int quietPeriod, Cause cause) {
        return createParameterizedJobMixIn().scheduleBuild(quietPeriod, cause);
    }

    public void doBuild(StaplerRequest req, StaplerResponse rsp, @QueryParameter TimeDuration delay) throws IOException, ServletException {
        createParameterizedJobMixIn().doBuild(req, rsp, delay);
    }
    
    public void doBuildWithParameters(StaplerRequest req, StaplerResponse rsp, @QueryParameter TimeDuration delay) throws IOException, ServletException {
        createParameterizedJobMixIn().doBuildWithParameters(req, rsp, delay);
    }

    @RequirePOST
    public void doCancelQueue(StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        createParameterizedJobMixIn().doCancelQueue(req, rsp);
    }

    @Override
    protected SearchIndexBuilder makeSearchIndex() {
        return createParameterizedJobMixIn().extendSearchIndex(super.makeSearchIndex());
    }
    
    @Override
    public LazyBuildMixIn<PollerJob, PollerRun> getLazyBuildMixIn() {
        return buildMixIn;
    }
    
    @Extension
    public static final class DescriptorImpl extends TopLevelItemDescriptor {

        @SuppressWarnings("rawtypes")
        @Override
        public TopLevelItem newInstance(ItemGroup parent, String name) {
            return new PollerJob(parent, name);
        }

        public boolean includeInJobList(Item item) {
            return item instanceof SCMTriggerItem;
        }

        @Override
        public String getDisplayName() {
            return Messages.PollerJob_displayName();
        }

        public String getDescription() {
            return Messages.PollerJob_description();
        }
        
    }
    
    @Extension
    public static class Listener extends ItemListener {
        @Override
        public void onDeleted(final Item item) {
            super.onDeleted(item);
            ACL.impersonate(ACL.SYSTEM, new Runnable() {
                
                @Override
                public void run() {
                    for (Item i : Jenkins.getInstance().getAllItems()) {
                        if (i instanceof PollerJob) {
                            PollerJob job = (PollerJob) i;
                            job.checkDeletedItem(item);
                        }
                    }
                }
            });
        }
        
        @Override
        public void onRenamed(final Item item, final String oldFullName, final String newFullName) {
            super.onRenamed(item, oldFullName, newFullName);
            ACL.impersonate(ACL.SYSTEM, new Runnable() {
                
                @Override
                public void run() {
                    for (Item i : Jenkins.getInstance().getAllItems()) {
                        if (i instanceof PollerJob) {
                            PollerJob job = (PollerJob) i;
                            job.checkRenamedItem(item, oldFullName, newFullName);
                        }
                    }
                }
            });
        }
        
        @Override
        public void onUpdated(Item item) {
            if (item instanceof Job<?, ?>) {
                Job<?, ?> job = (Job<?, ?>) item;
                if (job.isBuildable()) {
                    return;
                }
                JobResourceState state = JobResourceState.quietLoad(job);
                if (state == null || state == JobResourceState.NO_RUN) {
                    // Failed to load or not triggered by us.
                    return;
                }
                state = state.markAsDisabled();
                state.quietSave(job);
            }
        }
    }
    
    private void checkDeletedItem(Item item) {
        String name = item.getFullName();
        boolean present;
        synchronized (this) {
            present = jobsToTrigger.remove(name);
        }
        if (present) {
            quietSave();
        }
    }
    
    private void checkRenamedItem(Item item, String oldFullName, String newFullName) {
        boolean present;
        synchronized (this) {
            present = jobsToTrigger.remove(oldFullName);
            if (present) {
                jobsToTrigger.add(newFullName);
            }
        }
        if (present) {
            quietSave();
        }
    }
    
    private void quietSave() {
        try {
            save();
        } catch (IOException e) {
            Logger.getLogger(PollerJob.class.getName()).log(Level.WARNING, "Unable to save", e);
        }
    }
    
    public synchronized boolean triggers(Job<?, ?> job) {
        if (job == null) {
            return false;
        }
        return jobsToTrigger.contains(job.getFullName());
    }
    
    public synchronized Collection<String> getJobsToTrigger() {
        return new ArrayList<String>(jobsToTrigger);
    }

    @SuppressWarnings("deprecation")
    @Override
    public BuildAuthorizationToken getAuthToken() {
        return null;
    }

    @Override
    public int getQuietPeriod() {
        return 0;
    }

    @Override
    public String getBuildNowText() {
        return createParameterizedJobMixIn().getBuildNowText();
    }
    
    public boolean isDisabled() {
        return disabled;
    }
    
    public void setDisabled(boolean newVal) throws IOException {
        if (newVal == disabled) {
            // No change, ignore this change
            return;
        }
        disabled = newVal;
        if (disabled) {
            Jenkins.getInstance().getQueue().cancel(this);
        }
        save();
        ItemListener.fireOnUpdated(this);
    }

    @CLIMethod(name="disable-job")
    @RequirePOST
    public HttpResponse doDisable() throws IOException, ServletException {
        checkPermission(CONFIGURE);
        setDisabled(true);
        return new HttpRedirect(".");
    }

    @CLIMethod(name="enable-job")
    @RequirePOST
    public HttpResponse doEnable() throws IOException, ServletException {
        checkPermission(CONFIGURE);
        setDisabled(false);
        return new HttpRedirect(".");
    }
}
