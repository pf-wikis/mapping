package io.github.pfwikis.run;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

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
        var baos = new ByteArrayOutputStream();
        try(var cmd = Command.of(command)) {
            Process proc = new ProcessBuilder()
                .redirectError(Redirect.INHERIT)
                .redirectInput(in==null?Redirect.INHERIT:Redirect.PIPE)
                .redirectOutput(Redirect.PIPE)
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
            var outThread=new Thread() {
                public void run() {
                    try {
                        int v;
                        while((v=proc.getInputStream().read())!=-1) {
                            baos.write(v);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            };
            outThread.start();

            proc.onExit().join();
            outThread.join();
            if(proc.exitValue() != 0) {
                throw new RuntimeException("Exited with non-zero code");
            }
            byte[] result = new byte[0];
            if(output == Redirect.PIPE) {
                result = baos.toByteArray();
            }
            else {
                log(Level.SEVERE, baos.toByteArray());
            }

            if(cmd.getResultFile() != null) {
                result = FileUtils.readFileToByteArray(cmd.getResultFile());
                FileUtils.delete(cmd.getResultFile());
            }

            return result;
        } catch(Exception e) {
            if(in != null) {
                var str = new String(in);
                log.severe("Failure for input "+str.substring(0, Math.min(1000, str.length())));
            }
            log(Level.SEVERE, baos.toByteArray());

            throw new IOException(e);
        }
    }

    private static void log(Level level, byte[] out) {
        String msg = new String(out, StandardCharsets.UTF_8);
        if(StringUtils.isEmpty(msg)) return;
        log.log(level, "std/out: "+msg);
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
