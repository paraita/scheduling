/*
 * ################################################################
 *
 * ProActive Parallel Suite(TM): The Java(TM) library for
 *    Parallel, Distributed, Multi-Core Computing for
 *    Enterprise Grids & Clouds
 *
 * Copyright (C) 1997-2010 INRIA/University of 
 * 				Nice-Sophia Antipolis/ActiveEon
 * Contact: proactive@ow2.org or contact@activeeon.com
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; version 3 of
 * the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 * If needed, contact us to obtain a release under GPL Version 2 
 * or a different license than the GPL.
 *
 *  Initial developer(s):               The ProActive Team
 *                        http://proactive.inria.fr/team_members.htm
 *  Contributor(s):
 *
 * ################################################################
 * $$PROACTIVE_INITIAL_DEV$$
 */
package org.ow2.proactive.scheduler.ext.matlab.worker;

import org.ow2.proactive.scheduler.common.task.TaskResult;
import org.ow2.proactive.scheduler.ext.matlab.common.PASolveMatlabGlobalConfig;
import org.ow2.proactive.scheduler.ext.matlab.common.PASolveMatlabTaskConfig;
import org.ow2.proactive.scheduler.ext.matlab.common.exception.InvalidNumberOfParametersException;
import org.ow2.proactive.scheduler.ext.matlab.common.exception.InvalidParameterException;
import org.ow2.proactive.scheduler.ext.matlab.common.exception.MatlabTaskException;
import org.ow2.proactive.scheduler.ext.matlab.worker.util.MatlabEngineConfig;
import org.ow2.proactive.scheduler.ext.matsci.worker.MatSciWorker;
import ptolemy.data.BooleanToken;
import ptolemy.data.Token;
import ptolemy.kernel.util.IllegalActionException;

import java.io.File;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;


/**
 * An active object which handles the interaction between the MatlabTask and a local Matlab engine
 *
 * @author The ProActive Team
 */
public class AOMatlabWorker implements Serializable, MatSciWorker {

    protected PASolveMatlabGlobalConfig paconfig;

    protected PASolveMatlabTaskConfig taskconfig;

    static String nl = System.getProperty("line.separator");

    static String fs = System.getProperty("file.separator");

    /**
     * input script
     */
    private String inputScript = null;

    /**
     * lines of the main script
     */
    private ArrayList<String> scriptLines = new ArrayList<String>();

    private PrintStream outDebug;

    public AOMatlabWorker() {
    }

    public AOMatlabWorker(MatlabEngineConfig matlabConfig) {
        MatlabEngine.setConfiguration(matlabConfig);
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
                try {
                    MatlabEngine.close();
                } catch (Exception e) {
                }
            }
        }));
    }

    public void init(String inputScript, ArrayList<String> scriptLines, PASolveMatlabGlobalConfig paconfig,
            PASolveMatlabTaskConfig taskconfig, MatlabEngineConfig matlabConfig) {
        if (!MatlabEngine.getConfiguration().equals(matlabConfig)) {
            MatlabEngine.close();
        }
        MatlabEngine.setConfiguration(matlabConfig);
        this.inputScript = inputScript;
        this.scriptLines = scriptLines;
        this.paconfig = paconfig;
        this.taskconfig = taskconfig;
        if (paconfig.isDebug()) {
            MatlabEngine.setDebug((byte) 2);
        } else {
            MatlabEngine.setDebug((byte) 0);
        }
    }

    public Serializable execute(int index, TaskResult... results) throws Throwable {
        Token out = null;
        MatlabEngine.Connection conn = MatlabEngine.acquire();
        File localSpace = new File(paconfig.getLocalSpace());
        File tempSubDir = new File(localSpace, paconfig.getTempSubDirName());
        try {
            conn.clear();
            if (!paconfig.isTransferVariables()) {
                if (results != null) {
                    if (results.length > 1) {
                        throw new InvalidNumberOfParametersException(results.length);
                    }

                    if (results.length == 1) {
                        TaskResult res = results[0];

                        if (index != -1) {
                            if (!(res.value() instanceof SplittedResult)) {
                                throw new InvalidParameterException(res.value().getClass());
                            }

                            SplittedResult sr = (SplittedResult) res.value();
                            Token tok = sr.getResult(index);
                            conn.put("in", tok);
                        } else {
                            if (!(res.value() instanceof Token)) {
                                throw new InvalidParameterException(res.value().getClass());
                            }

                            Token in = (Token) res.value();
                            conn.put("in", in);
                        }
                    }
                }
            }

            executeScript(conn);
            if (paconfig.isDebug()) {
                System.out.println("Receiving output:");
                //outDebug.println("Receiving output:");
            }
            if (paconfig.isTransferVariables()) {
                File outputFile = new File(tempSubDir, taskconfig.getOutputVariablesFileName());
                if (paconfig.getMatFileOptions() != null) {
                    conn.evalString("save('" + outputFile + "','out','" + paconfig.getMatFileOptions() +
                        "');");
                } else {
                    conn.evalString("save('" + outputFile + "','out');");
                }

                if (!outputFile.exists()) {
                    throw new MatlabTaskException();
                }
                out = new BooleanToken(true);
            } else {
                try {
                    out = conn.get("out");
                } catch (ptolemy.kernel.util.IllegalActionException e) {
                    throw new MatlabTaskException();
                }
            }
            if (paconfig.isDebug()) {
                System.out.println(out);
                //outDebug.println(out);
                System.out.flush();
                //	outDebug.flush();
            }

            // outputFiles
            if (taskconfig.isOutputFilesThere() && paconfig.isZipOutputFiles()) {
                if (paconfig.isDebug()) {
                    System.out.println("Zipping output files");
                }
                String[] outputFiles = taskconfig.getOutputFiles();
                String[] names = taskconfig.getOutputFilesZipNames();
                for (int i = 0; i < names.length; i++) {
                    File outputZip = new File(tempSubDir, names[i]);
                    //conn.evalString("ProActiveOutputFiles=cell(1,"+outputFiles.length+");");
                    //for (int i=0; i < outputFiles.length; i++) {
                    String updatedFile = outputFiles[i].replaceAll("/", fs);
                    //conn.evalString("ProActiveOutputFiles{"+(i+1)+"}='"+updatedFile+"';");
                    //}
                    conn.evalString("zip('" + outputZip + "',{'" + updatedFile + "'});");
                }

            }

        } finally {
            conn.release();
        }
        return out;
    }

    /**
     * Terminates the Matlab engine
     *
     * @return true for synchronous call
     */
    public boolean terminate() {
        MatlabEngine.close();

        return true;
    }

    public boolean pack() {
        MatlabEngine.Connection conn = MatlabEngine.acquire();
        try {
            conn.evalString("pack;");
            conn.release();
        } catch (IllegalActionException e) {
        }
        return true;
    }

    /**
     * Executes both input and main scripts on the engine
     *
     * @throws Throwable
     */
    protected final void executeScript(MatlabEngine.Connection conn) throws Throwable {

        // Changing dir to local space
        File localSpace = new File(paconfig.getLocalSpace());
        if (localSpace.exists() && localSpace.canRead() && localSpace.canWrite()) {
            conn.evalString("cd('" + localSpace + "');");
        }

        if (paconfig.isTransferSource()) {
            if (paconfig.isDebug()) {
                System.out.println("Unzipping source files");
            }
            File sourceZip = new File(taskconfig.getSourceZipFileURI());
            if (sourceZip.exists() && sourceZip.canRead()) {
                File sourceZipFolder = sourceZip.getParentFile();
                if (!sourceZipFolder.exists() || (!sourceZipFolder.canWrite())) {
                    System.err.println("Error, can't write on : " + sourceZipFolder);
                    throw new IllegalStateException("Error, can't write on : " + sourceZipFolder);
                }
                conn.evalString("restoredefaultpath;");
                conn.evalString("addpath('" + sourceZipFolder + "');");
                conn.evalString("unzip('" + sourceZip + "','" + sourceZipFolder + "');");
                if (paconfig.isDebug()) {
                    System.out.println("Contents of " + sourceZipFolder);
                    for (File f : sourceZipFolder.listFiles()) {
                        System.out.println(f);
                    }
                }
            } else {
                System.err.println("Error, source zip file cannot be accessed at : " + sourceZip);
                throw new IllegalStateException("Error, source zip file cannot be accessed at : " + sourceZip);
            }
        }
        if (paconfig.isTransferEnv()) {
            if (paconfig.isZipEnvFile()) {
                if (paconfig.isDebug()) {
                    System.out.println("Unzipping env file");
                }
                File envZip = new File(taskconfig.getEnvZipFileURI());
                if (envZip.exists()) {
                    File envZipFolder = envZip.getParentFile();
                    conn.evalString("unzip('" + envZip + "','" + envZipFolder + "');");

                } else {
                    System.err.println("Error, env zip file cannot be accessed at : " + envZip);
                    throw new IllegalStateException("Error, env zip file cannot be accessed at : " + envZip);
                }
            }
            File envMat = new File(taskconfig.getEnvMatFileURI());
            conn.evalString("load('" + envMat + "');");
        }
        if (paconfig.isTransferVariables()) {
            File inMat = new File(taskconfig.getInputVariablesFileURI());
            conn.evalString("load('" + inMat + "');");
            if (taskconfig.getComposedInputVariablesFileURI() != null) {
                File compinMat = new File(taskconfig.getComposedInputVariablesFileURI());
                conn.evalString("load('" + compinMat + "');in=out;clear out;");
            }
        }
        if (paconfig.isDebug()) {
            conn.evalString("who");
        }

        if (taskconfig.isInputFilesThere() && paconfig.isZipInputFiles()) {
            if (paconfig.isDebug()) {
                System.out.println("Unzipping input files");
            }
            for (URI uri : taskconfig.getInputZipFilesURI()) {
                File inputZip = new File(uri);
                if (inputZip.exists()) {
                    conn.evalString("unzip('" + inputZip + "','" + localSpace + "');");
                } else {
                    System.err.println("Error, input zip file cannot be accessed at : " + inputZip);
                    throw new IllegalStateException("Error, input zip file cannot be accessed at : " +
                        inputZip);
                }
            }

        }

        if (inputScript != null) {
            if (paconfig.isDebug()) {
                System.out.println("Feeding input:");
                //outDebug.println("Feeding input:");
                System.out.println(inputScript);
                //	outDebug.println(inputScript);
            }
            conn.evalString(inputScript);
        }

        String execScript = prepareScript();
        if (paconfig.isDebug()) {
            System.out.println("Executing Matlab command:");
            //outDebug.println("Executing Matlab command:");
            System.out.println(execScript);
            //outDebug.println(execScript);
            System.out.flush();
            //	outDebug.flush();
        }
        conn.evalString(execScript);
        if (paconfig.isDebug()) {
            System.out.println("Matlab command completed successfully");
            //outDebug.println("Matlab command completed successfully");
        }

    }

    /**
     * Appends all the script's lines as a single string
     *
     * @return
     */
    private String prepareScript() {
        String script = "";

        for (String line : scriptLines) {
            script += line;
            script += nl;
        }

        return script;
    }
}