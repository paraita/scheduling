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
package org.ow2.proactive_grid_cloud_portal.scheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;

import org.dozer.DozerBeanMapper;
import org.dozer.Mapper;
import org.jboss.resteasy.annotations.GZIP;
import org.ow2.proactive.scheduler.common.Scheduler;
import org.ow2.proactive.scheduler.common.exception.NotConnectedException;
import org.ow2.proactive.scheduler.common.exception.PermissionException;
import org.ow2.proactive.scheduler.common.exception.UnknownJobException;
import org.ow2.proactive.scheduler.common.job.JobState;
import org.ow2.proactive.scheduler.common.task.TaskState;
import org.ow2.proactive.scheduler.common.util.SchedulerProxyUserInterface;
import org.ow2.proactive_grid_cloud_portal.common.SchedulerTasksRestInterface;
import org.ow2.proactive_grid_cloud_portal.common.Session;
import org.ow2.proactive_grid_cloud_portal.common.SessionStore;
import org.ow2.proactive_grid_cloud_portal.common.SharedSessionStore;
import org.ow2.proactive_grid_cloud_portal.scheduler.dto.TaskStateData;
import org.ow2.proactive_grid_cloud_portal.scheduler.exception.NotConnectedRestException;
import org.ow2.proactive_grid_cloud_portal.scheduler.exception.PermissionRestException;
import org.ow2.proactive_grid_cloud_portal.scheduler.exception.UnknownJobRestException;
import org.ow2.proactive_grid_cloud_portal.scheduler.exception.UnknownTaskRestException;


/**
 * This class exposes the Scheduler as a RESTful service.
 */
@Path("/scheduler/")
public class SchedulerJobTaskRest implements SchedulerTasksRestInterface {

    /**
     * If the rest api was unable to instantiate the value from byte array
     * representation
     */
    public static final String UNKNOWN_VALUE_TYPE = "Unknown value type";

    private final SessionStore sessionStore = SharedSessionStore.getInstance();

    private static final Mapper mapper = new DozerBeanMapper(
        Collections.singletonList("org/ow2/proactive_grid_cloud_portal/scheduler/dozer-mappings.xml"));

    @Context
    private HttpServletRequest httpServletRequest;

    /**
     * Returns a list of the name of the tasks belonging to job <code>jobId</code>
     * @param sessionId a valid session id
     * @param jobId jobid one wants to list the tasks' name
     * @return a list of tasks' name 
     */
    @Override
    @GET
    @Path("jobs/{jobid}/tasks")
    @Produces("application/json")
    public List<String> getJobTasksIds(@HeaderParam("sessionid") String sessionId,
            @PathParam("jobid") String jobId)
                    throws NotConnectedRestException, UnknownJobRestException, PermissionRestException {
        return getJobTasksIdsPaginated(sessionId, jobId, 0, 50);
    }

    /**
     * Returns a list of the name of the tasks belonging to job <code>jobId</code> with pagination
     * @param sessionId a valid session id
     * @param jobId jobid one wants to list the tasks' name
     * @param offset the number of the first task to fetch
     * @param limit the number of the last task to fetch (non inclusive)
     * @return a list of tasks' name
     */
    @Override
    @GET
    @Path("jobs/{jobid}/tasks/paginated")
    @Produces("application/json")
    public List<String> getJobTasksIdsPaginated(@HeaderParam("sessionid") String sessionId,
            @PathParam("jobid") String jobId, @QueryParam("offset") @DefaultValue("0") int offset,
            @QueryParam("limit") @DefaultValue("50") int limit)
                    throws NotConnectedRestException, UnknownJobRestException, PermissionRestException {
        try {
            Scheduler s = checkAccess(sessionId, "jobs/" + jobId + "/tasks");

            JobState jobState = s.getJobState(jobId);
            List<String> tasksName = new ArrayList<>(jobState.getTasksPaginated(offset, limit).size());
            for (TaskState ts : jobState.getTasksPaginated(offset, limit)) {
                tasksName.add(ts.getId().getReadableName());
            }

            return tasksName;
        } catch (PermissionException e) {
            throw new PermissionRestException(e);
        } catch (UnknownJobException e) {
            throw new UnknownJobRestException(e);
        } catch (NotConnectedException e) {
            throw new NotConnectedRestException(e);
        }
    }

    /**
     * Returns a list of the name of the tasks belonging to job and filtered by a given tag.
     * <code>jobId</code>
     *
     * @param sessionId
     *            a valid session id
     * @param jobId
     *            jobid one wants to list the tasks' name
     * @param taskTag
     *            the tag used to filter the tasks.
     * @return a list of tasks' name
     */
    @Override
    @GET
    @Path("jobs/{jobid}/tasks/tag/{tasktag}")
    @Produces("application/json")
    public List<String> getJobTasksIdsByTag(@HeaderParam("sessionid") String sessionId,
            @PathParam("jobid") String jobId, @PathParam("tasktag") String taskTag)
                    throws NotConnectedRestException, UnknownJobRestException, PermissionRestException {
        try {
            Scheduler s = checkAccess(sessionId, "jobs/" + jobId + "/tasks");

            JobState jobState = s.getJobState(jobId);
            List<TaskState> tasks = jobState.getTaskByTagPaginated(taskTag, 0, 50);
            List<String> tasksName = new ArrayList<>(tasks.size());
            for (TaskState ts : tasks) {
                tasksName.add(ts.getId().getReadableName());
            }

            return tasksName;
        } catch (PermissionException e) {
            throw new PermissionRestException(e);
        } catch (UnknownJobException e) {
            throw new UnknownJobRestException(e);
        } catch (NotConnectedException e) {
            throw new NotConnectedRestException(e);
        }
    }

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
    public List<String> getJobTasksIdsByTagPaginated(@HeaderParam("sessionid") String sessionId,
            @PathParam("jobid") String jobId, @PathParam("tasktag") String taskTag,
            @QueryParam("offset") @DefaultValue("0") int offset,
            @QueryParam("limit") @DefaultValue("50") int limit)
                    throws NotConnectedRestException, UnknownJobRestException, PermissionRestException {

        try {
            Scheduler s = checkAccess(sessionId, "jobs/" + jobId + "/tasks/" + taskTag + "/paginated");

            JobState jobState = s.getJobState(jobId);
            List<TaskState> tasks = jobState.getTaskByTagPaginated(taskTag, offset, limit);
            List<String> tasksName = new ArrayList<>(tasks.size());

            for (TaskState ts : tasks) {
                tasksName.add(ts.getId().getReadableName());
            }

            return tasksName;
        } catch (PermissionException e) {
            throw new PermissionRestException(e);
        } catch (UnknownJobException e) {
            throw new UnknownJobRestException(e);
        } catch (NotConnectedException e) {
            throw new NotConnectedRestException(e);
        }
    }

    /**
     * Returns a list of the tags of the tasks belonging to job <code>jobId</code>
     * @param sessionId a valid session id
     * @param jobId jobid one wants to list the tasks' tags
     * @return a list of tasks' name
     */
    @GET
    @Path("jobs/{jobid}/tasks/tags")
    @Produces("application/json")
    public List<String> getJobTaskTags(@HeaderParam("sessionid") String sessionId,
            @PathParam("jobid") String jobId)
                    throws NotConnectedRestException, UnknownJobRestException, PermissionRestException {
        try {
            Scheduler s = checkAccess(sessionId, "jobs/" + jobId + "/tasks/tags");
            JobState jobState = s.getJobState(jobId);
            return jobState.getTags();
        } catch (PermissionException e) {
            throw new PermissionRestException(e);
        } catch (UnknownJobException e) {
            throw new UnknownJobRestException(e);
        } catch (NotConnectedException e) {
            throw new NotConnectedRestException(e);
        }
    }

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
    public List<String> getJobTaskTagsPrefix(@HeaderParam("sessionid") String sessionId,
            @PathParam("jobid") String jobId, @PathParam("prefix") String prefix)
                    throws NotConnectedRestException, UnknownJobRestException, PermissionRestException {
        try {
            Scheduler s = checkAccess(sessionId, "jobs/" + jobId + "/tasks/tags/startswith/" + prefix);
            JobState jobState = s.getJobState(jobId);
            return jobState.getTags(prefix);
        } catch (PermissionException e) {
            throw new PermissionRestException(e);
        } catch (UnknownJobException e) {
            throw new UnknownJobRestException(e);
        } catch (NotConnectedException e) {
            throw new NotConnectedRestException(e);
        }
    }

    /**
     * Returns a list of taskState
     *
     * @param sessionId
     *            a valid session id
     * @param jobId
     *            the job id
     * @return a list of task' states of the job <code>jobId</code>
     */
    @Override
    @GET
    @GZIP
    @Path("jobs/{jobid}/taskstates")
    @Produces("application/json")
    public List<TaskStateData> getJobTaskStates(@HeaderParam("sessionid") String sessionId,
            @PathParam("jobid") String jobId)
                    throws NotConnectedRestException, UnknownJobRestException, PermissionRestException {
        return getJobTaskStatesPaginated(sessionId, jobId, 0, 50);
    }

    /**
     * Returns a list of taskState with pagination
     *
     * @param sessionId a valid session id
     * @param jobId the job id
     * @param offset the index of the first TaskState to return
     * @param limit the index (non inclusive) of the last TaskState to return
     * @return a list of task' states of the job <code>jobId</code>
     */
    @Override
    @GET
    @GZIP
    @Path("jobs/{jobid}/taskstates/paginated")
    @Produces("application/json")
    public List<TaskStateData> getJobTaskStatesPaginated(@HeaderParam("sessionid") String sessionId,
            @PathParam("jobid") String jobId, @QueryParam("offset") @DefaultValue("0") int offset,
            @QueryParam("limit") @DefaultValue("50") int limit)
                    throws NotConnectedRestException, UnknownJobRestException, PermissionRestException {
        try {
            Scheduler s = checkAccess(sessionId, "jobs/" + jobId + "/taskstates/paginated");
            JobState jobState = s.getJobState(jobId);

            return map(jobState.getTasksPaginated(offset, limit), TaskStateData.class);
        } catch (PermissionException e) {
            throw new PermissionRestException(e);
        } catch (UnknownJobException e) {
            throw new UnknownJobRestException(e);
        } catch (NotConnectedException e) {
            throw new NotConnectedRestException(e);
        }
    }

    /**
     * Returns a list of taskState of the tasks filtered by a given tag.
     *
     * @param sessionId
     *            a valid session id
     * @param jobId
     *            the job id
     * @param taskTag
     *             the tag used to filter the tasks
     * @return a list of task' states of the job <code>jobId</code> filtered by a given tag.
     */
    @Override
    @GET
    @GZIP
    @Path("jobs/{jobid}/taskstates/{tasktag}")
    @Produces("application/json")
    public List<TaskStateData> getJobTaskStatesByTag(@HeaderParam("sessionid") String sessionId,
            @PathParam("jobid") String jobId, @PathParam("tasktag") String taskTag)
                    throws NotConnectedRestException, UnknownJobRestException, PermissionRestException {
        try {
            Scheduler s = checkAccess(sessionId, "jobs/" + jobId + "/taskstates/" + taskTag);
            JobState jobState = s.getJobState(jobId);
            return map(jobState.getTaskByTagPaginated(taskTag, 0, 50), TaskStateData.class);
        } catch (PermissionException e) {
            throw new PermissionRestException(e);
        } catch (UnknownJobException e) {
            throw new UnknownJobRestException(e);
        } catch (NotConnectedException e) {
            throw new NotConnectedRestException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @GET
    @GZIP
    @Path("jobs/{jobid}/taskstates/{tasktag}/paginated")
    @Produces("application/json")
    public List<TaskStateData> getJobTaskStatesByTagPaginated(@HeaderParam("sessionid") String sessionId,
            @PathParam("jobid") String jobId, @PathParam("tasktag") String taskTag,
            @QueryParam("offset") @DefaultValue("0") int offset,
            @QueryParam("limit") @DefaultValue("50") int limit)
                    throws NotConnectedRestException, UnknownJobRestException, PermissionRestException {
        try {
            Scheduler s = checkAccess(sessionId, "jobs/" + jobId + "/taskstates/" + taskTag + "/paginated");
            JobState jobState = s.getJobState(jobId);
            return map(jobState.getTaskByTagPaginated(taskTag, offset, limit), TaskStateData.class);
        } catch (PermissionException e) {
            throw new PermissionRestException(e);
        } catch (UnknownJobException e) {
            throw new UnknownJobRestException(e);
        } catch (NotConnectedException e) {
            throw new NotConnectedRestException(e);
        }
    }

    /**
     * Return the task state of the task <code>taskname</code> of the job
     * <code>jobId</code>
     *
     * @param sessionId
     *            a valid session id
     * @param jobId
     *            the id of the job
     * @param taskname
     *            the name of the task
     * @return the task state of the task <code>taskname</code> of the job
     *         <code>jobId</code>
     */
    @Override
    @GET
    @Path("jobs/{jobid}/tasks/{taskname}")
    @Produces("application/json")
    public TaskStateData jobtasks(@HeaderParam("sessionid") String sessionId,
            @PathParam("jobid") String jobId, @PathParam("taskname") String taskname)
                    throws NotConnectedRestException, UnknownJobRestException, PermissionRestException,
                    UnknownTaskRestException {
        try {
            Scheduler s = checkAccess(sessionId, "jobs/" + jobId + "/tasks/" + taskname);

            JobState jobState = s.getJobState(jobId);

            for (TaskState ts : jobState.getTasks()) {
                if (ts.getId().getReadableName().equals(taskname)) {
                    return mapper.map(ts, TaskStateData.class);
                }
            }

            throw new UnknownTaskRestException("task " + taskname + "not found");
        } catch (PermissionException e) {
            throw new PermissionRestException(e);
        } catch (UnknownJobException e) {
            throw new UnknownJobRestException(e);
        } catch (NotConnectedException e) {
            throw new NotConnectedRestException(e);
        }
    }

    /**
     * the method check is the session id is valid i.e. a scheduler client is
     * associated to the session id in the session map. If not, a
     * NotConnectedRestException is thrown specifying the invalid access *
     *
     * @return the scheduler linked to the session id, an NotConnectedRestException,
     *         if no such mapping exists.
     * @throws NotConnectedRestException
     */
    private SchedulerProxyUserInterface checkAccess(String sessionId, String path)
            throws NotConnectedRestException {
        Session session = sessionStore.get(sessionId);

        if (session == null) {
            throw new NotConnectedRestException(
                "You are not connected to the scheduler, you should log on first");
        }

        SchedulerProxyUserInterface schedulerProxy = session.getScheduler();

        if (schedulerProxy == null) {
            throw new NotConnectedRestException(
                "You are not connected to the scheduler, you should log on first");
        }

        renewLeaseForClient(schedulerProxy);

        return schedulerProxy;
    }

    /**
     * Call a method on the scheduler's frontend in order to renew the lease the
     * user has on this frontend. see PORTAL-70
     *
     * @throws NotConnectedRestException
     */
    protected void renewLeaseForClient(Scheduler scheduler) throws NotConnectedRestException {
        try {
            scheduler.renewSession();
        } catch (NotConnectedException e) {
            throw new NotConnectedRestException(e);
        }
    }

    private static <T> List<T> map(List<?> toMaps, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Object toMap : toMaps) {
            result.add(mapper.map(toMap, type));
        }
        return result;
    }

}
