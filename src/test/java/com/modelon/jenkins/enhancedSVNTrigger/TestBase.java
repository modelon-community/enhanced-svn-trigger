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

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.modelon.jenkins.enhancedSVNTrigger.model.TriggerCause;
import hudson.scm.SubversionSCM;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.HudsonHomeLoader.CopyExisting;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;

import antlr.ANTLRException;
import hudson.FilePath;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Run;
import hudson.scm.SVNRevisionState;

public abstract class TestBase {

    @Rule
    public ExtendedJenkinsRule j = new ExtendedJenkinsRule();

    public static void assertRevisions(Run<?, ?> run, String[] repos, long revisions[]) {
        SVNRevisionState action = run.getAction(SVNRevisionState.class);
        for (int i = 0; i < repos.length; i++) {
            assertEquals("Revisions for " + repos[i] + "differs!", revisions[i], action.getRevision(repos[i]));
        }
    }

    public static void assertRevision(Run<?, ?> run, String repo, long revision) {
        assertRevisions(run, new String[] {repo}, new long[] {revision});
    }

    public void assertRevisionInfo(Run<?, ?> run, String[] expectedURLs, long[] expectedRevisions) {
        TriggerCause cause = run.getCause(TriggerCause.class);
        Map<String, Long> actualMap = new HashMap<String, Long>();
        for (SubversionSCM.SvnInfo revision : cause.getRevisionInfo()) {
            assertNull("There are multiple entries for url " + revision.url,
                    actualMap.put(revision.url, revision.revision));
        }
        for (int i = 0; i < expectedURLs.length; i++) {
            Long actualRev = actualMap.remove(expectedURLs[i]);
            assertNotNull("No entries for url " + expectedURLs[i], actualRev);
            assertEquals("Revisions for " + expectedURLs[i] + "differs!", expectedRevisions[i], (long)actualRev);
        }
        assertEquals("There are leftover urls in actual", 0, actualMap.size());
    }

    public String hostZipRepo(String designatedRepo) throws Exception {
        // creating SVN repos on the fly
        String repo = createEmptyRepo(designatedRepo);
        if (designatedRepo.equals("svnRepos/empty.zip")) {
            return repo;
        }
        FilePath co = checkOut(repo);
        if (designatedRepo.equals("svnRepos/fiveCommits.zip")) {
            // repo contains a single file file.txt with a single digit in it that changes once for each revision, no 
            // branches or tags
            editAndCommitWithMsg(co, "#1", "1", new String[] {"file.txt"});
            editAndCommitWithMsg(co, "#2", "2", new String[] {"file.txt"});
            editAndCommitWithMsg(co, "#3", "3", new String[] {"file.txt"});
            editAndCommitWithMsg(co, "#4", "4", new String[] {"file.txt"});
            editAndCommitWithMsg(co, "#5", "5", new String[] {"file.txt"});
            return repo;
        }
        if (designatedRepo.equals("svnRepos/trunkBranchesTags.zip")) {
            // empty repo but structured with trunk, branches and tags
            editAndCommitWithMsg(co, "", null, new String[] {"trunk/", "branches/", "tags/"});
            return repo;
        } else {
            throw new IllegalArgumentException(String.format("Failed to construct repo, unrecognized repo: {}", designatedRepo));
        }

    }

    public String createEmptyRepo(String designatedRepo) {
        try {
            File temp = f.newFolder().getCanonicalFile();
            boolean enableRevisionProperties = true;
            boolean force = true;
            SVNURL repoUrl = FSRepositoryFactory.createLocalRepository(temp, enableRevisionProperties, force);
            return repoUrl.toString();
        } catch ( IOException e ) {
            throw new RuntimeException(e);
        } catch ( SVNException e ) {
            throw new RuntimeException(e);
        }
    }

    public BuildTrigger addTrigger(AbstractProject<?, ?> job) throws ANTLRException, IOException {
        BuildTrigger t = createTrigger(job);
        job.addTrigger(t);
        return t;
    }

    public BuildTrigger addTrigger(WorkflowJob job) throws ANTLRException, IOException {
        BuildTrigger t = createTrigger(job);
        job.addTrigger(t);
        return t;
    }
    
    public BuildTrigger createTrigger(Item job) throws ANTLRException {
        BuildTrigger.getMyDescriptor().setDefaultSchedule("");
        BuildTrigger t = new BuildTrigger();
        t.start(job, true);
        return t;
    }

    @Rule public TemporaryFolder f = new TemporaryFolder();

    public FilePath checkOut(String repoPath) throws IOException, SVNException {
        File workingCopy = f.newFolder();
        SVNClientManager manager = SVNClientManager.newInstance();
        SVNUpdateClient uc = manager.getUpdateClient();
        uc.doCheckout(SVNURL.parseURIEncoded(repoPath), workingCopy, SVNRevision.HEAD, SVNRevision.HEAD, SVNDepth.INFINITY, false);
        return new FilePath(workingCopy);
    }

    public static void editAndCommit(FreeStyleProject p, String contents, String... filesInWorkspace)
            throws IOException, InterruptedException, SVNException {
        editAndCommit(p.getSomeWorkspace(), contents, filesInWorkspace);
    }

    public static void editAndCommit(FilePath repoCO, String contents, String... filesInWorkspace) throws IOException, InterruptedException, SVNException {
        editAndCommitWithMsg(repoCO, "Testing commit with contents: " + contents, contents, filesInWorkspace);
    }

    /**
     * Makes a new commit on the working copy repoCO. Creates the files in filesInWorkspace if they do not exist. If the 
     * file ends with a '/', e.g. "trunk/", then it is a directory. Otherwise it is a file.
     */
    public static void editAndCommitWithMsg(FilePath repoCO, String msg, String contents, String[] filesInWorkspace)
            throws IOException, InterruptedException, SVNException {
        Collection<File> files = new ArrayList<File>();
        for (String relativeFile : filesInWorkspace) {

            // determines if the file is a directory using relativeFile instead of fp.isDirectory() because if the file 
            // does not exist, then isDirectory() returns false. isDirectory() requires that the file exists AND is a 
            // directory.
            boolean isDirectory = relativeFile.endsWith("/");
            FilePath fp = repoCO.child(relativeFile);
            files.add(new File(fp.getRemote()));
            if (!fp.exists()) {
                if (isDirectory) {
                    fp.mkdirs();
                }
                fp.touch(System.currentTimeMillis());
            }
            if (!isDirectory) {
                fp.write(contents, "UTF-8");
            }
        }
        SVNClientManager manager = SVNClientManager.newInstance();
        File[] fileVector = files.toArray(new File[files.size()]);
        manager.getWCClient().doAdd(fileVector, true, false, true, SVNDepth.INFINITY, true, false, false);
        manager.getCommitClient().doCommit(fileVector, false, msg, null, null,
                false, false, SVNDepth.INFINITY);
    }
    
}
