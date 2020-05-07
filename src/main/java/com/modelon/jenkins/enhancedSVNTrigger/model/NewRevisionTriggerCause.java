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

import java.util.List;

import com.modelon.jenkins.enhancedSVNTrigger.BuildTrigger;
import com.modelon.jenkins.enhancedSVNTrigger.model.polling.RepositoryRevision;

import hudson.scm.SubversionSCM.SvnInfo;

public class NewRevisionTriggerCause extends TriggerCause {

    public final String location;
    public final long revision;
    transient private String simplifiedLocation;
    private String commitMessage;

    public NewRevisionTriggerCause(List<SvnInfo> revisionInfo, RepositoryRevision change) {
        this(revisionInfo, change.getRepoBasePath(), change.getRevision(), change.getMessage());
    }

    public NewRevisionTriggerCause(List<SvnInfo> revisionInfo, String location, long revision, String commitMessage) {
        super(BuildTrigger.extractEnvVarsFromMsg(commitMessage), revisionInfo);
        this.location = location;
        this.revision = revision;
        this.commitMessage = commitMessage;
    }

    @Override
    public String getShortDescription() {
        return String.format("Started due to change: %s@%d", location, revision);
    }

    @Override
    public String triggerCauseLabel() {
        if (simplifiedLocation == null) {
            simplifiedLocation = BuildTrigger.getMyDescriptor().simplifyLabel(location);
        }
        return simplifiedLocation + "@" + revision;
    }

    @Override
    public boolean shouldTriggerBuild() {
        return !commitMessage.contains("[ci skip]");
    }

}
