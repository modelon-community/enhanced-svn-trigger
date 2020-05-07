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

import java.util.Arrays;
import java.util.Collections;

import com.modelon.jenkins.enhancedSVNTrigger.model.*;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Test;

import hudson.FilePath;
import hudson.model.CauseAction;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.scm.SubversionSCM;

public class TriggerCauseTest extends TestBase {
    @Test
    public void triggerCause() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        String svnUrl = hostZipRepo("svnRepos/empty.zip");
        SubversionSCM scm = new SubversionSCM(svnUrl);
        p.setScm(scm);
        BuildTrigger t = addTrigger(p);
        
        t.run();
        j.waitUntilNoActivity();
        NewRepoTriggerCause newRepoCause = p.getLastBuild().getAction(CauseAction.class).findCause(NewRepoTriggerCause.class);
        assertNotNull("Trigger cause was not set properly!", newRepoCause);
        assertNull("Trigger cause was not set properly!", p.getLastBuild().getAction(CauseAction.class).findCause(NewRevisionTriggerCause.class));
        assertEquals(1, newRepoCause.newLocations.size());
        assertEquals(svnUrl, newRepoCause.newLocations.get(0));
        assertEquals("NEW_REPO", newRepoCause.triggerCauseLabel());
        
        editAndCommit(p, "Second!", "a.txt");
        t.run();
        j.waitUntilNoActivity();
        assertNull("Trigger cause was not set properly!", p.getLastBuild().getAction(CauseAction.class).findCause(NewRepoTriggerCause.class));
        NewRevisionTriggerCause newRevCause = p.getLastBuild().getAction(CauseAction.class).findCause(NewRevisionTriggerCause.class);
        assertNotNull("Trigger cause was not set properly!", newRevCause);
        assertEquals(svnUrl, newRevCause.location);
        assertEquals(1, newRevCause.revision);
        assertEquals(svnUrl + "@1", newRevCause.triggerCauseLabel());
    }

    @Test
    public void oneSimplificationRule() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        String svnUrl = hostZipRepo("svnRepos/empty.zip");
        SubversionSCM scm = new SubversionSCM(svnUrl);
        p.setScm(scm);
        BuildTrigger.getMyDescriptor().setLabelSimplifications(Collections.singletonList(new LabelSimplificationRule(".*", "REPLACED")));
        BuildTrigger t = addTrigger(p);
        
        t.run();
        j.waitUntilNoActivity();
        editAndCommit(p, "Second!", "a.txt");
        t.run();
        j.waitUntilNoActivity();
        NewRevisionTriggerCause newRevCause = p.getLastBuild().getAction(CauseAction.class).findCause(NewRevisionTriggerCause.class);
        assertEquals("REPLACED@1", newRevCause.triggerCauseLabel());
    }

    @Test
    public void environmentVariabe() throws Exception {
        String svnUrl = hostZipRepo("svnRepos/empty.zip");
        FilePath repoCO = checkOut(svnUrl);
        FreeStyleProject p = j.createFreeStyleProject();
        EnvVarRecordBuilder verifier = new EnvVarRecordBuilder();
        p.getBuildersList().add(verifier);
        SubversionSCM scm = new SubversionSCM(svnUrl);
        p.setScm(scm);
        BuildTrigger t = addTrigger(p);
        
        p.scheduleBuild2(0); // build started without our trigger
        j.waitUntilNoActivity();
        verifier.assertEnvVars("TRIGGER_CAUSE", null);
        
        t.run(); // This will trigger a new build since we don't have any previous trigger info
        j.waitUntilNoActivity();
        verifier.assertEnvVars("TRIGGER_CAUSE", "NEW_REPO");
        
        editAndCommit(repoCO, "Second!", "a.txt");
        t.run();
        j.waitUntilNoActivity();
        verifier.assertEnvVars("TRIGGER_CAUSE", svnUrl + "@1");
    }

    @Test
    public void extraEnvironmentVariabes() throws Exception {
        String svnUrl = hostZipRepo("svnRepos/empty.zip");
        FilePath repoCO = checkOut(svnUrl);
        FreeStyleProject p = j.createFreeStyleProject();
        EnvVarRecordBuilder verifier = new EnvVarRecordBuilder();
        p.getBuildersList().add(verifier);
        SubversionSCM scm = new SubversionSCM(svnUrl);
        p.setScm(scm);
        BuildTrigger.getMyDescriptor().setCommitMessageParameterRules(Arrays.asList(
                    new CommitMessageParameterRule("^PARAM_1=(.+)$", "$1", "PARAM_1"),
                    new CommitMessageParameterRule("^FOO=(.+)$", "RAB", "FOO")
                ));
        BuildTrigger t = addTrigger(p);
        
        t.run(); // This will trigger a new build since we don't have any previous trigger info
        j.waitUntilNoActivity();
        
        String msg = "A commit\n"
                + "PARAM_1=ASD_123\n"
                + "FOO=BAR";
        editAndCommit(repoCO, msg, "a.txt");
        
        t.run();
        j.waitUntilNoActivity();
        verifier.assertEnvVars(
                "TRIGGER_CAUSE", svnUrl + "@1",
                "PARAM_1", "ASD_123",
                "FOO", "RAB"
        );
    }
    
    @Test
    public void triggerCauseEnvVarOverride() throws Exception {
        String svnUrl = hostZipRepo("svnRepos/empty.zip");
        FilePath repoCO = checkOut(svnUrl);
        FreeStyleProject p = j.createFreeStyleProject();
        EnvVarRecordBuilder verifier = new EnvVarRecordBuilder();
        p.getBuildersList().add(verifier);
        SubversionSCM scm = new SubversionSCM(svnUrl);
        p.setScm(scm);
        BuildTrigger.getMyDescriptor().setCommitMessageParameterRules(Arrays.asList(
                new CommitMessageParameterRule("^TRIGGER_CAUSE=(.+)$", "$1", "TRIGGER_CAUSE")
        ));
        BuildTrigger t = addTrigger(p);
        
        t.run(); // This will trigger a new build since we don't have any previous trigger info
        j.waitUntilNoActivity();
        
        String msg = "A commit\n"
                + "TRIGGER_CAUSE=this should be the trigger cause\n"
                + "SOME_OTHER_THING=FOO_BAR";
        editAndCommit(repoCO, msg, "a.txt");
        
        t.run();
        j.waitUntilNoActivity();
        verifier.assertEnvVars("TRIGGER_CAUSE", "this should be the trigger cause");
    }


    @Test
    public void labelSimplification() throws Exception {
        LabelSimplificationRule rule = new LabelSimplificationRule("a(b|c)d", "REPLACE");
        assertEquals("REPLACE", rule.simplify("abd"));
        assertEquals("REPLACE", rule.simplify("acd"));
        assertEquals("abc", rule.simplify("abc"));
        assertEquals("aabd", rule.simplify("aabd"));
    }
    
    @Test
    public void labelSimplificationGroupReplace() throws Exception {
        LabelSimplificationRule rule = new LabelSimplificationRule("http://a\\.b/(.+)", "$1");
        assertEquals("asd", rule.simplify("http://a.b/asd"));
        assertEquals("abc", rule.simplify("abc"));
    }
    
    @Test
    public void labelSimplificationGroupReplaceError() throws Exception {
        LabelSimplificationRule rule = new LabelSimplificationRule("abc", "WillNotWork$1");
        assertEquals("abc", rule.simplify("abc"));
    }
    
    @Test
    public void labelSimplificationIncorrectPattern() throws Exception {
        LabelSimplificationRule rule = new LabelSimplificationRule("[abc", "ShouldNotWork");
        assertEquals("abc", rule.simplify("abc"));
    }
    
    @Test
    public void labelSimplificationGroupEscape() throws Exception {
        LabelSimplificationRule rule = new LabelSimplificationRule("a(b|c)d", "\\$12$1");
        assertEquals("$12b", rule.simplify("abd"));
    }
    
    @Test
    public void pipelineTest() throws Exception {
        String svnUrl = hostZipRepo("svnRepos/empty.zip");
        FilePath repoCO = checkOut(svnUrl);
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        BuildTrigger t = addTrigger(p);
        
        p.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                "  svn(url:'" + svnUrl + "')\n" +
                "  if (env.TRIGGER_CAUSE != null) {\n" +
                "    error \"TRIGGER_CAUSE not set correctly! Should be null but was '${env.TRIGGER_CAUSE}'\";\n" +
                "  }\n" +
                "}\n"
                ));
        p.scheduleBuild2(0); // A dummy build so that we get SVN info
        j.waitUntilNoActivity();
        assertEquals(p.getLastBuild().getLog(), Result.SUCCESS, p.getLastBuild().getResult());
        
        
        p.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                "  svn(url:'" + svnUrl + "')\n" +
                "  if (!'NEW_REPO'.equals(env.TRIGGER_CAUSE)) {\n" +
                "    error \"TRIGGER_CAUSE not set correctly! Should be 'NEW_REPO' but was '${env.TRIGGER_CAUSE}'\";\n" +
                "  }\n" +
                "}\n"
        ));
        t.run(); // This will trigger a new build since we don't have any previous trigger info
        j.waitUntilNoActivity();
        assertEquals(p.getLastBuild().getLog(), Result.SUCCESS, p.getLastBuild().getResult());
        
        p.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                "  svn(url:'" + svnUrl + "')\n" +
                "  def labelValue=\"" + svnUrl.replaceAll("\\\\", "\\\\") + "@1\";" +
                "  if (!labelValue.equals(env.TRIGGER_CAUSE)) {\n" +
                "    error \"TRIGGER_CAUSE not set correctly! Should be '${labelValue}' but was '${env.TRIGGER_CAUSE}'\";\n" +
                "  }\n" +
                "}\n"
        ));
        editAndCommit(repoCO, "Second!", "a.txt");
        t.run();
        j.waitUntilNoActivity();
        assertEquals(p.getLastBuild().getLog(), Result.SUCCESS, p.getLastBuild().getResult());
    }
    
    @Test
    public void envVarInRebuild() throws Exception {
        String svnUrl = hostZipRepo("svnRepos/empty.zip");
        FreeStyleProject p = j.createFreeStyleProject();
        EnvVarRecordBuilder verifier = new EnvVarRecordBuilder();
        p.getBuildersList().add(verifier);
        SubversionSCM scm = new SubversionSCM(svnUrl);
        p.setScm(scm);
        BuildTrigger t = addTrigger(p);
        
        t.run();
        j.waitUntilNoActivity();
        assertEquals(1, p.getLastBuild().getNumber());
        verifier.assertEnvVars("TRIGGER_CAUSE", "NEW_REPO");
        
        j.createWebClient().getPage(p, "1/rebuild");
        j.waitUntilNoActivity();
        
        assertEquals(2, p.getLastBuild().getNumber());
        verifier.assertEnvVars("TRIGGER_CAUSE", "NEW_REPO");
        
        editAndCommit(p, "Second!", "a.txt");
        t.run();
        j.waitUntilNoActivity();
        
        assertEquals(3, p.getLastBuild().getNumber());
        verifier.assertEnvVars("TRIGGER_CAUSE", svnUrl + "@1");
        
        j.createWebClient().getPage(p, "3/rebuild");
        j.waitUntilNoActivity();
        
        assertEquals(4, p.getLastBuild().getNumber());
        verifier.assertEnvVars("TRIGGER_CAUSE", svnUrl + "@1");
    }

    @Test
    public void revisionInfoTestMultipleInSame() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        String repo1 = hostZipRepo("svnRepos/empty.zip");
        String repo2 = hostZipRepo("svnRepos/empty.zip");
        String[] repos = new String[] {repo1, repo2};
        SubversionSCM scm = new SubversionSCM(repos, new String[] {"repo1", "repo2"});
        p.setScm(scm);
        PollerJob poller = j.createPollerJobForJobs(p);


        poller.scheduleBuild();
        j.waitUntilNoActivity();

        assertEquals(1, p.getLastBuild().getNumber());
        editAndCommit(p, "First!", "repo1/a.txt");
        editAndCommit(p, "Second!", "repo1/a.txt");

        poller.scheduleBuild();
        j.waitUntilNoActivity();

        assertEquals(4, p.getNextBuildNumber());
        assertRevisionInfo(p.getBuildByNumber(2), repos, new long[] {1, 0});
        assertRevisionInfo(p.getBuildByNumber(3), repos, new long[] {2, 0});
    }

    @Test
    public void revisionInfoTestSubPath() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        String repo1 = hostZipRepo("svnRepos/trunkBranchesTags.zip");
        String[] repos = new String[] {repo1 + "/trunk"};
        SubversionSCM scm = new SubversionSCM(repos, new String[] {"repo1"});
        p.setScm(scm);
        BuildTrigger t = addTrigger(p);

        t.run();
        j.waitUntilNoActivity();

        assertEquals(1, p.getLastBuild().getNumber());
        editAndCommit(p, "First!", "repo1/a.txt");
        editAndCommit(p, "Second!", "repo1/a.txt");

        t.run();
        j.waitUntilNoActivity();

        assertEquals(4, p.getNextBuildNumber());
        assertRevisionInfo(p.getBuildByNumber(2), repos, new long[] {2});
        assertRevisionInfo(p.getBuildByNumber(3), repos, new long[] {3});
    }

}
