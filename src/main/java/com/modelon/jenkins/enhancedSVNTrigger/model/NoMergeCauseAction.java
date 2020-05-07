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

import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Queue.QueueAction;

/**
 * A custom cause action which prevents that builds in the queue are merged.
 * This is needed by the trigger since we don't want to merge builds.
 */
public class NoMergeCauseAction extends CauseAction implements QueueAction {

    public NoMergeCauseAction(Cause ... causes) {
        super(causes);
    }

    @Override
    public boolean shouldSchedule(List<Action> actions) {
        // Always trigger, don't merge.
        return true;
    }

}
