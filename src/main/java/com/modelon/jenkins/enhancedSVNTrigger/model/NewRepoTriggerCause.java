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

import java.util.Collections;
import java.util.List;

import hudson.scm.SubversionSCM.SvnInfo;

public class NewRepoTriggerCause extends TriggerCause {

    public final List<String> newLocations;

    public NewRepoTriggerCause(List<SvnInfo> revisionInfo, List<String> newLocations) {
        super(Collections.<String, String>emptyMap(), revisionInfo);
        this.newLocations = Collections.unmodifiableList(newLocations);
    }

    @Override
    public String getShortDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("Started due to new ");
        sb.append(newLocations.size() > 1 ? "repositories" : "repository");
        sb.append(": ");
        int count = 0;
        for (String location : newLocations) {
            if (newLocations.size() > 1 && count + 1 == newLocations.size()) {
                sb.append(" and ");
            } else if (count > 0) {
                sb.append(", ");
            }
            sb.append(location);
            count++;
        }
        return sb.toString();
    }

    @Override
    public String triggerCauseLabel() {
        return "NEW_REPO";
    }

    @Override
    public boolean shouldTriggerBuild() {
        return true;
    }

}
