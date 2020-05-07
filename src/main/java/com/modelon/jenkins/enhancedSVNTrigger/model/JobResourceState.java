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
package com.modelon.jenkins.enhancedSVNTrigger.model;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.modelon.jenkins.enhancedSVNTrigger.PollerJob;

import hudson.XmlFile;
import hudson.model.Items;
import hudson.model.Job;

public class JobResourceState implements Serializable {
    public static final JobResourceState NO_RUN = new JobResourceState(Collections.<String, Long>emptyMap());
    private static final long serialVersionUID = 8261133654788517429L;
    public final Map<String, Long> repoRevisions;
    public final boolean disabledSinceLastRun;

    public JobResourceState(Map<String, Long> repoRevisions) {
        this.repoRevisions = Collections.unmodifiableMap(repoRevisions);
        disabledSinceLastRun = false;
    }
    
    private JobResourceState(JobResourceState last) {
        repoRevisions = Collections.unmodifiableMap(last.repoRevisions);
        disabledSinceLastRun = true;
    }
    
    public JobResourceState markAsDisabled() {
        return new JobResourceState(this);
    }
    
    public void save(Job<?, ?> job) throws IOException {
        getStateFile(job).write(this);
    }
    
    public void quietSave(Job<?, ?> job) {
        try {
            save(job);
        } catch (IOException e) {
            Logger.getLogger(PollerJob.class.getName()).log(Level.WARNING, "Unable to save state for job " + job.getFullName(), e);
        }
    }
    
    public static JobResourceState load(Job<?, ?> job) throws IOException {
        XmlFile stateFile = getStateFile(job);
        if (stateFile.exists()) {
            return (JobResourceState) stateFile.read();
        } else {
            return JobResourceState.NO_RUN;
        }
    }
    
    public static JobResourceState quietLoad(Job<?, ?> job) {
        try {
            return load(job);
        } catch (IOException e) {
            Logger.getLogger(job.getClass().getName()).log(Level.WARNING, "Unable to load state for job " + job.getFullName(), e);
            return null;
        }
    }
    
    private static XmlFile getStateFile(Job<?, ?> job) {
        return new XmlFile(Items.XSTREAM2, new File(job.getRootDir(), "enhancedSVNTrigger.xml"));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Long> rev : repoRevisions.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append(rev.getKey());
            sb.append('@');
            sb.append(rev.getValue());
        }
        sb.append(" : ");
        sb.append(disabledSinceLastRun);
        return sb.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (disabledSinceLastRun ? 1231 : 1237);
        result = prime * result + ((repoRevisions == null) ? 0 : repoRevisions.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof JobResourceState)) {
            return false;
        }
        JobResourceState other = (JobResourceState) obj;
        if (disabledSinceLastRun != other.disabledSinceLastRun) {
            return false;
        }
        if (repoRevisions == null) {
            if (other.repoRevisions != null) {
                return false;
            }
        } else if (!repoRevisions.equals(other.repoRevisions)) {
            return false;
        }
        return true;
    }
}
