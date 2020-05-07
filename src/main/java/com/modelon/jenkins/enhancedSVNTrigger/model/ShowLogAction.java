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

import java.io.IOException;
import java.nio.charset.Charset;

import org.apache.commons.jelly.XMLOutput;

import com.modelon.jenkins.enhancedSVNTrigger.BuildTrigger;

import hudson.Util;
import hudson.console.AnnotatedLargeText;
import hudson.model.Action;
import hudson.model.Item;

public class ShowLogAction implements Action {
    
    private final BuildTrigger trigger;
    
    public ShowLogAction(BuildTrigger trigger) {
        this.trigger = trigger;
    }
    
    @Override
    public String getIconFileName() {
        return "clipboard.png";
    }

    @Override
    public String getDisplayName() {
        return Messages.ShowLogAction_displayName();
    }

    @Override
    public String getUrlName() {
        return "ESTPollerLog";
    }

    public String getLog() throws IOException {
        return Util.loadFile(trigger.getLogFile());
    }

    public Item getItem() {
        return trigger.getJob();
    }

    public void writeLogTo(XMLOutput out) throws IOException {
        new AnnotatedLargeText<ShowLogAction>(trigger.getLogFile(), Charset.defaultCharset(), true, this).writeHtmlTo(0,
                out.asWriter());
    }
}

