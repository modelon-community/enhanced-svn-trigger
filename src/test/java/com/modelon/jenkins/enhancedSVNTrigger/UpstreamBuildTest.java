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

import com.modelon.jenkins.enhancedSVNTrigger.model.CommitMessageParameterRule;
import com.modelon.jenkins.enhancedSVNTrigger.model.NewRepoTriggerCause;
import com.modelon.jenkins.enhancedSVNTrigger.model.NewRevisionTriggerCause;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.scm.SubversionSCM;
import jenkins.model.Jenkins;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class UpstreamBuildTest extends TestBase {
    @Test
    public void triggerCause() throws Exception {
        BuildTrigger.getMyDescriptor().setCommitMessageParameterRules(Arrays.asList(
                new CommitMessageParameterRule("^BUILD_URL=(.+)$", "$1", "UPSTREAM_BUILD_URL")
        ));
        FreeStyleProject upstream = j.createFreeStyleProject();
        FreeStyleBuild upstreamBuild = j.buildAndAssertSuccess(upstream);
        String upstreamBuildUrl = Jenkins.getInstance().getRootUrl() + upstreamBuild.getUrl();

        FreeStyleProject downstream = j.createFreeStyleProject();
        String svnUrl = hostZipRepo("svnRepos/empty.zip");
        SubversionSCM scm = new SubversionSCM(svnUrl);
        downstream.setScm(scm);
        BuildTrigger t = addTrigger(downstream);

        t.run();
        j.waitUntilNoActivity();
        assertEquals(1, downstream.getLastBuild().getNumber());

        String msg = "A commit\n"
                + "BUILD_URL=" + upstreamBuildUrl + "\n"
                + "SOME_OTHER_THING=FOO_BAR";
        editAndCommit(downstream, msg, "a.txt");
        t.run();
        j.waitUntilNoActivity();
        FreeStyleBuild build = downstream.getLastBuild();
        assertEquals(2, build.getNumber());
        Cause.UpstreamCause upstreamCause = build.getCause(Cause.UpstreamCause.class);
        assertNotNull(upstreamCause);
        assertEquals(upstreamBuild, upstreamCause.getUpstreamRun());
    }

}
