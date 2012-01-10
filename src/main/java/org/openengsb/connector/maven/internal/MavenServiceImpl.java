/**
 * Licensed to the Austrian Association for Software Tool Integration (AASTI)
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. The AASTI licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openengsb.connector.maven.internal;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.openengsb.core.api.AliveState;
import org.openengsb.core.api.context.ContextCurrentService;
import org.openengsb.core.api.context.ContextHolder;
import org.openengsb.core.api.model.OpenEngSBFileModel;
import org.openengsb.core.common.AbstractOpenEngSBConnectorService;
import org.openengsb.core.common.util.ModelUtils;
import org.openengsb.domain.build.BuildDomain;
import org.openengsb.domain.build.BuildDomainEvents;
import org.openengsb.domain.build.BuildFailEvent;
import org.openengsb.domain.build.BuildStartEvent;
import org.openengsb.domain.build.BuildSuccessEvent;
import org.openengsb.domain.deploy.DeployDomain;
import org.openengsb.domain.deploy.DeployDomainEvents;
import org.openengsb.domain.deploy.DeployFailEvent;
import org.openengsb.domain.deploy.DeployStartEvent;
import org.openengsb.domain.deploy.DeploySuccessEvent;
import org.openengsb.domain.test.TestDomain;
import org.openengsb.domain.test.TestDomainEvents;
import org.openengsb.domain.test.TestFailEvent;
import org.openengsb.domain.test.TestStartEvent;
import org.openengsb.domain.test.TestSuccessEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MavenServiceImpl extends AbstractOpenEngSBConnectorService implements BuildDomain, TestDomain, DeployDomain {

    private static final Logger LOGGER = LoggerFactory.getLogger(MavenServiceImpl.class);

    private static final int MAX_LOG_FILES = 5;

    private String mvnVersion = "";
    private String mvnCommand;

    private BuildDomainEvents buildEvents;
    private TestDomainEvents testEvents;
    private DeployDomainEvents deployEvents;

    private Executor executor = Executors.newSingleThreadExecutor();
    private ExecutorService outputReaderPool = Executors.newCachedThreadPool();

    private boolean synchronous = false;

    private boolean useLogFile = true;

    private ContextCurrentService contextService;

    private String command;
    private File logDir;

    public MavenServiceImpl(String id) {
        super(id);
        String karafData = System.getProperty("karaf.data");
        logDir = new File(karafData, "log");
        if (!logDir.exists()) {
            logDir.mkdir();
        } else if (!logDir.isDirectory()) {
            throw new IllegalStateException("cannot access log-directory");
        }

        if (!mvnVersion.isEmpty()) {
            if (!isMavenInstalled()) {
                try {
                    installMaven();
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        } else {
            mvnCommand = "mvn" + addSystemEnding();
        }
    }

    public Boolean isMavenInstalled() {
        return new File(System.getProperty("karaf.data"), "apache-maven-" + mvnVersion).exists();
    }


    public void installMaven() throws IOException {
        File tmp = File.createTempFile("mvn_setup", "zip");
        initializeDownload(tmp);
        unzipFile(tmp.getAbsolutePath(), System.getProperty("karaf.data"));
    }

    private void initializeDownload(File tmp) {
        String baseUrl = "http://repo1.maven.org/maven2/org/apache/maven/apache-maven";
        String fullUrl = String.format("%s/%s/apache-maven-%s-bin.zip", baseUrl, mvnVersion, mvnVersion);
        if (!download(fullUrl, tmp)) {
            throw new IllegalStateException(
                String.format("Maven download not possible, because %s does not answer", fullUrl));
        }
    }

    public boolean download(String urlString, File tmp) {
        try {
            URL url = new URL(urlString);
            FileUtils.copyURLToFile(url, tmp);
        } catch (IOException e) {
            LOGGER.error("could not download maven from mirror " + urlString, e);
            return false;
        }
        return true;
    }

    public void unzipFile(String archivePath, String targetPath)
            throws IOException {
        File archiveFile = new File(archivePath);
        File targetFile = new File(targetPath);
        ZipFile zipFile = new ZipFile(archiveFile);
        Enumeration<?> e = zipFile.getEntries();
        while (e.hasMoreElements()) {
            ZipArchiveEntry zipEntry = (ZipArchiveEntry) e.nextElement();
            File file = new File(targetFile, zipEntry.getName());
            if (zipEntry.isDirectory()) {
                FileUtils.forceMkdir(file);
            } else {
                InputStream is = zipFile.getInputStream(zipEntry);
                FileOutputStream os = FileUtils.openOutputStream(file);
                try {
                    IOUtils.copy(is, os);
                } finally {
                    os.close();
                    is.close();
                }
            }
        }
        zipFile.close();
    }

    private static String addSystemEnding() {
        if (System.getProperty("os.name").contains("Windows")) {
            return ".bat";
        }
        return "";
    }

    @Override
    public AliveState getAliveState() {
        return AliveState.ONLINE;
    }

    @Override
    public String runTests(final OpenEngSBFileModel path) {
        final String id = createId();
        final String contextId = ContextHolder.get().getCurrentContextId();
        Runnable runTests = new Runnable() {

            @Override
            public void run() {
                ContextHolder.get().setCurrentContextId(contextId);
                MavenResult result = excuteCommand(command, path.getFile());
                testEvents.raiseEvent(new TestStartEvent(id));
                if (result.isSuccess()) {
                    OpenEngSBFileModel outPath = ModelUtils.createEmptyModelObject(OpenEngSBFileModel.class);
                    outPath.setFile(path.getFile());
                    testEvents.raiseEvent(new TestSuccessEvent(id, result
                            .getOutput(), outPath));
                } else {
                    testEvents.raiseEvent(new TestFailEvent(id, result
                            .getOutput()));
                }
            }
        };
        execute(runTests);
        return id;
    }

    @Override
    public void runTests(final OpenEngSBFileModel path, final long processId) {
        final String contextId = ContextHolder.get().getCurrentContextId();
        Runnable runTests = new Runnable() {
            @Override
            public void run() {
                ContextHolder.get().setCurrentContextId(contextId);
                MavenResult result = excuteCommand(command, path.getFile());
                testEvents.raiseEvent(new TestStartEvent(processId));
                if (result.isSuccess()) {
                    OpenEngSBFileModel outPath = ModelUtils.createEmptyModelObject(OpenEngSBFileModel.class);
                    outPath.setFile(path.getFile());
                    testEvents.raiseEvent(new TestSuccessEvent(processId,
                            result.getOutput(), outPath));
                } else {
                    testEvents.raiseEvent(new TestFailEvent(processId, result
                            .getOutput()));
                }
            }
        };
        execute(runTests);
    }

    @Override
    public String build(final OpenEngSBFileModel path) {
        final String id = createId();
        final String contextId = ContextHolder.get().getCurrentContextId();
        Runnable doBuild = new Runnable() {
            @Override
            public void run() {
                ContextHolder.get().setCurrentContextId(contextId);
                MavenResult result = excuteCommand(command, path.getFile());
                buildEvents.raiseEvent(new BuildStartEvent(id));
                if (result.isSuccess()) {
                    OpenEngSBFileModel outPath = ModelUtils.createEmptyModelObject(OpenEngSBFileModel.class);
                    outPath.setFile(path.getFile());
                    buildEvents.raiseEvent(new BuildSuccessEvent(id, result
                            .getOutput(), outPath));
                } else {
                    buildEvents.raiseEvent(new BuildFailEvent(id, result
                            .getOutput()));
                }
            }
        };
        execute(doBuild);
        return id;
    }

    @Override
    public void build(final OpenEngSBFileModel path, final long processId) {
        final String contextId = ContextHolder.get().getCurrentContextId();
        Runnable doBuild = new Runnable() {
            @Override
            public void run() {
                ContextHolder.get().setCurrentContextId(contextId);
                MavenResult result = excuteCommand(command, path.getFile());
                BuildStartEvent buildStartEvent = new BuildStartEvent();
                buildStartEvent.setProcessId(processId);
                buildEvents.raiseEvent(buildStartEvent);
                if (result.isSuccess()) {
                    OpenEngSBFileModel outPath = ModelUtils.createEmptyModelObject(OpenEngSBFileModel.class);
                    outPath.setFile(path.getFile());
                    buildEvents.raiseEvent(new BuildSuccessEvent(processId,
                            result.getOutput(), outPath));
                } else {
                    buildEvents.raiseEvent(new BuildFailEvent(processId, result
                            .getOutput()));
                }
            }
        };
        execute(doBuild);

    }

    private void execute(Runnable runnable) {
        if (synchronous) {
            runnable.run();
        } else {
            executor.execute(runnable);
        }
    }

    @Override
    public String deploy(final OpenEngSBFileModel path) {
        final String id = createId();
        final String contextId = ContextHolder.get().getCurrentContextId();
        Runnable doDeploy = new Runnable() {

            @Override
            public void run() {
                ContextHolder.get().setCurrentContextId(contextId);
                MavenResult result = excuteCommand(command, path.getFile());
                deployEvents.raiseEvent(new DeployStartEvent(id));
                if (result.isSuccess()) {
                    deployEvents.raiseEvent(new DeploySuccessEvent(id, result
                            .getOutput()));
                } else {
                    deployEvents.raiseEvent(new DeployFailEvent(id, result
                            .getOutput()));
                }
            }
        };
        execute(doDeploy);
        return id;
    }

    @Override
    public void deploy(final OpenEngSBFileModel path, final long processId) {
        final String contextId = ContextHolder.get().getCurrentContextId();
        Runnable doDeploy = new Runnable() {
            @Override
            public void run() {
                ContextHolder.get().setCurrentContextId(contextId);
                MavenResult result = excuteCommand(command, path.getFile());
                deployEvents.raiseEvent(new DeployStartEvent(processId));
                if (result.isSuccess()) {
                    deployEvents.raiseEvent(new DeploySuccessEvent(processId,
                            result.getOutput()));
                } else {
                    deployEvents.raiseEvent(new DeployFailEvent(processId,
                            result.getOutput()));
                }

            }
        };
        execute(doDeploy);
    }

    private String createId() {
        return UUID.randomUUID().toString();
    }

    private synchronized MavenResult excuteCommand(String goal, File dir) {
        List<String> command = new ArrayList<String>();
        command.add(mvnCommand);
        command.addAll(Arrays.asList(goal.trim().split(" ")));

        try {
            return runMaven(dir, command);
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
            return new MavenResult(false, e.getMessage());
        } catch (InterruptedException e) {
            LOGGER.error(e.getMessage(), e);
            return new MavenResult(false, e.getMessage());
        }
    }

    private MavenResult runMaven(File dir, List<String> command)
            throws IOException, InterruptedException {
        LOGGER.info("running '{}' in directory '{}'", command, dir.getPath());
        Process process = configureProcess(dir, command);
        Future<String> outputFuture = configureProcessOutputReader(process);
        Future<String> errorFuture = configureProcessErrorReader(process);
        boolean processResultCode = process.waitFor() == 0;
        String outputResult = readResultFromFuture(outputFuture);
        String errorResult = readResultFromFuture(errorFuture);
        if (!errorResult.isEmpty()) {
            LOGGER.warn("Maven connector error stream output: {}", errorResult);
        }
        LOGGER.info("maven exited with status {}", processResultCode);
        return new MavenResult(processResultCode, outputResult);
    }

    private Process configureProcess(File dir, List<String> command)
            throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        Process process = builder.directory(dir).start();
        return process;
    }

    private Future<String> configureProcessErrorReader(Process process) {
        ProcessOutputReader error = new ProcessOutputReader(
                process.getErrorStream());
        return outputReaderPool.submit(error);
    }

    private Future<String> configureProcessOutputReader(Process process)
            throws IOException {
        ProcessOutputReader output;
        if (useLogFile) {
            File logFile = getNewLogFile();
            output = new ProcessOutputReader(process.getInputStream(), logFile);
        } else {
            output = new ProcessOutputReader(process.getInputStream());
        }
        return outputReaderPool.submit(output);
    }

    private String readResultFromFuture(Future<String> future)
            throws InterruptedException {
        String result;
        try {
            result = future.get();
        } catch (ExecutionException e) {
            LOGGER.error(e.getMessage(), e.getCause());
            result = ExceptionUtils.getFullStackTrace(e);
        }
        return result;
    }

    private File getNewLogFile() throws IOException {
        if (logDir.list().length + 1 > MAX_LOG_FILES) {
            assertLogLimit();
        }
        String dateString = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss")
                .format(new Date());
        String fileName = String.format("maven.%s.log", dateString);
        File logFile = new File(logDir, fileName);
        logFile.createNewFile();
        return logFile;
    }

    private boolean assertLogLimit() {
        File[] logFiles = logDir.listFiles();
        Arrays.sort(logFiles, new Comparator<File>() {
            @Override
            public int compare(File f1, File f2) {
                return Long.valueOf(f1.lastModified()).compareTo(
                        f2.lastModified());
            }
        });
        return logFiles[0].delete();
    }

    public void setMvnVersion(String mvnVersion) {
        String tmpMvnVersion = this.mvnVersion;
        this.mvnVersion = mvnVersion;
        try {
            installMaven();
        } catch (IOException e) {
            this.mvnVersion = tmpMvnVersion;
            throw new RuntimeException(e);
        }
        mvnCommand = System.getProperty("karaf.data") + "/apache-maven-"
                + mvnVersion + "/bin/mvn" + addSystemEnding();
        new File(mvnCommand).setExecutable(true);
    }

    public void setBuildEvents(BuildDomainEvents buildEvents) {
        this.buildEvents = buildEvents;
    }

    public void setTestEvents(TestDomainEvents testEvents) {
        this.testEvents = testEvents;
    }

    public void setDeployEvents(DeployDomainEvents deployEvents) {
        this.deployEvents = deployEvents;
    }

    public void setContextService(ContextCurrentService contextService) {
        this.contextService = contextService;
    }

    protected BuildDomainEvents getBuildEvents() {
        return buildEvents;
    }

    protected TestDomainEvents getTestEvents() {
        return testEvents;
    }

    protected DeployDomainEvents getDeployEvents() {
        return deployEvents;
    }

    protected ContextCurrentService getContextService() {
        return contextService;
    }

    public void setSynchronous(boolean synchronous) {
        this.synchronous = synchronous;
    }

    public boolean isSynchronous() {
        return synchronous;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public void setUseLogFile(boolean useLogFile) {
        this.useLogFile = useLogFile;
    }

    public int getLogLimit() {
        return MAX_LOG_FILES;
    }

    private class MavenResult {
        private String output;

        private boolean success;

        public MavenResult(boolean success, String output) {
            this.success = success;
            this.output = output;
        }

        public String getOutput() {
            return output;
        }

        public boolean isSuccess() {
            return success;
        }
    }

}
