/*
 * The MIT License
 *
 * Copyright 2019 Intuit Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.intuit.karate.driver;

import com.intuit.karate.Logger;
import com.intuit.karate.StringUtils;
import com.intuit.karate.shell.Command;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 *
 * @author pthomas3
 */
public class DockerTarget implements Target {

    private Logger logger;

    private final String imageId;
    private String containerId;
    private Function<Integer, String> command;
    private final Map<String, Object> options;
    
    private boolean karateChrome = false;

    public DockerTarget(String dockerImage) {
        this(Collections.singletonMap("docker", dockerImage));
    }

    @Override
    public void setLogger(Logger logger) {
        this.logger = logger;
    }        

    public DockerTarget(Map<String, Object> options) {
        this.options = options;
        if (options != null) {
            imageId = (String) options.get("docker");
            Integer vncPort = (Integer) options.get("vncPort");
            String seccomp = (String) options.get("seccomp");
            StringBuilder sb = new StringBuilder();
            sb.append("docker run -d");
            if (seccomp == null) {
                sb.append(" --cap-add=SYS_ADMIN");
            } else {
                sb.append(" --security-opt seccomp=").append(seccomp);
            }
            if (vncPort != null) {
                sb.append(" -p ").append(vncPort).append(":5900");
            }
            if (imageId != null) {
                if (imageId.startsWith("justinribeiro/chrome-headless")) {
                    command = p -> sb.toString() + " -p " + p + ":9222 " + imageId;
                } else if (imageId.startsWith("ptrthomas/karate-chrome")) {
                    karateChrome = true;
                    command = p -> sb.toString() + " -p " + p + ":9222 " + imageId;
                }
            }
        } else {
            imageId = null;
        }
    }

    public void setCommand(Function<Integer, String> command) {
        this.command = command;
    }

    public Function<Integer, String> getCommand() {
        return command;
    }

    @Override
    public Map<String, Object> start() {
        if (logger == null) {
            logger = new Logger(getClass());
        }
        if (command == null) {
            throw new RuntimeException("docker target command (function) not set");
        }
        if (imageId != null) {
            logger.debug("attempting to pull docker image: {}", imageId);
            Command.execLine(null, "docker pull " + imageId);
        }
        int port = Command.getFreePort();
        containerId = Command.execLine(null, command.apply(port));
        Map<String, Object> map = new HashMap();        
        if (options != null) {
            options.remove("headless");
            options.remove("start");
            map.putAll(options);
        }
        map.put("port", port);
        map.put("type", "chrome");
        Command.waitForHttp("http://127.0.0.1:" + port + "/json");
        return map;
    }

    @Override
    public Map<String, Object> stop() {
        Command.execLine(null, "docker stop " + containerId);
        if (!karateChrome) { // no video
            return Collections.EMPTY_MAP;
        }        
        String shortName = containerId.contains("_") ? containerId : StringUtils.truncate(containerId, 12, false);
        String dirName = "karate-chrome_" + shortName;
        String resultsDir = Command.getBuildDir() + File.separator + dirName;
        Command.execLine(null, "docker cp " + containerId + ":/tmp " + resultsDir);
        String video = resultsDir + File.separator + "karate.mp4";
        File file = new File(video);
        if (!file.exists()) {
            logger.warn("video file missing: {}", file);
            return Collections.EMPTY_MAP;
        }
        return Collections.singletonMap("video", file.getAbsolutePath());
    }

}
