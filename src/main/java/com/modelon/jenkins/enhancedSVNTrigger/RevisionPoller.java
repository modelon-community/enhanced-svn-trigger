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
import java.io.PrintStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import hudson.model.*;
import jenkins.model.Jenkins;
import org.tmatesoft.svn.core.SVNException;

import com.modelon.jenkins.enhancedSVNTrigger.model.JobResourceState;
import com.modelon.jenkins.enhancedSVNTrigger.model.NewRepoTriggerCause;
import com.modelon.jenkins.enhancedSVNTrigger.model.NewRevisionTriggerCause;
import com.modelon.jenkins.enhancedSVNTrigger.model.NoMergeCauseAction;
import com.modelon.jenkins.enhancedSVNTrigger.model.ResumeTriggerCause;
import com.modelon.jenkins.enhancedSVNTrigger.model.TriggerCause;
import com.modelon.jenkins.enhancedSVNTrigger.model.polling.LocalLocation;
import com.modelon.jenkins.enhancedSVNTrigger.model.polling.RemoteLocation;
import com.modelon.jenkins.enhancedSVNTrigger.model.polling.RemoteLocation.LogCallback;
import com.modelon.jenkins.enhancedSVNTrigger.model.polling.RepositoryRevision;

import hudson.EnvVars;
import hudson.console.ModelHyperlinkNote;
import hudson.scm.SubversionSCM;
import hudson.scm.SubversionSCM.SvnInfo;
import jenkins.triggers.SCMTriggerItem;

public final class RevisionPoller {
    
    private RevisionPoller() {
        // Not meant to be instantiated.
    }

    public static SubversionSCM dummySCM() {
        return new SubversionSCM(new String[]{},new String[]{});
    }
    
    public static Map<Job<?, ?>, JobResourceState> loadStates(Iterable<? extends Job<?, ?>> jobs, TaskListener listener) {
        Map<Job<?, ?>, JobResourceState> res = new LinkedHashMap<Job<?, ?>, JobResourceState>();
        for (Job<?, ?> job : jobs) {
            try {
                JobResourceState state = JobResourceState.load(job);
                res.put(job, state);
            } catch (IOException e) {
                listener.error("Failed to retrieve state for " + ModelHyperlinkNote.encodeTo(job) + ", this job will be skipped!");
                e.printStackTrace(listener.getLogger());
            }
        }
        return res;
    }

    public static Result pollForChanges(Job<?, ?> context, Collection<? extends Job<?, ?>> unfilteredJobs, TaskListener listener) {
        Date time = new Date();
        Result result = Result.SUCCESS;
        
        
        Collection<Job<?, ?>> jobs = new ArrayList<Job<?, ?>>();
        for (Job<?, ?> job : unfilteredJobs) {
            if (job.isBuildable()) {
                jobs.add(job);
            }
        }
        
        final Map<Job<?, ?>, JobResourceState> lastRevisions = loadStates(jobs, listener);
        if (lastRevisions.size() != jobs.size()) {
            result = result.combine(Result.UNSTABLE);
        }
        
        jobs = lastRevisions.keySet();
        EnvVars env;
        try {
            env = BuildTrigger.getGlobalEnvVars(listener);
        } catch (IOException e) {
            listener.error("Failed to retrieve environment variables!");
            e.printStackTrace(listener.getLogger());
            return Result.FAILURE;
        } catch (InterruptedException e) {
            listener.error("Failed to retrieve environment variables!");
            e.printStackTrace(listener.getLogger());
            return Result.FAILURE;
        }
        
        Collection<RemoteLocation> remotes = RemoteLocation.extract(jobs, env);
        
        final Map<Job<?, ?>, SortedSet<RepositoryRevision>> newRevisionsMap = new HashMap<Job<?,?>, SortedSet<RepositoryRevision>>();
        final Map<Job<?, ?>, List<NewLocation>> newLocationsMap = new HashMap<Job<?, ?>, List<NewLocation>>();
        final Map<Job<?, ?>, Map<String, Collection<String>>> repoBasePathToLocationMapMap = new HashMap<Job<?, ?>, Map<String, Collection<String>>>();
        for (Job<?, ?> job : jobs) {
            newRevisionsMap.put(job, new TreeSet<RepositoryRevision>());
            newLocationsMap.put(job, new ArrayList<NewLocation>());
            repoBasePathToLocationMapMap.put(job, new HashMap<String, Collection<String>>());
        }
        
        for (RemoteLocation remote : remotes) {
            try {
                result = result.combine(remote.log(context, dummySCM(), listener.getLogger(), time, new LogCallback() {
                    
                    @Override
                    public void newRevision(RepositoryRevision rev, LocalLocation local, String remoteURL) {
                        newRevisionsMap.get(local.getOwner()).add(rev);
                    }

                    @Override
                    public void registerBasePath(Job<?, ?> job, String repoBasePath, String location) {
                        Map<String, Collection<String>> locationsMap = repoBasePathToLocationMapMap.get(job);
                        Collection<String> locations = locationsMap.get(repoBasePath);
                        if (locations == null) {
                            locations = new LinkedHashSet<String>();
                            locationsMap.put(repoBasePath, locations);
                        }
                        locations.add(location);
                    }

                    @Override
                    public void newLocation(LocalLocation local, String url, String repoBasePath, long revision) {
                        newLocationsMap.get(local.getOwner()).add(new NewLocation(url, repoBasePath, revision));
                    }
                    
                    @Override
                    public Long lastRev(String repoBasePath, Job<?, ?> context) {
                        return lastRevisions.get(context).repoRevisions.get(repoBasePath);
                    }
                    
                }));
            } catch (SVNException e) {
                listener.getLogger().format("Error while polling %s\n", remote.getModuleLocation().remote);
                e.printStackTrace(listener.getLogger());
                result = result.combine(Result.UNSTABLE);
                continue;
            }
        }
        for (Job<?, ?> job : jobs) {
            String jobLink = ModelHyperlinkNote.encodeTo(job);
            listener.getLogger().format("Triggering builds for %s...\n", jobLink);
            
            JobResourceState lastState = lastRevisions.get(job);
            SortedSet<RepositoryRevision> newRevisions = newRevisionsMap.get(job);
            List<NewLocation> newLocations = newLocationsMap.get(job);
            List<String> newURLs = new ArrayList<String>();
            
            // Gather repo base paths and last triggered revision
            Map<String, Long> repoBasePathToRevisionMap = new HashMap<String, Long>(lastState.repoRevisions);
            
            
            for (NewLocation newLocation : newLocations) {
                // There might be several locations which check out from the
                // same repo. In that case we wan't to take max revision
                Long rev = repoBasePathToRevisionMap.get(newLocation.repoBasePath);
                if (rev == null) {
                    rev = newLocation.revision;
                } else {
                    rev = Math.max(rev, newLocation.revision);
                }
                newURLs.add(newLocation.url);
                repoBasePathToRevisionMap.put(newLocation.repoBasePath, rev);
            }
            
            Map<String, Collection<String>> repoBasePathToLocationMap = repoBasePathToLocationMapMap.get(job);
            
            if (!newLocations.isEmpty()) {
                // We have one or more new repositories, let's trigger this
                // before we trigger the others. This way we get an isolated
                // build with only the new repository.
                List<SvnInfo> revisionInfo = buildSvnInfosForLocations(repoBasePathToLocationMap, repoBasePathToRevisionMap);
                triggerBuild(job, listener, new NewRepoTriggerCause(revisionInfo, newURLs));
            } else if (newRevisions.isEmpty()) {
                listener.getLogger().println("  ... nothing to trigger!");
            } else if (lastState.disabledSinceLastRun) {
                listener.getLogger().println("  ... this job was disabled previously, but has since been enabled. Time for a catch up build!");
                for (RepositoryRevision change : newRevisions) {
                    Long previous = repoBasePathToRevisionMap.put(change.getRepoBasePath(), change.getRevision());
                    if (previous == null) {
                        throw new IllegalArgumentException(
                                change.getRepoBasePath() + " isn't present in the baseRepoRevisions map!");
                    }
                }
                List<SvnInfo> revisionInfo = buildSvnInfosForLocations(repoBasePathToLocationMap, repoBasePathToRevisionMap);
                triggerBuild(job, listener, new ResumeTriggerCause(revisionInfo));
            } else {
                for (RepositoryRevision change : newRevisions) {
                    Long previous = repoBasePathToRevisionMap.put(change.getRepoBasePath(), change.getRevision());
                    if (previous == null) {
                        throw new IllegalArgumentException(
                                change.getRepoBasePath() + " isn't present in the baseRepoRevisions map!");
                    }
                    List<SvnInfo> revisionInfo = buildSvnInfosForLocations(repoBasePathToLocationMap, repoBasePathToRevisionMap);
                    triggerBuild(job, listener, new NewRevisionTriggerCause(revisionInfo, change));
                }
            }
            try {
                saveState(job, repoBasePathToRevisionMap);
            } catch (IOException e) {
                listener.getLogger().format("Error while saving state for %s", jobLink);
                e.printStackTrace(listener.getLogger());
                result = result.combine(Result.UNSTABLE);
                continue;
            }
        }
        return result;
    }
    
    private static void saveState(Job<?, ?> job, Map<String, Long> repoBasePathToRevisionMap) throws IOException {
        JobResourceState state = new JobResourceState(repoBasePathToRevisionMap);
        state.save(job);
    }

    private static void triggerBuild(Job<?, ?> job, TaskListener listener, TriggerCause cause) {

        // don't trigger builds for new revisions that have the tag [ci skip] in the commit message
        if(!cause.shouldTriggerBuild()) {
            SvnInfo info = cause.getRevisionInfo().get(0);
            listener.getLogger().println(String.format("Skipping %s@%d because this changeset contains the tag [ci skip] in the commit message.", info.url, info.revision));
            return;
        }
            
        
        // When triggering we should add a list of url : revision pairs where
        // url corresponds to ModuleLocation.getURL(). Note that several
        // ModuleLocations can correspond to the same repository root and that
        // they should have the same revision.
        List<SvnInfo> locationRevisions = cause.getRevisionInfo();
        listener.getLogger().println("Triggering with label=" + cause.triggerCauseLabel());
        for (SvnInfo info : locationRevisions) {    
            listener.getLogger().println("  " + info.url + "@" + info.revision);
        }

        Cause upStreamCause = parseUpstreamBuildURL(cause.getExtraEnvVars().get("UPSTREAM_BUILD_URL"), listener);

        NoMergeCauseAction causeAction;
        if (upStreamCause == null) {
            causeAction = new NoMergeCauseAction(cause);
        } else {
            causeAction = new NoMergeCauseAction(cause, upStreamCause);
        }
        getSCMTriggerItem(job).scheduleBuild2(0, causeAction);
    }

    private static final Pattern UPSTREAM_BUILD_URL_PATTERN = Pattern.compile("^/?((job/[^/]+/)+)([1-9][0-9]*)/?$");

    /**
     * Parses jenkins build URLs and matches it to an actual build (and creates
     * a cause). Build URLs are on the following format:
     * https://a.b.c/internal/job/someJob/123/
     * and
     * http://localhost:8888/job/test/job/b/job/s/3/
     *
     * @param url The url
     * @param listener for feedback
     * @return UpStreamCause on success, otherwise null.
     */
    private static Cause parseUpstreamBuildURL(String url, TaskListener listener) {
        if (url == null) {
            return null;
        }
        PrintStream log = listener.getLogger();
        String rootUrl = Jenkins.getInstance().getRootUrl();
        if (!url.startsWith(rootUrl)) {
            log.println("Unable to parse upstream build url: '" + url + "' doesn't start with '" + rootUrl + "'!");
            return null;
        }
        // Now we have /job/aa/job/bb/123/
        url = url.substring(rootUrl.length());
        Matcher m = UPSTREAM_BUILD_URL_PATTERN.matcher(url);
        if (!m.find()) {
            log.println("Unable to parse upstream build url: '" + url + "' doesn't match the pattern '^/?((job/[^/]+/)+)([1-9][0-9]*)/?$'!");
            return null;
        }
        String[] jobParts = m.group(1).split("/?job/");
        String buildNumber = m.group(3);
        if (jobParts.length == 0) {
            log.println("Unable to parse upstream build url: '" + url + "' expecting at least two parts in jobParts!");
            return null;
        }
        if (!jobParts[0].isEmpty()) {
            log.println("Unable to parse upstream build url: '" + url + "' expecting part zero of jobParts to be empty, got '" + jobParts[0] + "'!");
            return null;
        }
        StringBuilder jobNameBuilder = new StringBuilder();
        for (int i = 1; i < jobParts.length; i++) {
            jobNameBuilder.append("/");
            jobNameBuilder.append(jobParts[i]);
        }
        Job job = Jenkins.getInstance().getItemByFullName(jobNameBuilder.toString(), Job.class);
        if (job == null) {
            log.println("Unable to parse upstream build url: can't find job with name '" + jobNameBuilder.toString() + "'!");
            return null;
        }
        Run run = job.getBuild(buildNumber);
        if (run == null) {
            log.println("Unable to parse upstream build url: can't find run '" + buildNumber + "' for job '" + jobNameBuilder.toString() + "'!");
            return null;
        }
        return new Cause.UpstreamCause(run);
    }

    private static List<SvnInfo> buildSvnInfosForLocations(Map<String, Collection<String>> locations,
            Map<String, Long> baseRevisions) {
        List<SvnInfo> locationRevisions = new ArrayList<SvnInfo>();
        for (Map.Entry<String, Collection<String>> entry : locations.entrySet()) {
            for (String location : entry.getValue()) {
                Long revision = baseRevisions.get(entry.getKey());
                if (revision == null) {
                    throw new IllegalArgumentException(
                            entry.getKey() + " isn't present in the baseRepoRevisions map!");
                }
                locationRevisions.add(new SvnInfo(location, revision));
            }
        }
        return locationRevisions;
    }

    private static SCMTriggerItem getSCMTriggerItem(Job<?, ?> job) {
        return SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(job);
    }
    
    private static class NewLocation {
        
        private final String url;
        private final String repoBasePath;
        private final long revision;

        public NewLocation(String url, String repoBasePath, long revision) {
            this.url = url;
            this.repoBasePath = repoBasePath;
            this.revision = revision;
        }
    }

}
