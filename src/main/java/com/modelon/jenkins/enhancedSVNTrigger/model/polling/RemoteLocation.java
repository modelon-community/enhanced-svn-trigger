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
package com.modelon.jenkins.enhancedSVNTrigger.model.polling;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.io.SVNRepository;

import com.modelon.jenkins.enhancedSVNTrigger.BuildTrigger;

import hudson.EnvVars;
import hudson.model.Job;
import hudson.model.Result;
import hudson.scm.SCM;
import hudson.scm.SubversionSCM;
import hudson.scm.SubversionSCM.ModuleLocation;
import jenkins.triggers.SCMTriggerItem;

public class RemoteLocation {
    private final ModuleLocation ml;
    private final Set<LocalLocation> localLocations = new LinkedHashSet<LocalLocation>();
    
    private RemoteLocation(ModuleLocation ml) {
        this.ml = ml;
    }

    public String getUrl() {
        return getModuleLocation().getURL();
    }
    
    public ModuleLocation getModuleLocation() {
        return ml;
    }

    public void addLocalLocation(LocalLocation local) {
        this.localLocations.add(local);
    }
    
    public Result log(Job<?, ?> context, SubversionSCM scm, PrintStream log, Date endTime, final LogCallback callback)
            throws SVNException {
        log.format("Checking for updates to %s, %d local checkouts...\n", ml.remote, localLocations.size());
        SVNRepository repo = ml.openRepository(context, scm, true);
        final String repoBasePath = repo.getRepositoryRoot(true).toString();

        long maxRev = repo.getDatedRevision(endTime);

        final Map<LocalLocation, Long> lastRevs = new HashMap<LocalLocation, Long>();
        long minRev = Long.MAX_VALUE;
        for (LocalLocation local : localLocations) {
            callback.registerBasePath(local.getOwner(), repoBasePath, ml.remote);
            Long last = callback.lastRev(repoBasePath, local.getOwner());
            if (last == null) {
                callback.newLocation(local, ml.remote, repoBasePath, maxRev);
            } else {
                lastRevs.put(local, last);
                minRev = Math.min(minRev, last);
            }
        }
        if (minRev == Long.MAX_VALUE) {
            log.format("  ... all local checkouts are new!\n");
            return Result.SUCCESS;
        } else if (minRev >= maxRev) {
            log.format("  ... no new revisions, latest revision is %d, oldest, polled revision is %d\n", maxRev, minRev);
            return Result.SUCCESS;
        }
        log.format("  ... latest revision is %d, oldest, polled revision is %d\n", maxRev, minRev);
        
        final String repoSubPath;
        if (repoBasePath.isEmpty()) {
            log.println("Error, basepath for " + ml.remote + " is empty!");
            return Result.FAILURE;
        } else if (!ml.remote.startsWith(repoBasePath)) {
            log.println("Error, path " + ml.remote + " does not start with " + repoBasePath + "!");
            return Result.FAILURE;
        } else {
            repoSubPath = ml.remote.substring(repoBasePath.length());
        }

        repo.log(new String[] {repoSubPath}, minRev, maxRev, true, true, new ISVNLogEntryHandler() {
            
            @Override
            public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
                RepositoryRevision rev = null;
                for (LocalLocation local : localLocations) {
                    Long lastRev = lastRevs.get(local);
                    if (lastRev != null && lastRev < logEntry.getRevision()
                            && local.getFilter().isIncluded(logEntry)) {
                        if (rev == null) {
                            rev = new RepositoryRevision(logEntry, repoBasePath);
                        }
                        callback.newRevision(rev, local, ml.remote);
                    }
                }
            }
        });
        return Result.SUCCESS;
    }

    public static int compareKey(ModuleLocation ml) {
        return ml.remote.hashCode() ^ (ml.credentialsId == null ? 0 : ml.credentialsId.hashCode());
    }

    public static Collection<String> extractURLS(Job<?, ?> job) throws IOException, InterruptedException {
        EnvVars env = BuildTrigger.getGlobalEnvVars(null);
        Collection<String> res = new LinkedHashSet<String>();
        for (RemoteLocation location : extract(job, env)) {
            res.add(location.getUrl());
        }
        return res;
    }
    
    public static Collection<RemoteLocation> extract(Job<?, ?> job, EnvVars env) {
        return extract(Collections.singletonList(job), env);
    }
    public static Collection<RemoteLocation> extract(Iterable<? extends Job<?, ?>> jobs, EnvVars env) {
        Map<Integer, RemoteLocation> remoteMap = new HashMap<Integer, RemoteLocation>();
        Collection<RemoteLocation> res = new ArrayList<RemoteLocation>();
        for (Job<?, ?> job : jobs) {
            for (SCM scm : SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(job).getSCMs()) {
                if (!(scm instanceof SubversionSCM)) {
                    continue;
                }
                SubversionSCM svnSCM = (SubversionSCM) scm;
                for (ModuleLocation ml : svnSCM.getLocations(env, null)) {
                    if (ml.getRevision(null) != null) {
                        // Pegged revision, ignore.
                        /* For FreeStyle jobs it would be fine to check if pegged
                         is newer than last triggered, however this does not
                         work for job types where the SCM is retrieved from the
                         last build, there we would get double triggering.
                         Additionally, for such jobs, there is no way of
                         telling if the pegged url has been bumped or not */
                        continue;
                    }
                    RemoteLocation remote = remoteMap.get(compareKey(ml));
                    if (remote == null) {
                        remote = new RemoteLocation(ml);
                        remoteMap.put(compareKey(ml), remote);
                        res.add(remote);
                    }
                    remote.addLocalLocation(new LocalLocation(svnSCM.createSVNLogFilter(), job));
                }
            }
        }
        return res;
    }
    
    public static interface LogCallback {
        public Long lastRev(String repoBasePath, Job<?, ?> context);
        public void newLocation(LocalLocation local, String url, String repoBasePath, long revision);
        public void newRevision(RepositoryRevision rev, LocalLocation local, String remoteURL);
        public void registerBasePath(Job<?, ?> job, String repoBasePath, String location);
    }
    
}
