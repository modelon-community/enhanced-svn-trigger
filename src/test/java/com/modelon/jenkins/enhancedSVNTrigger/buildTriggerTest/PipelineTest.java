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
package com.modelon.jenkins.enhancedSVNTrigger.buildTriggerTest;

import static org.junit.Assert.assertEquals;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Test;

import com.modelon.jenkins.enhancedSVNTrigger.BuildTrigger;
import com.modelon.jenkins.enhancedSVNTrigger.TestBase;

import hudson.FilePath;

public class PipelineTest extends TestBase {
    
    @Test
    public void simple() throws Exception {
        String repo = hostZipRepo("svnRepos/empty.zip");
        FilePath repoCO = checkOut(repo);
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                "  svn(url:'" + repo + "')\n" +
                "}"
        ));
        BuildTrigger t = addTrigger(p);
        
        p.scheduleBuild2(0); // A dummy build so that we get SVN info
        j.waitUntilNoActivity();
        t.run(); // This will trigger a new build since we don't have any previous trigger info
        j.waitUntilNoActivity();
        assertEquals(2, p.getLastBuild().getNumber());
        editAndCommit(repoCO, "Second!", "a.txt");
        editAndCommit(repoCO, "Third!", "a.txt");
        t.run();
        j.waitUntilNoActivity();
        assertEquals(4, p.getLastBuild().getNumber());
    }

}
