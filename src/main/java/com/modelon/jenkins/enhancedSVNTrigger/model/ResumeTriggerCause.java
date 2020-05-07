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

public class ResumeTriggerCause extends TriggerCause {

    public ResumeTriggerCause(List<SvnInfo> revisionInfo) {
        super(Collections.<String, String>emptyMap(), revisionInfo);
    }

    @Override
    public String triggerCauseLabel() {
        return "RESUME";
    }

    @Override
    public String getShortDescription() {
        return "Job has been disabled while commits were made, this is the catch up build";
    }

    @Override
    public boolean shouldTriggerBuild() {
        return true;
    }

}
