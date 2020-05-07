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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import hudson.model.BuildListener;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.Queue.Executable;
import jenkins.model.Jenkins;
import jenkins.model.lazy.BuildReference;
import jenkins.model.lazy.LazyBuildMixIn.LazyLoadingRun;
import jenkins.model.lazy.LazyBuildMixIn.RunMixIn;
import jenkins.model.queue.AsynchronousExecution;

public class PollerRun extends Run<PollerJob, PollerRun> implements LazyLoadingRun<PollerJob, PollerRun>, Executable {

    private transient final RunMixIn<PollerJob,PollerRun> runMixIn = new RunMixIn<PollerJob, PollerRun>() {
        @Override protected PollerRun asRun() {
            return PollerRun.this;
        }
    };
    
    private Collection<String> jobsToTrigger;

    public PollerRun(PollerJob project) throws IOException {
        super(project);
        jobsToTrigger = project.getJobsToTrigger();
    }
    
    public PollerRun(PollerJob job, File buildDir) throws IOException {
        super(job, buildDir);
    }
    
    @Override
    protected void onLoad() {
        super.onLoad();
        if (jobsToTrigger == null) {
            jobsToTrigger = Collections.emptyList();
        }
    }

    @Override
    public void run() throws AsynchronousExecution {
        execute(new RunExecution() {
            
            @Override
            public Result run(BuildListener listener) throws Exception, RunnerAbortedException {
                Collection<Job<?, ?>> jobs = new ArrayList<Job<?, ?>>();
                for (String jobFullName : jobsToTrigger) {
                    Job<?, ?> job = Jenkins.getInstance().getItemByFullName(jobFullName, Job.class);
                    if (job == null) {
                        listener.error("Unable to find job " + jobFullName + "!");
                        setResult(Result.UNSTABLE);
                        continue;
                    }
                    jobs.add(job);
                }
                return RevisionPoller.pollForChanges(getParent(), jobs, listener);
            }
            
            @Override
            public void post(BuildListener listener) throws Exception {
            }
            
            @Override
            public void cleanUp(BuildListener listener) throws Exception {
            }
        });
    }

    @Override
    public RunMixIn<PollerJob, PollerRun> getRunMixIn() {
        return runMixIn;
    }
    
    @Override
    public PollerRun getNextBuild() {
        return runMixIn.getNextBuild();
    }
    
    @Override
    public PollerRun getPreviousBuild() {
        return runMixIn.getPreviousBuild();
    }
    
    @Override
    protected BuildReference<PollerRun> createReference() {
        return runMixIn.createReference();
    }
    
    @Override
    protected void dropLinks() {
        runMixIn.dropLinks();
    }
    
}
