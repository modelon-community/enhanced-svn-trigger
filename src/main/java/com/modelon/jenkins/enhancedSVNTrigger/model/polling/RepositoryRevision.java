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

import java.util.Date;

import org.tmatesoft.svn.core.SVNLogEntry;

public class RepositoryRevision implements Comparable<RepositoryRevision> {
    private final String repoBasePath;
    private final Long revision;
    private final String message;
    private final Date date;

    public RepositoryRevision(SVNLogEntry logEntry, String repoBasePath) {
        this.repoBasePath = repoBasePath;
        this.revision = logEntry.getRevision();
        this.message = logEntry.getMessage();
        this.date = logEntry.getDate();
    }

    public String getMessage() {
        return message;
    }

    public String getRepoBasePath() {
        return repoBasePath;
    }

    public Long getRevision() {
        return revision;
    }

    @Override
    public int compareTo(RepositoryRevision o) {
        int diff;
        diff = date.compareTo(o.date);
        if (diff != 0) {
            return diff;
        }
        diff = repoBasePath.compareTo(o.repoBasePath);
        if (diff != 0) {
            return diff;
        }
        diff = Long.compare(revision, o.revision);
        if (diff != 0) {
            return diff;
        }
        if (!message.equals(o.message)) {
            throw new IllegalStateException(
                    "The following two changesets have been deemed to be equal yet their messages differ:\n" + this
                            + "\n\n" + o);
        }
        return 0;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof RepositoryRevision)) {
            return false;
        }
        RepositoryRevision other = (RepositoryRevision) obj;
        return compareTo(other) == 0;
    }

    @Override
    public String toString() {
        return repoBasePath + "@" + revision + " " + date + "\n" + message;
    }

}
