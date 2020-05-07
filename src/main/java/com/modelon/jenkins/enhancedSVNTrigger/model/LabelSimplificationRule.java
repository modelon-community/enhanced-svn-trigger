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

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;

public class LabelSimplificationRule extends AbstractDescribableImpl<LabelSimplificationRule> {

    public final String pattern;
    public final String replacement;
    transient private Pattern regex;

    @DataBoundConstructor
    public LabelSimplificationRule(String pattern, String replacement) {
        this.pattern = pattern;
        this.replacement = replacement;
    }
    
    public String getPattern() {
        return pattern;
    }
    
    public String getReplacement() {
        return replacement;
    }
    
    public String simplify(String str) {
        if (regex == null) {
            try {
                regex = Pattern.compile(pattern);
            } catch (PatternSyntaxException e) {
                // So that we don't have to fail computing the pattern each time
                regex = Pattern.compile("a^");
            }
        }
        Matcher match = regex.matcher(str);
        if (!match.matches()) {
            return str;
        }
        StringBuffer sb = new StringBuffer();
        try {
            match.appendReplacement(sb, replacement);
        } catch (IllegalArgumentException e) {
            return str;
        } catch (IndexOutOfBoundsException e) {
            return str;
        }
        return sb.toString();
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<LabelSimplificationRule> {

        public FormValidation doCheckPattern(@QueryParameter String value) {
            value = Util.fixEmptyAndTrim(value);
            if (value == null) {
                return FormValidation.error("You must provide a pattern");
            }
            try {
                Pattern.compile(value);
            } catch (PatternSyntaxException e) {
                return FormValidation.error("Invalid regular expression");
            }
            return FormValidation.ok();
        }

   }

}
