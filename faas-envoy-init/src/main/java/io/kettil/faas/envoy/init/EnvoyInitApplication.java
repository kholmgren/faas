package io.kettil.faas.envoy.init;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.kettil.faas.Manifest;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

@Slf4j
public class EnvoyInitApplication implements Callable<Integer> {
    @Option(names = {"-h", "--help"}, usageHelp = true, description = "display a help message")
    private boolean helpRequested = false;

    @Parameters(index = "0", description = "input manifest file")
    private File manifestFile;

    @Parameters(index = "1", description = "output envoy config file")
    private File envoyConfigFile;

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static void main(String[] args) {
        int exitCode = new picocli.CommandLine(new EnvoyInitApplication())
                .execute(args);

        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        if (!manifestFile.exists()) {
            System.err.println("Manifest file does not exist: " + manifestFile);
            return 1;
        }

        log.info("Using manifest file {}", manifestFile);

        Manifest manifest = yamlMapper.readValue(manifestFile, Manifest.class);

        ObjectNode envoy = yamlMapper.readValue(
                getClass().getClassLoader().getResourceAsStream("envoy.yml"),
                ObjectNode.class);

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

        yamlMapper.writerWithDefaultPrettyPrinter()
                .writeValue(envoyConfigFile, envoy);

        log.info("Built envoy config file {}", envoyConfigFile);

        return 0;
    }
}
