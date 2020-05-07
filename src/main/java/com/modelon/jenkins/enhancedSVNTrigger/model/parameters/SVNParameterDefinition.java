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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.servlet.ServletException;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.modelon.jenkins.enhancedSVNTrigger.model.polling.RemoteLocation;

import hudson.Extension;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.scm.SubversionSCM.SvnInfo;
import hudson.util.FormValidation;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

public class SVNParameterDefinition extends ParameterDefinition {

    private static final long serialVersionUID = -4049131234328532433L;

    @DataBoundConstructor
    public SVNParameterDefinition(String name) {
        super(name);
    }

    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
        JSONObject form;
        try {
            form = req.getSubmittedForm();
        } catch (ServletException e1) {
            return null;
        }
        List<JSONObject> vals = new ArrayList<JSONObject>();
        Object o = form.get(getName());
        if (o instanceof JSONObject) {
            vals.add((JSONObject) o);
        } else if (o instanceof JSONArray) {
            JSONArray array = (JSONArray) o;
            for (Object arrayO : array) {
                vals.add((JSONObject) arrayO);
            }
            
        }
        List<SvnInfo> svnInfos = new ArrayList<SvnInfo>();
        for (JSONObject obj : vals) {
            String url = obj.getString("url");
            long rev;
            try {
                rev = obj.getLong("revision");
            } catch (JSONException e) {
                // Either it was HEAD or something non-long, can be ignored
                continue;
            }
            svnInfos.add(new SvnInfo(url, rev));
        }
        return new SVNParameterValue(getName(), svnInfos);
    }
    
    @Override
    public ParameterValue createValue(StaplerRequest req) {
        // TODO: We might wan't to support this in the future.
        return null;
    }

    @Extension
    public static class DescriptorImpl extends ParameterDescriptor {
        @Override
        public String getDisplayName() {
            return "SVN Revision Parameter";
        }

        public FormValidation doCheckRevision(@QueryParameter String value) {
            if (value.toUpperCase().equals("HEAD")) {
                return FormValidation.okWithMarkup("Latest revision will be used");
            }
            try {
                Long.parseLong(value);
                return FormValidation.ok();
            } catch (NumberFormatException e) {
                return FormValidation.error("Please enter a revision number or HEAD");
            }
            
        }

    }
    
    public Collection<String> getSVNURLS(StaplerRequest req) throws IOException, InterruptedException {
        return RemoteLocation.extractURLS(req.findAncestorObject(Job.class));
    }
    
}
