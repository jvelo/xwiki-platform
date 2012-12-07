/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.test.integration;

import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.BuildListener;
import org.apache.tools.ant.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Allow logging Ant messages to the console. This is used by the {@link org.xwiki.test.integration.XWikiTestSetup}
 * class which uses Ant tasks to start/stop XWiki.
 */
public class AntBuildListener implements BuildListener
{
    /**
     * The logger to use logs generated by Ant.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(AntBuildListener.class);

    private int logLevel;

    /**
     * @param logLevel the level at which to leg (eg {@link Project#MSG_DEBUG} will log everything and
     *        {@link Project#MSG_ERR} will only logs errors
     */
    public AntBuildListener(int logLevel)
    {
        this.logLevel = logLevel;
    }

    @Override
    public void buildStarted(BuildEvent event)
    {
        // Voluntarily do nothing
    }

    @Override
    public void buildFinished(BuildEvent event)
    {
        // Voluntarily do nothing
    }

    @Override
    public void targetStarted(BuildEvent event)
    {
        // Voluntarily do nothing
    }

    @Override
    public void targetFinished(BuildEvent event)
    {
        // Voluntarily do nothing
    }

    @Override
    public void taskStarted(BuildEvent event)
    {
        // Voluntarily do nothing
    }

    @Override
    public void taskFinished(BuildEvent event)
    {
        // Voluntarily do nothing
    }

    @Override
    public void messageLogged(BuildEvent event)
    {
        if (event.getPriority() <= this.logLevel) {
            switch (event.getPriority()) {
                case Project.MSG_ERR:
                    LOGGER.error(event.getMessage());
                    break;
                case Project.MSG_WARN:
                    LOGGER.warn(event.getMessage());
                    break;
                case Project.MSG_INFO:
                    LOGGER.info(event.getMessage());
                    break;
                default:
                    LOGGER.debug(event.getMessage());
            }
        }
    }
}
