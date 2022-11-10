package io.github.pfwikis.run;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import lombok.Getter;
import lombok.extern.java.Log;

@Log
public class Runner {
    public static byte[] run(Object... command) throws IOException {
        return internalRun(Redirect.INHERIT, null, command);
    }

    public static byte[] runPipeOut(Object... command) throws IOException {
        return internalRun(Redirect.PIPE, null, command);
    }

    public static byte[] runPipeInOut(byte[] in, Object... command) throws IOException {
        return internalRun(Redirect.PIPE, in, command);
    }

    private static byte[] internalRun(Redirect output, byte[] in, Object... command) throws IOException {
        try(var cmd = Command.of(command)) {
            var proc = new ProcessBuilder()
                .redirectError(Redirect.INHERIT)
                .redirectInput(in==null?Redirect.INHERIT:Redirect.PIPE)
                .redirectOutput(output)
                .command(cmd.getParts())
                .start();

            if(in != null) {
                new Thread() {
                    public void run() {
                        try {
                            proc.getOutputStream().write(in);
                            proc.getOutputStream().close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }.start();
            }

            byte[] result = null;
            if(output == Redirect.PIPE) {
                result = IOUtils.toByteArray(proc.getInputStream());
            }
            proc.onExit().join();
            if(proc.exitValue() != 0) {
                throw new RuntimeException("Exited with non-zero code");
            }

            if(cmd.getResultFile() != null) {
                result = FileUtils.readFileToByteArray(cmd.getResultFile());
                FileUtils.delete(cmd.getResultFile());
            }

            return result;
        }
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
        return new File(TMP_DIR, TMP_COUNTER.getAndIncrement()+".geojson");
    }

    public static record TmpGeojson(String commandPrefix, byte[] content){
        public TmpGeojson(byte[] content) {
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

        private final List<File> files = new ArrayList<>();
        private final List<String> parts = new ArrayList<>();
        private File resultFile;

        public static Command of(Object... commandParts) throws IOException {
            var result = new Command();
            result.addCommandParts(commandParts);
            log.info(String.join(" ", result.parts));
            return result;
        }

        private void addCommandParts(Object[] commandParts) throws IOException {
            for(var part : commandParts) {
                if(part instanceof byte[] bytes) {
                    var tmpFile = tmpGeojson();
                    files.add(tmpFile);
                    FileUtils.writeByteArrayToFile(tmpFile, bytes);
                    parts.add(tmpFile.toString());
                }
                else if(part instanceof TmpGeojson json) {
                    var tmpFile = tmpGeojson();
                    files.add(tmpFile);
                    FileUtils.writeByteArrayToFile(tmpFile, json.content());
                    parts.add(json.commandPrefix()+tmpFile.toString());
                }
                else if(part instanceof OutGeojson json) {
                    resultFile = tmpGeojson();
                    parts.add(json.commandPrefix()+resultFile.toString());
                }
                else if(part instanceof String || part instanceof File) {
                    parts.add(Objects.toString(part));
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
                    throw new IllegalStateException("Can't handle "+part.getClass().getName());
                }
            }
        }

        @Override
        public void close() throws IOException {
            for(var f : files) {
                FileUtils.delete(f);
            }
        }
    }
}
