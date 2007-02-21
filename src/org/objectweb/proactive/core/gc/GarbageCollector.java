package org.objectweb.proactive.core.gc;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.apache.log4j.Level;
import org.objectweb.proactive.core.ProActiveRuntimeException;
import org.objectweb.proactive.core.UniqueID;
import org.objectweb.proactive.core.body.AbstractBody;
import org.objectweb.proactive.core.body.HalfBody;
import org.objectweb.proactive.core.body.LocalBodyStore;
import org.objectweb.proactive.core.body.proxy.UniversalBodyProxy;
import org.objectweb.proactive.core.body.request.BodyRequest;
import org.objectweb.proactive.core.exceptions.body.SendReplyCommunicationException;
import org.objectweb.proactive.core.exceptions.manager.TypedNFEListener;

/**
 * Remember if we terminated and why.
 */
enum FinishedState {
    /** We didn't terminate */
    NOT_FINISHED,
    /** Acyclic garbage */
    ACYCLIC,
    /** Cyclic garbage, notify referencers */
    CYCLIC;
}

/**
 * Locking: single lock: the GarbageCollector instance
 *
 * Broadcasting is done in a separate thread to avoid blocking. It would suck
 * less to use non-blocking I/O instead of threads.
 */
public class GarbageCollector {

    /**
     * TimeToBroadcast
     * Time is always in milliseconds. It is fundamental for this value
     * to be the same in all JVM of the distributed system, so think twice
     * before changing it.
     * TODO: make it dynamic.
     */
    static final int TTB = 30000;

    /**
     * TimeToAlone
     * After this delay, we suppose we got a message from all our referencers.
     */
    static final int TTA = 5 * TTB;

    static {
        if (dgcIsEnabled()) {
            GarbageCollectorThread.start();
        }
    }

    /**
     * Number of consecutive consensus to agree on until the object decides
     * to belong to some garbage cycle.
     * My gut feeling oscillates between 1 and 3 with a preference to 1 but
     * the theoretical proof will tell us.
     */
    private static final int NR_CONSENSUS = 1;

    /**
     * Whether the object is pinned somewhere.
     */
    private boolean registered;

    /**
     * Whether the GC should stop being part of the algorithm.
     * This flag is used to stop the GC thread, and notify the referencers
     * of some cyclic garbage.
     */
    private FinishedState finished;

    /**
     * Statistical info: count the number of iterations.
     */
    private long iterations;

    /**
     * My last activity
     * Lamport clock
     * Incremented for every activity:
     *  - service stopped => between each service
     *  - loss of a referencer => avoid cycle with no owner
     *  - loss of a referenced => maybe loss of parent
     *
     * It is always the maximum of my activity and those received.
     */
    private Activity lastActivity;

    /**
     * My parent in the reverse spanning tree.
     */
    private Referenced parent;

    /**
     * Count consecutively reached consensus up to NR_CONSENSUS
     */
    private int nrReachedConsensus;

    /**
     * List of (weak references to) referenced proxies.
     * We have to track every proxy instance, but
     * for a given target object, one proxy is enough for the broadcast.
     */
    private final HashMap<UniqueID, Referenced> referenced;

    /**
     * New referenced proxies are kept as strong references on the first round,
     * to be sure they are sent at least one message before vanishing.
     */
    private final List<UniversalBodyProxy> newReferenced;

    /**
     * These are just strings, not remote references.
     * We keep track of referencers to know when we lose one.
     */
    private final HashMap<UniqueID, Referencer> referencers;

    /**
     * For how long did no one reference us? This is to be compared to TTA.
     */
    private long aloneTimestamp;

    /**
     * The AO we belong to
     */
    protected final AbstractBody body;

    /**
     * To detect new activities
     */
    private boolean previouslyBusy;

    /**
     * Build a GarbageCollector instance for each active object
     */
    public GarbageCollector(AbstractBody body) {
        this.registered = body instanceof HalfBody;
        this.finished = FinishedState.NOT_FINISHED;
        this.iterations = 0;
        this.lastActivity = new Activity(body.getID(), 0);
        this.parent = null;
        this.nrReachedConsensus = 0;
        this.referenced = new HashMap<UniqueID, Referenced>();
        this.newReferenced = new LinkedList<UniversalBodyProxy>();
        this.referencers = new HashMap<UniqueID, Referencer>();
        this.aloneTimestamp = System.currentTimeMillis();
        this.body = body;
        this.previouslyBusy = true;
        body.addNFEListener(new TypedNFEListener(
                SendReplyCommunicationException.class,
                SendReplyCommunicationExceptionHandler.instance));
    }

    /**
     * A new activity has been made, either from myself or a referencer
     */
    private synchronized void setLastActivity(Activity ac) {
        this.lastActivity = ac;
        this.nrReachedConsensus = 0;
        this.parent = null;
    }

    /**
     * Something happened on the active object
     */
    public synchronized void incActivity() {
        Activity newActivity = new Activity(this.body.getID(),
                this.lastActivity.getCounter() + 1);
        this.setLastActivity(newActivity);
    }

    /**
     * Remove old referencers, this can cause an increase
     * of the activity.
     */
    private void purgeReferencers() {
        Collection<String> removed = new Vector<String>();
        Collection<UniqueID> removedId = new Vector<UniqueID>();
        long pastTimestamp = System.currentTimeMillis() - TTA;
        for (Map.Entry<UniqueID, Referencer> entry : this.referencers.entrySet()) {
            UniqueID id = entry.getKey();
            long timestamp = entry.getValue().getLastMessageTimestamp();
            if (timestamp < pastTimestamp) {
                removedId.add(id);
                removed.add(id.shortString());
            }
        }

        if (!removed.isEmpty()) {
            this.log(Level.DEBUG,
                "Removed " + removed.size() + " referencers: " + removed);
            this.incActivity();
            for (UniqueID i : removedId) {
                this.referencers.remove(i);
            }
            if (this.referencers.isEmpty()) {
                this.aloneTimestamp = System.currentTimeMillis();
            }
        }
    }

    /**
     * Build the message we are going to send to the referenced p
     */
    private GCSimpleMessage buildMessageForProxy(boolean isMyActivity,
        Referenced parent, Referenced p) {
        boolean consensus = false;
        GCSimpleResponse resp = p.getLastResponse();
        if (!isBusy() && (resp != null) &&
                resp.getConsensusActivity().equals(this.lastActivity) &&
                (isMyActivity || (parent != null))) {
            consensus = true;
            if (p.equals(parent)) {
                for (Referencer kr : this.referencers.values()) {
                    boolean krConsensus = kr.getConsensus(this.lastActivity);
                    consensus = consensus && krConsensus;
                    if (!consensus) {
                        break;
                    }
                }
            }
        }
        return new GCSimpleMessage(p, this.body.getID(), consensus,
            this.lastActivity);
    }

    /**
     * A new referenced has been sent a GC message to, so now we can add it to
     * our weak references.
     */
    private void promoteReferenced(UniversalBodyProxy ubp) {
        UniqueID proxyBodyId = ubp.getBodyID();
        Referenced p = this.referenced.get(proxyBodyId);
        if (p == null) {
            p = new Referenced(ubp, this);
            this.referenced.put(proxyBodyId, p);
        } else {
            p.add(ubp);
        }
    }

    /**
     * Remove our referenced which weak reference is null. This can cause a
     * new activity.
     */
    private Collection<Referenced> purgeReferenced() {
        Collection<Referenced> deleted = new LinkedList<Referenced>();
        for (Map.Entry<UniqueID, Referenced> entry : this.referenced.entrySet()) {
            Referenced p = entry.getValue();
            if (!p.isReferenced()) {
                deleted.add(p);
            }
        }

        if (!deleted.isEmpty()) {
            for (Referenced r : deleted) {
                this.referenced.remove(r.getBodyID());
            }
            this.incActivity();
        }

        return deleted;
    }

    private boolean isLastActivityMine() {
        return this.lastActivity.getBodyID().equals(this.body.getID());
    }

    /**
     * Check if we found a new parent
     */
    void newResponse(Referenced ref) {
        if (this.parent == null) {
            Activity refActivity = ref.getLastResponse().getConsensusActivity();
            if (this.lastActivity.equals(refActivity) && !isLastActivityMine()) {
                this.parent = ref;
            }
        }
    }

    /**
     * Queue a message for each referenced
     */
    private Collection<GCSimpleMessage> broadcast() {
        /* Remove old referencers */
        purgeReferencers();

        /* Add new referenced proxys */
        for (UniversalBodyProxy ubp : this.newReferenced) {
            promoteReferenced(ubp);
        }
        this.newReferenced.clear();

        /* Remove failing or unreferenced referenced proxys */
        Collection<Referenced> deleted = this.purgeReferenced();

        boolean isMyActivity = this.isLastActivityMine();
        Collection<Referenced> refs = this.referenced.values();
        this.log(Level.DEBUG, "Sending GC Message to: " + refs);
        Vector<GCSimpleMessage> messages = new Vector<GCSimpleMessage>(refs.size());
        for (Referenced p : refs) {
            messages.add(buildMessageForProxy(isMyActivity, parent, p));
        }

        if (!deleted.isEmpty()) {
            this.log(Level.DEBUG,
                "Deleting " + deleted.size() + " referenced: " + deleted);
        }

        return messages;
    }

    /**
     * The DGC found out that the AO is garbage
     */
    private void terminateBody() {
        try {
            BodyRequest br = new BodyRequest(this.body, "terminate",
                    new Class[0], new Object[0], false);

            br.send(this.body);
            // this.body.terminate(); Does not wake up the AO
        } catch (ProActiveRuntimeException e) {
            // org.objectweb.proactive.core.ProActiveRuntimeException:
            // Cannot perform this call because this body is inactive
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Did the GC already do its job?
     */
    boolean isFinished() {
        return this.finished != FinishedState.NOT_FINISHED;
    }

    /**
     * The GC has just done its job
     */
    void setFinishedState(FinishedState state) {
        if (this.finished != FinishedState.NOT_FINISHED) {
            throw new IllegalStateException("Was already finished:" +
                this.finished);
        }
        this.finished = state;
    }

    /**
     * Terminate the AO if the DGC decides so
     */
    private String checkConsensus() {
        if (this.isBusy()) {
            return null;
        }
        String goodbye = null;

        /* Did someone notify us of a cycle? */
        boolean consensusAlreadyReached = false;
        for (Map.Entry<UniqueID, Referenced> entry : this.referenced.entrySet()) {
            if (entry.getValue().hasTerminated()) {
                goodbye = "####### Noticed of garbage cycle from " +
                    entry.getKey().shortString() + " => PAF: " +
                    this.lastActivity;
                this.setFinishedState(FinishedState.CYCLIC);
                consensusAlreadyReached = true;
                break;
            }
        }

        if (!consensusAlreadyReached) {
            if (this.referencers.isEmpty()) {
                if ((System.currentTimeMillis() - this.aloneTimestamp) <= TTA) {
                    return null;
                }
                goodbye = "####### No more known referencers => PAF";
                this.setFinishedState(FinishedState.ACYCLIC);
            } else {
                if (this.lastActivity.getBodyID().equals(this.body.getID())) {
                    for (Referencer kr : this.referencers.values()) {
                        if (!kr.getConsensus(this.lastActivity)) {
                            return null;
                        }
                    }
                    this.nrReachedConsensus++;
                    /* We reached a consensus on my activity */
                    if (this.nrReachedConsensus < GarbageCollector.NR_CONSENSUS) {
                    	int backupNrReachedConsensus = this.nrReachedConsensus; // setLastActivity() will clear it
                        this.incActivity();
                        this.nrReachedConsensus = backupNrReachedConsensus;
                        return null;
                    }
                    goodbye = "####### Detected garbage cycle => PAF: " +
                        this.lastActivity;
                    this.setFinishedState(FinishedState.CYCLIC);
                } else {
                    return null;
                }
            }
        }

        if (!(this.body instanceof HalfBody)) {
            terminateBody();
        }

        return goodbye;
    }

    /**
     * Locally decide if an AO is busy, this can make a new activity
     * We don't handle the case of immediate services playing with the
     * request queue.
     */
    protected boolean isBusy() {
        boolean currentlyBusy = this.registered;

        try {
            currentlyBusy = currentlyBusy || this.body.isInImmediateService();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        try {
            currentlyBusy = currentlyBusy ||
                !this.body.getRequestQueue().isWaitingForRequest();
        } catch (ProActiveRuntimeException pre) {

            /* Cannot perform this call because this body is inactive */
        }

        if (previouslyBusy && !currentlyBusy) {
            this.incActivity();
        }

        this.previouslyBusy = currentlyBusy;
        return currentlyBusy;
    }

    /**
     * Run by the broadcasting thread
     */
    protected synchronized Collection<GCSimpleMessage> iteration() {
        if (!this.isFinished() && this.body.isAlive()) {
            String goodbye = this.checkConsensus();
            this.iterations++;
            if (this.isFinished()) {
                this.log(Level.INFO,
                    "Goodbye because: " + goodbye + " after " + iterations +
                    " iterations");
            } else {
                return this.broadcast();
            }
        }
        return null;
    }

    /**
     * A new referenced was deserialized
     */
    public synchronized void addProxy(AbstractBody body,
        UniversalBodyProxy proxy) {
        if (body != this.body) {
            this.log(Level.FATAL, "Wrong body");
        }
        UniqueID proxyID = proxy.getBodyID();
        if (!proxyID.equals(this.body.getID()) &&
                !this.referenced.containsKey(proxyID)) {
            newReferenced.add(proxy);
            this.log(Level.DEBUG,
                "New referenced: " + proxy.getBodyID().shortString());
        }
    }

    /**
     * For IC2D and the logs
     */
    private String getStatus() {
        String state = this.isBusy() ? "busy" : "idle";
        return state + " " + this.lastActivity + " from " + this.parent;
    }

    /**
     * For IC2D
     */
    public static String getDgcState(UniqueID bodyID) {
        if (!dgcIsEnabled()) {
            return "DGC Disabled";
        }

        AbstractBody body = (AbstractBody) LocalBodyStore.getInstance()
                                                         .getLocalBody(bodyID);
        if (body == null) {
            AsyncLogger.queueLog(Level.WARN, "Body " + bodyID + " not found");
            return "Body not found";
        }

        GarbageCollector gc = body.getGarbageCollector();
        return gc.body.getID().shortString() + ": " + gc.getStatus();
    }

    /**
     * For IC2D
     */
    public Collection<UniqueID> getReferencesID() {
        return new Vector<UniqueID>(this.referenced.keySet());
    }

    /**
     * The method remotely called by the referencer on the referenced
     */
    public synchronized GCResponse receiveGCMessage(GCMessage mesg) {
        long start = System.currentTimeMillis();
        GCResponse response = new GCResponse();
        this.log(Level.DEBUG,
            "Beginning processing of " + mesg.size() + " messages");
        for (GCSimpleMessage m : mesg) {
            response.add(this.receiveSimpleGCMessage(m));
        }
        long duration = System.currentTimeMillis() - start;
        this.log(Level.DEBUG,
            "Ending processing of " + mesg.size() + " messages in " + duration +
            " ms");
        return response;
    }

    /**
     * Called for each message
     */
    GCSimpleResponse receiveSimpleGCMessage(GCSimpleMessage mesg) {
        UniqueID senderID = mesg.getSender();
        Referencer kr = this.referencers.get(senderID);
        GCSimpleResponse resp = null;
        if (this.finished == FinishedState.CYCLIC) {
            this.log(Level.DEBUG, "cycle to " + mesg.getSender().shortString());
            if (kr == null) {
                this.log(Level.FATAL, "Cycle notification to a newcomer");
            }
            resp = new GCTerminationResponse(this.lastActivity);
        } else if (this.finished == FinishedState.ACYCLIC) {
            throw new IllegalStateException(this.body.getID().shortString() +
                " thought it was alone but received " + mesg);
        }

        if (mesg.getLastActivity().strictlyMoreRecentThan(this.lastActivity)) {
            this.setLastActivity(mesg.getLastActivity());
        }
        if (resp == null) {
            resp = new GCSimpleResponse(this.lastActivity);
        }
        if (kr == null) {
            /* new known referencer */
            kr = new Referencer();
            this.referencers.put(senderID, kr);
            this.log(Level.DEBUG, "New referencer: " + senderID.shortString());
        }
        kr.setLastGCMessage(mesg);
        kr.setGivenActivity(resp.getConsensusActivity());
        this.log(Level.DEBUG, mesg + " -> " + this.getStatus());
        return resp;
    }

    /**
     * Check if we have to use the DGC
     */
    private static Boolean cache = null;

    public static boolean dgcIsEnabled() {
        if (cache == null) {
            cache = new Boolean("true".equals(System.getProperty(
                            "proactive.dgc")));
        }
        return cache.booleanValue();
    }

    /**
     * Wrapper for the logging method prefixing the message with the ID.
     */
    public void log(Level level, String msg) {
        if (AsyncLogger.isEnabledFor(level)) {
            String prefix = level.toString().charAt(0) + ">";
            prefix += ((this.body instanceof HalfBody) ? "h" : "b");
            prefix += this.body.getID().shortString();
            prefix += (" " + System.currentTimeMillis() + " ");
            msg = prefix + msg;
            AsyncLogger.queueLog(level, msg);
        }
    }

    /**
     * Inform the DGC whether the AO is pinned somewhere or not
     */
    public synchronized void setRegistered(boolean registered) {
        this.registered = registered;
    }
}
