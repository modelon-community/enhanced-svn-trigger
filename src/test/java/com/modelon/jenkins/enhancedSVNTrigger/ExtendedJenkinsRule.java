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

import hudson.scm.subversion.UpdateUpdater;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.scm.SubversionSCM;

public class ExtendedJenkinsRule extends JenkinsRule {

    public PollerJob createPollerJobForJobs(Job<?, ?> ... jobs) throws IOException {
        PollerJob poller = jenkins.createProject(PollerJob.class, createUniqueProjectName());
        Collection<String> jobNames = new ArrayList<String>();
        for (Job<?, ?> job : jobs) {
            jobNames.add(job.getFullName());
        }
        poller.setJobsToTrigger(jobNames);
        return poller;
    }

    public FreeStyleProject createFreeStyleProjectWithRepos(String ... repos) throws IOException {
        String[] locals = new String[repos.length];
        for (int i = 0; i < repos.length; i++) {
            locals[i] = "repo_" + i;
        }
        FreeStyleProject p = createFreeStyleProject();
        SubversionSCM scm = new SubversionSCM(repos, locals);
        p.setScm(scm);
        return p;
    }

    public FreeStyleProject createFreeStyleProjectWithRepo(String repo) throws IOException {
        return createFreeStyleProjectWithRepo(repo, null);
    }

    public FreeStyleProject createFreeStyleProjectWithRepo(String repo, String includeRegions) throws IOException {
        FreeStyleProject p = createFreeStyleProject();
        SubversionSCM scm = new SubversionSCM(
                SubversionSCM.ModuleLocation.parse(new String[] {repo}, null, new String[] {"."}, null, null),
                new UpdateUpdater(),
                null,
                null,
                null,
                null,
                null,
                includeRegions,
                false,
                false,
                null);
        p.setScm(scm);
        return p;
    }



}
