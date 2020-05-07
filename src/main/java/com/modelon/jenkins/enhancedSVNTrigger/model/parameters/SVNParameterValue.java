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
package com.modelon.jenkins.enhancedSVNTrigger.model.parameters;

import java.util.List;

import hudson.EnvVars;
import hudson.model.ParameterValue;
import hudson.model.Run;
import hudson.scm.RevisionParameterAction;
import hudson.scm.SubversionSCM.SvnInfo;

public class SVNParameterValue extends ParameterValue {

    private static final long serialVersionUID = -3732406110897611539L;

    private final List<SvnInfo> revisions;

    protected SVNParameterValue(String name, List<SvnInfo> revisions) {
        super(name);
        this.revisions = revisions;
    }

    @Override
    public void buildEnvironment(Run<?, ?> build, EnvVars env) {
        build.addAction(new RevisionParameterAction(revisions));
    }
    
    public List<SvnInfo> getRevisions() {
        return revisions;
    }
    

}
