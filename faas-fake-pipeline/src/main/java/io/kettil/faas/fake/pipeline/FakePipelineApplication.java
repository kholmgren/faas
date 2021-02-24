package io.kettil.faas.fake.pipeline;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.kettil.faas.Manifest;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.joining;

@Slf4j
@Data
public class FakePipelineApplication implements Callable<Integer> {
    private static final String INVOKER_BASE_IMAGE = "faas-invoker:latest";

    private static final String ENVOY_BASE_IMAGE = "envoyproxy/envoy-dev:latest";

    public static final String SERVICE_IMAGE_NAME_FORMAT = "%1$s-service:latest";

    public static final String ENVOY_IMAGE_NAME_FORMAT = "%1$s-envoy:latest";

    @picocli.CommandLine.Option(names = { "-h", "--help" }, usageHelp = true, description = "display a help message")
    private boolean helpRequested = false;

    @picocli.CommandLine.Parameters(index = "0", description = "Function repo dir")
    private File repo;

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static void main(String[] args) {
        int exitCode = new picocli.CommandLine(new FakePipelineApplication()).execute(args);
        System.exit(exitCode);
    }

    private final CountDownLatch latch = new CountDownLatch(1);

    @Override
    public Integer call() throws Exception {
        Path manifestPath = repo.toPath().resolve("manifest.yml");
        if (Files.notExists(manifestPath)) {
            System.err.println("Manifest file does not exist: " + manifestPath);
            return 1;
        }

        log.info("Using manifest file {}", manifestPath);

        Manifest manifest = yamlMapper.readValue(manifestPath.toFile(), Manifest.class);

        Path artifact = manifestPath.getParent().resolve(manifest.getLocation());
        if (Files.notExists(artifact)) {
            log.info("Building Maven project in {}", manifestPath.getParent());
            int exitCode = buildMvnProject(manifestPath.getParent());
            if (exitCode != 0)
                return exitCode;

            if (Files.notExists(artifact)) {
                System.err.println("Location path in manifest did not build: " + manifest.getLocation());
                return 1;
            }
        }

        Path buildDirectory = Files.createTempDirectory("faas");
        openVisualCode(buildDirectory); //TODO; comment out -- just a simple way to show the generated output

        Path serviceBuildDirectory = Files.createDirectory(buildDirectory.resolve("service"));
        log.info("Service build directory: {}", serviceBuildDirectory);

        Path envoyBuildDirectory = Files.createDirectory(buildDirectory.resolve("envoy"));
        log.info("Envoy build directory: {}", envoyBuildDirectory);

        Path serviceSrcDirectory = Files.createDirectory(serviceBuildDirectory.resolve("src"));

        log.info("Preparing files to copy into the service docker image");

        String handlersJarFileName = "handlers.jar";
        Files.copy(artifact, serviceSrcDirectory.resolve(handlersJarFileName));
        log.info("Copied {} to {}", artifact, serviceSrcDirectory.resolve(handlersJarFileName));

        String manifestFileName = "manifest.yml";
        Files.copy(manifestPath, serviceSrcDirectory.resolve(manifestFileName));
        log.info("Copied {} to {}", manifestPath, serviceSrcDirectory.resolve(manifestFileName));

        String propertiesFileName = "faas-invoker.properties";
        log.info("Creating {}", serviceSrcDirectory.resolve(propertiesFileName));
        try (PrintWriter w = new PrintWriter(serviceSrcDirectory.resolve(propertiesFileName).toFile())) {
            w.printf("manifest=/app/%s%n", manifestFileName);
            w.printf("spring.cloud.function.location=/app/%s%n", handlersJarFileName);

            w.printf("spring.cloud.function.function-class=%s%n",
                manifest.getPaths().values().stream()
                    .map(Manifest.PathManifest::getHandler)
                    .collect(joining(";")));
        }

        buildServiceDockerfile(serviceBuildDirectory, serviceSrcDirectory);

        String serviceImageName = String.format(SERVICE_IMAGE_NAME_FORMAT, manifest.getName());

        log.info("Building service docker image {} using build dir {}", serviceImageName, serviceBuildDirectory);
        int exitCode = buildDockerImage(serviceBuildDirectory, serviceImageName);
        if (exitCode != 0)
            return exitCode;
        exitCode = pushDockerImage(serviceImageName);
        if (exitCode != 0)
            return exitCode;

        Path envoyConfigFile = envoyBuildDirectory.resolve("envoy.yml");
        buildEnvoyConfig(manifest, envoyConfigFile.toFile());
        buildEnvoyDockerfile(envoyBuildDirectory, envoyConfigFile);

        String envoyImageName = String.format(ENVOY_IMAGE_NAME_FORMAT, manifest.getName());
        log.info("Building envoy docker image {} using build dir {}", envoyImageName, envoyBuildDirectory);
        exitCode = buildDockerImage(envoyBuildDirectory, envoyImageName);
        if (exitCode != 0)
            return exitCode;
        exitCode = pushDockerImage(envoyImageName);
        if (exitCode != 0)
            return exitCode;

        String envArgs = manifest.getEnvironment().entrySet().stream()
            .map(i -> "-e " + i.getKey() + "=" + i.getValue())
            .collect(joining(" "));

        String dockerMachineIp;
        try {
            dockerMachineIp = getDockerMachineIp();
        } catch (Exception e) {
            dockerMachineIp = getLocalMachineIp();
        }

        Path dockerComposeFile = Path.of("docker-compose.yml");
        buildDockerComposeFile(manifest, dockerMachineIp, dockerComposeFile);

        System.out.printf("%n%nTo start, run:%n  docker-compose -f %s up%n%n", dockerComposeFile.toAbsolutePath().normalize());

        return 0;
    }

    @SneakyThrows
    private void buildDockerComposeFile(Manifest manifest, String dockerMachineIp, Path dockerComposeFile) {
        try (PrintWriter w = new PrintWriter(dockerComposeFile.toFile())) {
            w.println("version: '2'");
            w.println("services:");
            w.println("  envoy:");
            w.println("    image: contact-functions-envoy:latest");
            w.println("    extra_hosts:");
            w.printf("      - \"authz_host:%s\"%n", dockerMachineIp);
            w.printf("      - \"service_host:%s\"%n", dockerMachineIp);
            w.println("    ports:");
            w.println("      - \"18000:18000\"");
            w.println("      - \"8001:8001\"");
            w.println();
            w.println("  service:");
            w.println("    image: contact-functions-service:latest");
            w.println("    environment:");

            for (Map.Entry<String, String> i : manifest.getEnvironment().entrySet()) {
                w.printf("      - %s=%s%n", i.getKey(), i.getValue());
            }

            w.println("    extra_hosts:");
            w.printf("      - \"authz_host:%s\"%n", dockerMachineIp);
            w.println("    ports:");
            w.println("      - \"8002:8002\"");
        }
    }

    private int buildMvnProject(Path dir) throws IOException {
        CommandLine cmdLine = new CommandLine("mvn").addArgument("clean").addArgument("package");
        DefaultExecutor executor = new DefaultExecutor();
        executor.setWorkingDirectory(dir.toFile());
        return executor.execute(cmdLine);
    }

    @SneakyThrows
    private void buildServiceDockerfile(Path buildDirectory, Path srcDirectory) {
        String dockerFileName = "Dockerfile";
        log.info("Creating {}", dockerFileName);
        try (PrintWriter w = new PrintWriter(buildDirectory.resolve(dockerFileName).toFile())) {
            w.printf("FROM %s%n", INVOKER_BASE_IMAGE);
            w.printf("COPY %1$s/* /app/%n", srcDirectory.getFileName());
        }
    }

    @SneakyThrows
    private void buildEnvoyDockerfile(Path buildDirectory, Path envoyConfigFile) {
        String dockerFileName = "Dockerfile";
        log.info("Creating {}", dockerFileName);
        try (PrintWriter w = new PrintWriter(buildDirectory.resolve(dockerFileName).toFile())) {
            w.printf("FROM %s%n", ENVOY_BASE_IMAGE);
            w.printf("COPY ./%s /envoy.yaml%n", envoyConfigFile.getFileName()); //Using the 'yaml' extension instead of 'yml' is important

            //
            //            //-l <string>,  --log-level <string>
            //            //    Log levels: [trace][debug][info][warning
            //            //    |warn][error][critical][off]
            //
            w.println("CMD [\"/usr/local/bin/envoy\", \"-l\", \"debug\", \"-c\", \"/envoy.yaml\", \"--service-cluster\", \"front-proxy\"]");
        }
    }

    private int openVisualCode(Path dir) throws IOException {
        CommandLine cmdLine = new CommandLine("code").addArgument(dir.toAbsolutePath().normalize().toString());

        Executor executor = new DefaultExecutor();
        executor.setWorkingDirectory(dir.toFile());
        return executor.execute(cmdLine);
    }

    private int buildDockerImage(Path dir, String imageName) throws IOException {
        CommandLine cmdLine = new CommandLine("docker")
            .addArgument("build")
            .addArgument("-t")
            .addArgument(imageName)
            .addArgument(".");

        Executor executor = new DefaultExecutor();
        executor.setWorkingDirectory(dir.toFile());
        return executor.execute(cmdLine);
    }

    private int pushDockerImage(String imageName) throws IOException {
        CommandLine cmdLine = new CommandLine("docker").addArgument("push").addArgument(imageName);

        Executor executor = new DefaultExecutor();
        return executor.execute(cmdLine);
    }

    @SneakyThrows
    private void buildEnvoyConfig(Manifest manifest, File envoyFile) {
        ObjectNode envoy = yamlMapper.readValue(getClass().getClassLoader().getResourceAsStream("envoy.yml"), ObjectNode.class);

        ArrayNode routes = (ArrayNode) envoy.at("/static_resources/listeners/0/filter_chains/0/filters/0/typed_config/route_config/virtual_hosts/0/routes");

        ObjectNode servicePrototype = (ObjectNode) routes.remove(0);
        ObjectNode aclNode = (ObjectNode) routes.get(0);

        for (Map.Entry<String, Manifest.PathManifest> i : manifest.getPaths().entrySet()) {
            ObjectNode route = servicePrototype.deepCopy();

            ObjectNode match = (ObjectNode) route.get("match");
            match.remove("prefix");
            match.put("path", i.getKey());

            ObjectNode contextExtensions = (ObjectNode) route.at("/typed_per_filter_config/envoy.filters.http.ext_authz/check_settings/context_extensions");
            contextExtensions.put("service_path", i.getKey());

            String objectIdPtr = i.getValue().getAuthorization().getObjectIdPtr();
            if (objectIdPtr != null)
                contextExtensions.put("objectid_ptr", objectIdPtr);

            LinkedHashMap<String, String> materializedExtensions = new LinkedHashMap<>(manifest.getAuthorization().getExtensions());
            materializedExtensions.putAll(i.getValue().getAuthorization().getExtensions());

            for (Map.Entry<String, String> j : materializedExtensions.entrySet()) {
                contextExtensions.put(j.getKey(), j.getValue());
            }

            routes.add(route);
        }

        yamlMapper.writerWithDefaultPrettyPrinter().writeValue(envoyFile, envoy);
    }

    @SneakyThrows
    private String run(String command) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        CommandLine commandline = CommandLine.parse(command);
        DefaultExecutor exec = new DefaultExecutor();
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
        exec.setStreamHandler(streamHandler);
        exec.execute(commandline);
        return outputStream.toString();
    }

    public static final Pattern INET_PATTERN = Pattern.compile("inet\\s+([\\d.]+)");

    private String getDockerMachineIp() {
        Matcher matcher = INET_PATTERN.matcher(run("ip addr show docker0")); //Ubuntu

        return matcher.find() ? matcher.group(1) : null;
    }

    private String getLocalMachineIp() {
        Matcher matcher = INET_PATTERN.matcher(run("ip addr show eth0")); //RHEL

        return matcher.find() ? matcher.group(1) : null;
    }
}
