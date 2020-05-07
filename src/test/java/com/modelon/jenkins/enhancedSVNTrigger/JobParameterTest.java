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

import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule.WebClient;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.modelon.jenkins.enhancedSVNTrigger.model.parameters.SVNParameterDefinition;

import hudson.model.FreeStyleProject;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.scm.SubversionSCM;

public class JobParameterTest extends TestBase {

    @Test
    public void noSVNResources() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(new SVNParameterDefinition("SVN")));
        WebClient client = j.createWebClient();
        client.getOptions().setThrowExceptionOnFailingStatusCode(false);
        HtmlPage page = client.getPage(p, "build");
        HtmlForm form = page.getFormByName("parameters");
        j.submit(form);
        j.waitUntilNoActivity();
        
        assertEquals(1, p.getLastBuild().getNumber());
        assertEquals(Result.SUCCESS, p.getLastBuild().getResult());
    }
    
    @Test
    public void oneSVNResource() throws Exception {
        String repo = hostZipRepo("svnRepos/fiveCommits.zip");
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new SubversionSCM(repo));
        p.addProperty(new ParametersDefinitionProperty(new SVNParameterDefinition("SVN")));
        
        WebClient client = j.createWebClient();
        client.getOptions().setThrowExceptionOnFailingStatusCode(false);
        HtmlPage page = client.getPage(p, "build");
        HtmlForm form = page.getFormByName("parameters");
        form.getInputByName("_.revision").setValueAttribute("2");
        j.submit(form);
        j.waitUntilNoActivity();
        
        assertEquals(1, p.getLastBuild().getNumber());
        assertEquals(Result.SUCCESS, p.getLastBuild().getResult());
        assertRevision(p.getLastBuild(), repo, 2);
    }

    @Test
    public void twoSVNResources() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        String repo1 = hostZipRepo("svnRepos/empty.zip");
        String repo2 = hostZipRepo("svnRepos/empty.zip");
        String[] repos = new String[] {repo1, repo2};
        p.setScm(new SubversionSCM(repos, new String[] {"repo1", "repo2"}));
        p.addProperty(new ParametersDefinitionProperty(new SVNParameterDefinition("SVN")));
        
        WebClient client = j.createWebClient();
        client.getOptions().setThrowExceptionOnFailingStatusCode(false);
        HtmlPage page = client.getPage(p, "build");
        HtmlForm form = page.getFormByName("parameters");
        j.submit(form);
        j.waitUntilNoActivity();
        
        assertEquals(1, p.getLastBuild().getNumber());
        assertEquals(Result.SUCCESS, p.getLastBuild().getResult());
        assertRevision(p.getLastBuild(), repo1, 0);
        assertRevision(p.getLastBuild(), repo2, 0);
    }
}
