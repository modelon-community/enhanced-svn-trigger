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

import org.junit.Test;

import com.modelon.jenkins.enhancedSVNTrigger.BuildTrigger;
import com.modelon.jenkins.enhancedSVNTrigger.TestBase;

import hudson.model.FreeStyleProject;
import hudson.scm.SubversionSCM;

public class FreeStyleTest extends TestBase {

    @Test
    public void simple() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        SubversionSCM scm = new SubversionSCM(hostZipRepo("svnRepos/empty.zip"));
        p.setScm(scm);
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


}
