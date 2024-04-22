package io.github.pfwikis.run;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Runner {
    /*package*/ static LCContent run(String command, Object... args) throws IOException {
        return internalRun(Redirect.INHERIT, null, command, args);
    }

    /*package*/ static LCContent runPipeOut(String command, Object... args) throws IOException {
        return internalRun(Redirect.PIPE, null, command, args);
    }

    /*package*/ static LCContent runPipeInOut(LCContent in, String command, Object... args) throws IOException {
        return internalRun(Redirect.PIPE, in, command, args);
    }
    
    private static LCContent internalRun(Redirect output, LCContent in, String command, Object... args) throws IOException {
    	var out = new ByteArrayOutputStream();
    	var error = new ByteArrayOutputStream();
        try(var cmd = Command.of(command, args)) {
        	var cmdl = new CommandLine(cmd.getParts().get(0));
        	cmdl.addArguments(
    			cmd.getParts().subList(1, cmd.getParts().size()).toArray(String[]::new),
    			false
        	);
        	DefaultExecuteResultHandler resultHandler = new DefaultExecuteResultHandler();

        	Executor executor = DefaultExecutor.builder().get();
        	executor.setExitValue(0);
        	if(in != null)
        		executor.setStreamHandler(new PumpStreamHandler(out, error, in.toInputStream()));
        	else
        		executor.setStreamHandler(new PumpStreamHandler(out, error));
        	executor.execute(cmdl, resultHandler);
        	resultHandler.waitFor();
        	
            if(resultHandler.getExitValue() != 0) {
                throw new RuntimeException("Exited command "+cmd.getParts()+" with non-zero code: "+resultHandler.getExitValue());
            }
            LCContent result = LCContent.empty();
            if(output == Redirect.PIPE) {
                result = LCContent.from(out.toByteArray());
            }
            else {
            	log(Level.INFO, out.toByteArray());
            	log(Level.SEVERE, error.toByteArray());
            }

            if(cmd.getResultFile() != null) {
                result = LCContent.from(cmd.getResultFile(), true);
            }

            return result;
        } catch(Exception e) {
            if(in != null) {
                var str = in.toJSONString();
                log.error("Failure for input "+str.substring(0, Math.min(1000, str.length())));
                var tmp = Files.createTempFile("pf-mapping-debug", ".json");
                Files.write(tmp, in.toBytes());
                log.error("Full input in "+tmp.toAbsolutePath());
            }
            log(Level.INFO, out.toByteArray());
        	log(Level.SEVERE, error.toByteArray());
            throw new IOException(e);
        }
    }

    private static void log(Level level, byte[] out) {
        String msg = new String(out, StandardCharsets.UTF_8);
        if(StringUtils.isEmpty(msg)) return;
        log.error("std/out: "+msg);
    }

    public static final File TMP_DIR;
    static {
        try {
            TMP_DIR = Files.createTempDirectory("pathfinder-mapping-").toFile();
            FileUtils.forceDeleteOnExit(TMP_DIR);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final AtomicInteger TMP_COUNTER = new AtomicInteger();

    public static File tmpGeojson() {
        var f = new File(TMP_DIR, TMP_COUNTER.getAndIncrement()+".geojson");
        f.deleteOnExit();
        return f;
    }

    public static record TmpGeojson(String commandPrefix, LCContent content){
        public TmpGeojson(LCContent content) {
            this("", content);
        }
    }
    public static record OutGeojson(String commandPrefix){
        public OutGeojson() {
            this("");
        }
    }

    @Getter
    private static class Command implements Closeable {

        private final List<String> parts = new ArrayList<>();
        private File resultFile;
        private ToolVariant toolVariant;

        public static Command of(String command, Object... commandParts) throws IOException {
            var result = new Command();
            result.toolVariant = ToolVariant.getFor(command);
            result.addCommandParts(new String[] {command});
            result.addCommandParts(commandParts);
            result.toolVariant.modifyArguments(result.parts);
            log.info(String.join(" ", result.parts));
            return result;
        }

        private void addCommandParts(Object[] commandParts) throws IOException {
            for(var part : commandParts) {
            	Objects.requireNonNull(part, ()->"null Argument when executing "+Arrays.toString(commandParts));
                if(part instanceof LCContent content) {
                	parts.add(toolVariant.translateFile(content.toTmpFile()));
                }
                else if(part instanceof TmpGeojson json) {
                    parts.add(json.commandPrefix()+toolVariant.translateFile(json.content().toTmpFile()));
                }
                else if(part instanceof OutGeojson json) {
                    resultFile = tmpGeojson();
                    parts.add(json.commandPrefix()+toolVariant.translateFile(resultFile.toPath()));
                }
                else if(part instanceof String v) {
                	parts.add(v.replace("\n", ""));
                }
                else if(part instanceof File f) {
                	parts.add(toolVariant.translateFile(f.toPath()));
                }
                else if(part instanceof Path p) {
                	parts.add(toolVariant.translateFile(p));
                }
                else if(part instanceof String[] arr) {
                    addCommandParts(arr);
                }
                else if(part instanceof Object[] arr) {
                    addCommandParts(arr);
                }
                else if(part instanceof Collection<?> col) {
                    addCommandParts(col.toArray(Object[]::new));
                }
                else {
                    throw new IllegalStateException("Can't handle command part "+part.getClass().getName());
                }
            }
        }

        @Override
        public void close() throws IOException {
        }
    }
}
