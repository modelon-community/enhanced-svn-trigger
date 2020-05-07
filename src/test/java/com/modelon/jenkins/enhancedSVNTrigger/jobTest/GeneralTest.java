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
package com.modelon.jenkins.enhancedSVNTrigger.jobTest;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import org.junit.Ignore;
import org.junit.Test;
import org.jvnet.hudson.test.MockFolder;

import com.modelon.jenkins.enhancedSVNTrigger.PollerJob;
import com.modelon.jenkins.enhancedSVNTrigger.TestBase;

import hudson.FilePath;
import hudson.model.FreeStyleProject;
import hudson.model.Result;

public class GeneralTest extends TestBase {
    @Test
    public void twoJobsSameRepo() throws Exception {
        String repo = hostZipRepo("svnRepos/empty.zip");
        FilePath repoCO = checkOut(repo);
        
        FreeStyleProject job1 = j.createFreeStyleProjectWithRepos(repo);
        FreeStyleProject job2 = j.createFreeStyleProjectWithRepos(repo);
        PollerJob poller = j.createPollerJobForJobs(job1, job2);
        
        poller.scheduleBuild();
        j.waitUntilNoActivity();
        
        assertEquals(Result.SUCCESS, poller.getLastBuild().getResult());
        assertEquals(1, job1.getLastBuild().getNumber());
        assertEquals(1, job2.getLastBuild().getNumber());
        
        editAndCommit(repoCO, "A change!", "a.txt");
        editAndCommit(repoCO, "Another change!", "a.txt");
        
        poller.scheduleBuild();
        j.waitUntilNoActivity();
        
        assertEquals(Result.SUCCESS, poller.getLastBuild().getResult());
        assertEquals(3, job1.getLastBuild().getNumber());
        assertEquals(3, job2.getLastBuild().getNumber());
    }

    @Test
    public void jobRename() throws Exception {
        FreeStyleProject job = j.createFreeStyleProject();
        PollerJob poller = j.createPollerJobForJobs(job);
        
        job.renameTo(job.getName() + "_renamed");
        
        Collection<String> jobsToTrigger = poller.getJobsToTrigger();
        assertEquals(1, jobsToTrigger.size());
        assertEquals(job.getFullName(), jobsToTrigger.iterator().next());
    }

    @Test
    public void jobDelete() throws Exception {
        FreeStyleProject job = j.createFreeStyleProject();
        PollerJob poller = j.createPollerJobForJobs(job);
        
        job.delete();
        
        Collection<String> jobsToTrigger = poller.getJobsToTrigger();
        assertEquals(0, jobsToTrigger.size());
    }

    @Test
    public void jobInFolder() throws IOException {
        MockFolder folder = j.createFolder("a_folder");
        FreeStyleProject job = folder.createProject(FreeStyleProject.class, "a_job");
        
        PollerJob poller = j.createPollerJobForJobs(job);
        
        Collection<String> jobsToTrigger = poller.getJobsToTrigger();
        assertEquals(1, jobsToTrigger.size());
        assertEquals(job.getFullName(), jobsToTrigger.iterator().next());
    }

    @Test @Ignore("This is a known limitation")
    public void folderRename() throws IOException {
        MockFolder folder = j.createFolder("a_folder");
        FreeStyleProject job = folder.createProject(FreeStyleProject.class, "a_job");
        
        PollerJob poller = j.createPollerJobForJobs(job);
        
        folder.renameTo("a_folder2");
        
        Collection<String> jobsToTrigger = poller.getJobsToTrigger();
        assertEquals(1, jobsToTrigger.size());
        assertEquals(job.getFullName(), jobsToTrigger.iterator().next());
    }

    @Test
    public void newJobAdded() throws Exception {
        String repo = hostZipRepo("svnRepos/empty.zip");
        FilePath repoCO = checkOut(repo);
        
        FreeStyleProject job1 = j.createFreeStyleProjectWithRepos(repo);
        PollerJob poller = j.createPollerJobForJobs(job1);
        
        poller.scheduleBuild();
        j.waitUntilNoActivity();
        
        assertEquals(Result.SUCCESS, poller.getLastBuild().getResult());
        assertEquals(1, job1.getLastBuild().getNumber());
        
        editAndCommit(repoCO, "A change!", "a.txt");
        editAndCommit(repoCO, "Another change!", "a.txt");
        
        poller.scheduleBuild();
        j.waitUntilNoActivity();
        
        assertEquals(Result.SUCCESS, poller.getLastBuild().getResult());
        assertEquals(3, job1.getLastBuild().getNumber());
        
        FreeStyleProject job2 = j.createFreeStyleProjectWithRepos(repo);
        poller.setJobsToTrigger(Arrays.asList(job1.getFullName(), job2.getFullName()));
        
        poller.scheduleBuild();
        j.waitUntilNoActivity();
        
        assertEquals(Result.SUCCESS, poller.getLastBuild().getResult());
        assertEquals(3, job1.getLastBuild().getNumber());
        assertEquals(1, job2.getLastBuild().getNumber());
    }

    @Test
    public void jobRemovedAndReadded() throws Exception {
        String repo = hostZipRepo("svnRepos/empty.zip");
        FilePath repoCO = checkOut(repo);
        
        FreeStyleProject job1 = j.createFreeStyleProjectWithRepos(repo);
        FreeStyleProject job2 = j.createFreeStyleProjectWithRepos(repo);
        PollerJob poller = j.createPollerJobForJobs(job1, job2);
        
        poller.scheduleBuild();
        j.waitUntilNoActivity();
        
        assertEquals(Result.SUCCESS, poller.getLastBuild().getResult());
        assertEquals(1, job1.getLastBuild().getNumber());
        assertEquals(1, job2.getLastBuild().getNumber());
        
        editAndCommit(repoCO, "A change!", "a.txt");
        editAndCommit(repoCO, "Another change!", "a.txt");
        poller.setJobsToTrigger(Arrays.asList(job1.getFullName()));
        
        poller.scheduleBuild();
        j.waitUntilNoActivity();
        
        assertEquals(Result.SUCCESS, poller.getLastBuild().getResult());
        assertEquals(3, job1.getLastBuild().getNumber());
        assertEquals(1, job2.getLastBuild().getNumber());
        
        poller.setJobsToTrigger(Arrays.asList(job1.getFullName(), job2.getFullName()));
        
        poller.scheduleBuild();
        j.waitUntilNoActivity();
        
        assertEquals(Result.SUCCESS, poller.getLastBuild().getResult());
        assertEquals(3, job1.getLastBuild().getNumber());
        assertEquals(3, job2.getLastBuild().getNumber());
    }
}
