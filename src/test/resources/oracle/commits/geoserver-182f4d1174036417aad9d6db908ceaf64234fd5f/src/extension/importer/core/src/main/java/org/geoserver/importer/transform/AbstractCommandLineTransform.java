/* (c) 2015 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.importer.transform;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.CountingOutputStream;
import org.apache.commons.lang.SystemUtils;
import org.geoserver.data.util.IOUtils;
import org.geoserver.importer.ImportData;
import org.geoserver.importer.ImportTask;

/**
 * Generic file translator getting a set of options, an input file, and an output file
 * 
 * @author Andrea Aime - GeoSolutions
 */
public abstract class AbstractCommandLineTransform extends AbstractTransform implements
        PreTransform {

    private static final long serialVersionUID = 5998049960852782644L;

    static final long DEFAULT_TIMEOUT = 60 * 60 * 1000; // one hour

    List<String> options;

    public AbstractCommandLineTransform(List<String> options) {
        this.options = options;
    }

    /**
     * @return the options
     */
    public List<String> getOptions() {
        return options;
    }

    /**
     * @param options the options to set
     */
    public void setOptions(List<String> options) {
        this.options = options;
    }

    @Override
    public boolean stopOnError(Exception e) {
        return true;
    }

    @Override
    public void apply(ImportTask task, ImportData data) throws Exception {
        boolean inline = isInline();
        File executable = getExecutable();
        File inputFile = getInputFile(data);
        Map<String, File> substitutions = new HashMap<>();
        substitutions.put("input", inputFile);
        File outputDirectory = null;
        File outputFile = null;
        if (!inline) {
            outputDirectory = getOutputDirectory(data);
            outputFile = new File(outputDirectory, inputFile.getName());
            substitutions.put("output", outputFile);
        }


        // setup the options
        CommandLine cmd = new CommandLine(executable);
        cmd.setSubstitutionMap(substitutions);

        setupCommandLine(inline, cmd);

        // prepare to run
        DefaultExecutor executor = new DefaultExecutor();
        // make sure we don't try to execute for too much time
        executor.setWatchdog(new ExecuteWatchdog(DEFAULT_TIMEOUT));

        // grab at least some part of the outputs
        int limit = 16 * 1024;
        try (OutputStream os = new BoundedOutputStream(new ByteArrayOutputStream(), limit);
                OutputStream es = new BoundedOutputStream(new ByteArrayOutputStream(), limit)) {
            PumpStreamHandler streamHandler = new PumpStreamHandler(os, es);
            executor.setStreamHandler(streamHandler);
            int result = executor.execute(cmd);

            if (result != 0) {
                // toString call is routed to ByteArrayOutputStream, which does the right string
                // conversion
                throw new IOException("Failed to execute command " + cmd.toString()
                        + "\nStandard output is:\n" + os.toString() + "\nStandard error is:\n"
                        + es.toString());
            }

        } catch (Exception e) {
            throw new IOException("Failure to execute command " + cmd.toString(), e);
        }

        // if not inline, replace inputs with output
        if (!inline) {
            List<String> names = getReplacementTargetNames(data);
            File inputParent = inputFile.getParentFile();
            for (String name : names) {
                File output = new File(outputDirectory, name);
                File input = new File(inputParent, name);
                if (output.exists()) {
                    // uses atomic rename on *nix, delete and copy on Windows
                    IOUtils.rename(output, input);
                } else if (input.exists()) {
                    input.delete();
                }
            }
            FileUtils.deleteDirectory(outputDirectory);
        }
    }

    protected boolean checkAvailable() throws IOException {
        CommandLine cmd = new CommandLine(getExecutable());
        for (String option : getAvailabilityTestOptions()) {
            cmd.addArgument(option);
        }

        // prepare to run
        DefaultExecutor executor = new DefaultExecutor();

        // grab at least some part of the outputs
        int limit = 16 * 1024;
        try (OutputStream os = new BoundedOutputStream(new ByteArrayOutputStream(), limit);
                OutputStream es = new BoundedOutputStream(new ByteArrayOutputStream(), limit)) {
            PumpStreamHandler streamHandler = new PumpStreamHandler(os, es);
            executor.setStreamHandler(streamHandler);
            int result = executor.execute(cmd);

            if (result != 0) {
                LOGGER.log(Level.SEVERE,
                        "Failed to execute command " + cmd.toString()
                        + "\nStandard output is:\n" + os.toString() + "\nStandard error is:\n"
                        + es.toString());
                return false;
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failure to execute command " + cmd.toString(), e);
            return false;
        }

        return true;
    }

    /**
     * Returns the list of options to be passed the executable to test its availability and ability
     * to run. e.g. "--help"
     * 
     * @return
     */
    protected abstract List<String> getAvailabilityTestOptions();

    protected void setupCommandLine(boolean inline, CommandLine cmd) {
        for (String option : options) {
            cmd.addArgument(option);
        }

        // setup input and output files
        if (inline) {
            cmd.addArgument("${input}");
        } else {

            if (isOutputAfterInput()) {
                cmd.addArgument("${input}");
                cmd.addArgument("${output}");
            } else {
                cmd.addArgument("${output}");
                cmd.addArgument("${input}");
            }
        }
    }

    /**
     * Returns the name of all the files that should be transferred from input to output (sometimes
     * the output is made of several files)
     * 
     * @param data
     * @return
     * @throws IOException
     */
    protected abstract List<String> getReplacementTargetNames(ImportData data) throws IOException;

    /**
     * Returns true if the command line manipulates the input file directly
     * 
     * @return
     */
    protected boolean isInline() {
        return false;
    }

    /**
     * Returns true if in the command line the output file comes after the input one. The default
     * implementation returns true
     * 
     * @return
     */
    protected boolean isOutputAfterInput() {
        return true;
    }

    /**
     * The command input file
     * 
     * @param data
     * @return
     * @throws IOException
     */
    protected abstract File getInputFile(ImportData data) throws IOException;

    /**
     * The directory used for outputs, by default, a subdirectory of the input file parent
     * 
     * @param data
     * @return
     * @throws IOException
     */
    protected File getOutputDirectory(ImportData data) throws IOException {
        File input = getInputFile(data);
        File parent = input.getParentFile();
        File tempFile = File.createTempFile("tmp", null, parent);
        tempFile.delete();
        if (!tempFile.mkdir()) {
            throw new IOException("Could not create work directory " + tempFile.getAbsolutePath());
        }

        return tempFile;
    }

    /**
     * Implementors must provide the executable to be run
     * 
     * @return
     */
    protected abstract File getExecutable() throws IOException;

    /**
     * Locates and executable in the system path. On windows it will automatically append .exe to
     * the searched file name
     * 
     * @param name
     * @return
     * @throws IOException
     */
    protected File getExecutableFromPath(String name) throws IOException {
        if (SystemUtils.IS_OS_WINDOWS) {
            name = name + ".exe";
        }
        String systemPath = System.getenv("PATH");
        if (systemPath == null) {
            systemPath = System.getenv("path");
        }
        if (systemPath == null) {
            throw new IOException("Path is not set, cannot locate " + name);
        }
        String[] paths = systemPath.split(File.pathSeparator);

        for (String pathDir : paths) {
            File file = new File(pathDir, name);
            if (file.exists() && file.isFile() && file.canExecute()) {
                return file;
            }
        }
        throw new IOException(
                "Could not locate executable (or could locate, but does not have execution rights): "
                        + name);
    }

    /**
     * Output stream wrapper with a soft limit
     * 
     * @author Andrea Aime - GeoSolutions
     */
    static final class BoundedOutputStream extends CountingOutputStream {

        private long maxSize;

        private OutputStream delegate;

        public BoundedOutputStream(OutputStream delegate, long maxSize) {
            super(delegate);
            this.delegate = delegate;
            this.maxSize = maxSize;
        }

        @Override
        public void write(byte[] bts) throws IOException {
            if (getByteCount() > maxSize) {
                return;
            }
            super.write(bts);
        }

        @Override
        public void write(byte[] bts, int st, int end) throws IOException {
            if (getByteCount() > maxSize) {
                return;
            }
            super.write(bts, st, end);
        }

        @Override
        public void write(int idx) throws IOException {
            if (getByteCount() > maxSize) {
                return;
            }
            super.write(idx);
        }

        @Override
        public String toString() {
            return delegate.toString();
        }

    }

}
