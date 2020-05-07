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
package com.modelon.jenkins.enhancedSVNTrigger;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.jvnet.hudson.test.TestBuilder;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;

public class EnvVarRecordBuilder extends TestBuilder {

    private final Map<String, String> lastEnvVars = new HashMap<String, String>();
    
    public void reset() {
        lastEnvVars.clear();
    }

    public void assertEnvVars(String ... expectedPairs) {
        if (expectedPairs.length % 2 != 0) {
            throw new IllegalArgumentException("Expecting pairs of strings consisting of environment variable name and expected value. Got an uneven number of strings");
        }
        for (int i = 0; i < expectedPairs.length; i += 2) {
            assertEquals("Incorrect value for variable '" + expectedPairs[i] + "'", expectedPairs[i + 1], lastEnvVars.get(expectedPairs[i]));
        }
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {
        reset();
        lastEnvVars.putAll(build.getEnvironment(listener));
        return true;
    }

}
