/*
 * ################################################################
 *
 * ProActive Parallel Suite(TM): The Java(TM) library for
 *    Parallel, Distributed, Multi-Core Computing for
 *    Enterprise Grids & Clouds
 *
 * Copyright (C) 1997-2011 INRIA/University of
 *                 Nice-Sophia Antipolis/ActiveEon
 * Contact: proactive@ow2.org or contact@activeeon.com
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; version 3 of
 * the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 * If needed, contact us to obtain a release under GPL Version 2 or 3
 * or a different license than the AGPL.
 *
 *  Initial developer(s):               The ActiveEon Team
 *                        http://www.activeeon.com/
 *  Contributor(s):
 *
 * ################################################################
 * $$ACTIVEEON_INITIAL_DEV$$
 */
package org.ow2.proactive_grid_cloud_portal.common;

import java.util.List;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import org.jboss.resteasy.annotations.GZIP;
import org.ow2.proactive_grid_cloud_portal.scheduler.dto.TaskStateData;
import org.ow2.proactive_grid_cloud_portal.scheduler.exception.NotConnectedRestException;
import org.ow2.proactive_grid_cloud_portal.scheduler.exception.PermissionRestException;
import org.ow2.proactive_grid_cloud_portal.scheduler.exception.UnknownJobRestException;
import org.ow2.proactive_grid_cloud_portal.scheduler.exception.UnknownTaskRestException;


@Path("/scheduler/")
public interface SchedulerTasksRestInterface {

    String ENCODING = "utf-8";

    /**
     * Returns a list of the name of the tasks belonging to job <code>jobId</code>
     * @param sessionId a valid session id
     * @param jobId jobid one wants to list the tasks' name
     * @return a list of tasks' name 
     */
    @GET
    @Path("jobs/{jobid}/tasks")
    @Produces("application/json")
    List<String> getJobTasksIds(@HeaderParam("sessionid") String sessionId, @PathParam("jobid") String jobId)
            throws NotConnectedRestException, UnknownJobRestException, PermissionRestException;

    /**
     * Returns a list of the name of the tasks belonging to job <code>jobId</code> with pagination
     * @param sessionId a valid session id
     * @param jobId jobid one wants to list the tasks' name
     * @param offset the number of the first task to fetch
     * @param limit the number of the last task to fetch (non inclusive)
     * @return a list of tasks' name
     */
    @GET
    @Path("jobs/{jobid}/tasks/paginated")
    @Produces("application/json")
    List<String> getJobTasksIdsPaginated(@HeaderParam("sessionid") String sessionId,
            @PathParam("jobid") String jobId, @QueryParam("offset") @DefaultValue("0") int offset,
            @QueryParam("limit") @DefaultValue("50") int limit)
                    throws NotConnectedRestException, UnknownJobRestException, PermissionRestException;

    /**
     * Returns a list of the name of the tasks belonging to job <code>jobId</code>
     * @param sessionId a valid session id
     * @param jobId jobid one wants to list the tasks' name
     * @param taskTag the tag used to filter the tasks.
     * @return a list of tasks' name
     */
    @GET
    @Path("jobs/{jobid}/tasks/tag/{tasktag}")
    @Produces("application/json")
    List<String> getJobTasksIdsByTag(@HeaderParam("sessionid") String sessionId,
            @PathParam("jobid") String jobId, @PathParam("tasktag") String taskTag)
                    throws NotConnectedRestException, UnknownJobRestException, PermissionRestException;

    /**
     * Returns a list of the name of the tasks belonging to job <code>jobId</code> (with pagination)
     * @param sessionId a valid session id.
     * @param jobId the job id.
     * @param taskTag the tag used to filter the tasks.
     * @param offset the number of the first task to fetch
     * @param limit the number of the last task to fetch (non inclusive)
     * @return a list of task' states of the job <code>jobId</code> filtered by a given tag, for a given pagination.
     */
    @GET
    @GZIP
    @Path("jobs/{jobid}/tasks/tag/{tasktag}/paginated")
    @Produces("application/json")
    List<String> getJobTasksIdsByTagPaginated(@HeaderParam("sessionid") String sessionId,
            @PathParam("jobid") String jobId, @PathParam("tasktag") String taskTag,
            @QueryParam("offset") @DefaultValue("0") int offset,
            @QueryParam("limit") @DefaultValue("50") int limit)
                    throws NotConnectedRestException, UnknownJobRestException, PermissionRestException;

    /**
     * Returns a list of the tags of the tasks belonging to job <code>jobId</code>
     * @param sessionId a valid session id
     * @param jobId jobid one wants to list the tasks' tags
     * @return a list of tasks' name
     */
    @GET
    @Path("jobs/{jobid}/tasks/tags")
    @Produces("application/json")
    List<String> getJobTaskTags(@HeaderParam("sessionid") String sessionId, @PathParam("jobid") String jobId)
            throws NotConnectedRestException, UnknownJobRestException, PermissionRestException;

    /**
     * Returns a list of the tags of the tasks belonging to job <code>jobId</code> and filtered by a prefix pattern
     * @param sessionId a valid session id
     * @param jobId jobid one wants to list the tasks' tags
     * @param prefix the prefix used to filter tags
     * @return a list of tasks' name
     */
    @GET
    @Path("jobs/{jobid}/tasks/tags/startsWith/{prefix}")
    @Produces("application/json")
    List<String> getJobTaskTagsPrefix(@HeaderParam("sessionid") String sessionId,
            @PathParam("jobid") String jobId, @PathParam("prefix") String prefix)
                    throws NotConnectedRestException, UnknownJobRestException, PermissionRestException;

    /**
     * Returns a list of taskState 
     * @param sessionId a valid session id
     * @param jobId the job id
     * @return a list of task' states of the job <code>jobId</code>
     */
    @GET
    @GZIP
    @Path("jobs/{jobid}/taskstates")
    @Produces("application/json")
    List<TaskStateData> getJobTaskStates(@HeaderParam("sessionid") String sessionId,
            @PathParam("jobid") String jobId)
                    throws NotConnectedRestException, UnknownJobRestException, PermissionRestException;

    /**
     * Returns a list of taskState with pagination
     * @param sessionId a valid session id
     * @param jobId the job id
     * @param offset the index of the first TaskState to return
     * @param limit the index (non inclusive) of the last TaskState to return
     * @return a list of task' states of the job <code>jobId</code>
     */
    @GET
    @GZIP
    @Path("jobs/{jobid}/taskstates/paginated")
    @Produces("application/json")
    List<TaskStateData> getJobTaskStatesPaginated(@HeaderParam("sessionid") String sessionId,
            @PathParam("jobid") String jobId, @QueryParam("offset") @DefaultValue("0") int offset,
            @QueryParam("limit") @DefaultValue("50") int limit)
                    throws NotConnectedRestException, UnknownJobRestException, PermissionRestException;

    /**
     * Returns a list of taskState of the tasks filtered by a given tag.
     * @param sessionId a valid session id.
     * @param jobId the job id.
     * @param taskTag the tag used to filter the tasks.
     * @return a list of task' states of the job <code>jobId</code> filtered by a given tag.
     */
    @GET
    @GZIP
    @Path("jobs/{jobid}/taskstates/{tasktag}")
    @Produces("application/json")
    List<TaskStateData> getJobTaskStatesByTag(@HeaderParam("sessionid") String sessionId,
            @PathParam("jobid") String jobId, @PathParam("tasktag") String taskTag)
                    throws NotConnectedRestException, UnknownJobRestException, PermissionRestException;

    /**
     * Returns a list of taskState of the tasks filtered by a given tag and paginated.
     * @param sessionId a valid session id.
     * @param jobId the job id.
     * @param taskTag the tag used to filter the tasks.
     * @param offset the number of the first task to fetch
     * @param limit the number of the last task to fetch (non inclusive)
     * @return a list of task' states of the job <code>jobId</code> filtered by a given tag, for a given pagination.
     */
    @GET
    @GZIP
    @Path("jobs/{jobid}/taskstates/{tasktag}/paginated")
    @Produces("application/json")
    List<TaskStateData> getJobTaskStatesByTagPaginated(@HeaderParam("sessionid") String sessionId,
            @PathParam("jobid") String jobId, @PathParam("tasktag") String taskTag,
            @QueryParam("offset") @DefaultValue("0") int offset,
            @QueryParam("limit") @DefaultValue("50") int limit)
                    throws NotConnectedRestException, UnknownJobRestException, PermissionRestException;

    /**
     * Return the task state of the task <code>taskname</code> of the job <code>jobId</code> 
     * @param sessionId a valid session id
     * @param jobId the id of the job
     * @param taskname the name of the task
     * @return the task state of the task  <code>taskname</code> of the job <code>jobId</code> 
     */
    @GET
    @Path("jobs/{jobid}/tasks/{taskname}")
    @Produces("application/json")
    TaskStateData jobtasks(@HeaderParam("sessionid") String sessionId, @PathParam("jobid") String jobId,
            @PathParam("taskname") String taskname) throws NotConnectedRestException, UnknownJobRestException,
                    PermissionRestException, UnknownTaskRestException;

}
