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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.LinkedHashMap;
import java.util.Map;

import hudson.FilePath;
import hudson.model.Result;
import org.junit.Test;

import com.modelon.jenkins.enhancedSVNTrigger.model.JobResourceState;
import com.modelon.jenkins.enhancedSVNTrigger.model.NewRepoTriggerCause;
import com.modelon.jenkins.enhancedSVNTrigger.model.ResumeTriggerCause;

import hudson.model.CauseAction;
import hudson.model.FreeStyleProject;
import hudson.slaves.EnvironmentVariablesNodeProperty;

public class GeneralTest extends TestBase {
    /**
     * When [ci skip] is used on a commit, assert that no job is triggered, and that the state is updated with that commit,
     * so that the next time a build is triggered that commit is used.
     * @throws Exception
     */
    @Test
    public void ciSkipCommand() throws Exception {
        String repo1 = hostZipRepo("svnRepos/empty.zip");
        FilePath co1 = checkOut(repo1);
        String[] repos = new String[] {repo1};
        FreeStyleProject p = j.createFreeStyleProjectWithRepos(repo1);
        BuildTrigger t = addTrigger(p);

        t.run();
        j.waitUntilNoActivity();
        assertEquals(1, p.getLastBuild().getNumber());
        //assertState
        Map<String, Long> expectedInitialState = new LinkedHashMap<String, Long>();
        expectedInitialState.put(repo1, (long) 0);
        assertEquals(new JobResourceState(expectedInitialState), JobResourceState.load(p));
        editAndCommitWithMsg(co1, "a commit message with the tag [ci skip] in it", "Second  commit some more text", new String[] {"a.txt"});
        t.run();
        j.waitUntilNoActivity();
        assertEquals(1, p.getLastBuild().getNumber());
        //assertState
        Map<String, Long> expectedState = new LinkedHashMap<String, Long>();
        expectedState.put(repo1, (long) 1);
        assertEquals(new JobResourceState(expectedState), JobResourceState.load(p));

    }

    /**
     * When [ci skip] is used on a commit, verify that that commit is used the next time a build is triggered due to a commit on another repository that is part of the job, and that the state is updated.
     * @throws Exception
     */
    @Test
    public void ciSkipThenCommitOnOther() throws Exception {
        String repo1 = hostZipRepo("svnRepos/empty.zip");
        FilePath co1 = checkOut(repo1);
        String repo2 = hostZipRepo("svnRepos/empty.zip");
        FilePath co2 = checkOut(repo2);
        String[] repos = new String[] {repo1, repo2};
        FreeStyleProject p = j.createFreeStyleProjectWithRepos(repo1, repo2);
        BuildTrigger t = addTrigger(p);

        t.run();
        j.waitUntilNoActivity();
        assertEquals(1, p.getLastBuild().getNumber());
        //assertState
        Map<String, Long> expectedInitialState = new LinkedHashMap<String, Long>();
        expectedInitialState.put(repo1, (long) 0);
        expectedInitialState.put(repo2, (long) 0);
        assertEquals(new JobResourceState(expectedInitialState), JobResourceState.load(p));
        editAndCommitWithMsg(co1, "a commit message with the tag [ci skip] in it", "Second  commit some more text", new String[] {"a.txt"});
        editAndCommitWithMsg(co2, "a commit message on the other repo without the tag in it", "Second  commit some more text", new String[] {"a.txt"});
        t.run();
        j.waitUntilNoActivity();
        assertEquals(2, p.getLastBuild().getNumber());
        assertRevisions(p.getBuildByNumber(2), repos, new long[] {1,1});
        //assertState
        Map<String, Long> expectedState = new LinkedHashMap<String, Long>();
        expectedState.put(repo1, (long) 1);
        expectedState.put(repo2, (long) 1);
        assertEquals(new JobResourceState(expectedState), JobResourceState.load(p));

    }

    @Test
    public void twoReposStaggeredCommits() throws Exception {
        String repo1 = hostZipRepo("svnRepos/empty.zip");
        FilePath co1 = checkOut(repo1);
        String repo2 = hostZipRepo("svnRepos/empty.zip");
        FilePath co2 = checkOut(repo2);
        String[] repos = new String[] {repo1, repo2};
        FreeStyleProject p = j.createFreeStyleProjectWithRepos(repo1, repo2);
        BuildTrigger t = addTrigger(p);

        t.run();
        j.waitUntilNoActivity();
        assertEquals(1, p.getLastBuild().getNumber());
        editAndCommit(co1, "Second!", "a.txt");
        editAndCommit(co2, "Third!", "a.txt");
        editAndCommit(co1, "Forth!", "a.txt");
        editAndCommit(co2, "Fifth!", "a.txt");
        editAndCommit(co2, "Sixth!", "a.txt");
        editAndCommit(co1, "Seventh!", "a.txt");
        t.run();
        j.waitUntilNoActivity();
        assertEquals(8, p.getNextBuildNumber());
        assertRevisions(p.getBuildByNumber(2), repos, new long[] {1,0});
        assertRevisions(p.getBuildByNumber(3), repos, new long[] {1,1});
        assertRevisions(p.getBuildByNumber(4), repos, new long[] {2,1});
        assertRevisions(p.getBuildByNumber(5), repos, new long[] {2,2});
        assertRevisions(p.getBuildByNumber(6), repos, new long[] {2,3});
        assertRevisions(p.getBuildByNumber(7), repos, new long[] {3,3});
    }

    @Test
    public void globalVarsInURL() throws Exception {
        String actualUrl = hostZipRepo("svnRepos/empty.zip");
        EnvironmentVariablesNodeProperty env = j.jenkins.getGlobalNodeProperties().get(EnvironmentVariablesNodeProperty.class);
        if (env == null) {
            env = new EnvironmentVariablesNodeProperty();
            j.jenkins.getGlobalNodeProperties().add(env);
        }
        env.getEnvVars().put("SVN_URL", actualUrl);
        FreeStyleProject p = j.createFreeStyleProjectWithRepo("${SVN_URL}");
        BuildTrigger t = addTrigger(p);

        t.run();
        j.waitUntilNoActivity();
        assertEquals(1, p.getLastBuild().getNumber());
        editAndCommit(p, "Second!", "a.txt");
        editAndCommit(p, "Third!", "a.txt");
        t.run();
        j.waitUntilNoActivity();
        assertEquals(3, p.getLastBuild().getNumber());
    }

    @Test
    public void peggedRevisionInURL() throws Exception {
        String actualUrl = hostZipRepo("svnRepos/empty.zip");
        FilePath repoCO = checkOut(actualUrl);
        FreeStyleProject p = j.createFreeStyleProjectWithRepo(actualUrl + "@0");
        PollerJob poller = j.createPollerJobForJobs(p);

        poller.scheduleBuild();
        j.waitUntilNoActivity();

        assertNull(p.getLastBuild());
        assertEquals(1, poller.getLastBuild().getNumber());
        assertEquals(Result.SUCCESS, poller.getLastBuild().getResult());

        editAndCommit(repoCO, "Second!", "a.txt");
        poller.scheduleBuild();
        j.waitUntilNoActivity();

        assertNull(p.getLastBuild());
        assertEquals(2, poller.getLastBuild().getNumber());
        assertEquals(Result.SUCCESS, poller.getLastBuild().getResult());
    }

    @Test
    public void disabledJobResume() throws Exception {
        FreeStyleProject p = j.createFreeStyleProjectWithRepo(hostZipRepo("svnRepos/empty.zip"));
        BuildTrigger t = addTrigger(p);
        
        t.run();
        j.waitUntilNoActivity();
        
        assertEquals(1, p.getLastBuild().getNumber());
        assertNotNull("Trigger cause was not set properly!", p.getLastBuild().getAction(CauseAction.class).findCause(NewRepoTriggerCause.class));
        
        p.disable();
        
        t.run();
        j.waitUntilNoActivity();
        
        // No build should be made if nothing has changed!
        assertEquals(1, p.getLastBuild().getNumber());
        
        p.enable();
        
        editAndCommit(p, "Second!", "a.txt");
        editAndCommit(p, "Third!", "a.txt");
        
        t.run();
        j.waitUntilNoActivity();
        
        assertEquals(2, p.getLastBuild().getNumber());
        assertNotNull("Trigger cause was not set properly!", p.getLastBuild().getAction(CauseAction.class).findCause(ResumeTriggerCause.class));
    }
    
    @Test
    public void svnRevisionInRebuild() throws Exception {
        String svnUrl = hostZipRepo("svnRepos/empty.zip");
        FreeStyleProject p = j.createFreeStyleProjectWithRepo(svnUrl);
        BuildTrigger t = addTrigger(p);
        
        t.run();
        j.waitUntilNoActivity();
        
        assertEquals(1, p.getLastBuild().getNumber());
        assertRevision(p.getLastBuild(), svnUrl, 0);
        
        editAndCommit(p, "First!", "a.txt");
        
        t.run();
        j.waitUntilNoActivity();
        
        assertEquals(2, p.getLastBuild().getNumber());
        assertRevision(p.getLastBuild(), svnUrl, 1);
        
        j.createWebClient().getPage(p, "1/rebuild");
        j.waitUntilNoActivity();
        
        assertEquals(3, p.getLastBuild().getNumber());
        assertRevision(p.getLastBuild(), svnUrl, 0);
    }

    /**
     * Ensure that filtering and similar works. This also demonstrates how to
     * use filtering.
     */
    @Test
    public void includeFilterTest() throws Exception {
        String svnUrl = hostZipRepo("svnRepos/empty.zip");
        FreeStyleProject p = j.createFreeStyleProjectWithRepo(svnUrl, ".*/a\\.txt");
        BuildTrigger t = addTrigger(p);

        t.run();
        j.waitUntilNoActivity();

        assertEquals(1, p.getLastBuild().getNumber());
        assertRevision(p.getLastBuild(), svnUrl, 0);

        editAndCommit(p, "First!", "a.txt");
        t.run();
        j.waitUntilNoActivity();

        assertEquals(2, p.getLastBuild().getNumber());
        assertRevision(p.getLastBuild(), svnUrl, 1);

        editAndCommit(p, "Second!", "b.txt");
        t.run();
        j.waitUntilNoActivity();

        assertEquals(2, p.getLastBuild().getNumber());

        editAndCommit(p, "Third!", "a.txt");
        t.run();
        j.waitUntilNoActivity();

        assertEquals(3, p.getLastBuild().getNumber());
        assertRevision(p.getLastBuild(), svnUrl, 3);
    }

    /**
     * This tests a bug where job A and B share the same co bug job A
     * only include part of the co in polling. Then B was triggered
     * incorrectly each time the poller ran!
     */
    @Test
    public void twoJobsWithPartiallySameRepo() throws Exception {
        String svnUrl = hostZipRepo("svnRepos/empty.zip");
        FilePath co = checkOut(svnUrl);
        editAndCommit(co, "Init", "a.txt", "b.txt");
        FreeStyleProject A = j.createFreeStyleProjectWithRepo(svnUrl, ".*/a\\.txt");
        FreeStyleProject B = j.createFreeStyleProjectWithRepos(svnUrl);
        PollerJob poller = j.createPollerJobForJobs(A, B);

        poller.scheduleBuild();
        j.waitUntilNoActivity();

        assertEquals(1, A.getLastBuild().getNumber());
        assertEquals(1, B.getLastBuild().getNumber());
        assertRevision(A.getLastBuild(), svnUrl, 1);
        assertRevision(B.getLastBuild(), svnUrl, 1);

        editAndCommit(co, "First!", "b.txt");
        poller.scheduleBuild();
        j.waitUntilNoActivity();

        assertEquals(1, A.getLastBuild().getNumber());
        assertEquals(2, B.getLastBuild().getNumber());
        assertRevision(A.getLastBuild(), svnUrl, 1);
        assertRevision(B.getLastBuild(), svnUrl, 2);

        poller.scheduleBuild();
        j.waitUntilNoActivity();

        assertEquals(1, A.getLastBuild().getNumber());
        assertEquals(2, B.getLastBuild().getNumber());
        assertRevision(A.getLastBuild(), svnUrl, 1);
        assertRevision(B.getLastBuild(), svnUrl, 2);
    }
}
