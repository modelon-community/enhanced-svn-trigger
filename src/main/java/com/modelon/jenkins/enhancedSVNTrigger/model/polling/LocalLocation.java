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

import hudson.model.Job;
import hudson.scm.SVNLogFilter;

public class LocalLocation {
    private final SVNLogFilter filter;
    private final Job<?, ?> owner;


    public LocalLocation(SVNLogFilter filter/*, Long lastKnownRevision*/, Job<?, ?> owner) {
        this.filter = filter;
        this.owner = owner;
    }


    public Job<?, ?> getOwner() {
        return owner;
    }

    public SVNLogFilter getFilter() {
        return filter;
    }

}
