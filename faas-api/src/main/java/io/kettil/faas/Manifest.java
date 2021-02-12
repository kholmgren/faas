package io.kettil.faas;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class Manifest {
    private String name;
    private String location;
    private Map<String, String> environment;
    private AuthManifest authorization;

    @Data
    public static class PathManifest {
        private String handler;
        private AuthManifest authorization;
    }

    @Data
    public static class AuthManifest {
        @JsonProperty("objectid_ptr")
        private String objectIdPtr;

        private Map<String, String> extensions;
    }

    @Data
    public static class StreamManifest {
        private String handler;

        @JsonProperty("in_topic")
        private String inTopic;

        @JsonProperty("in_group")
        private String inGroup;

        @JsonProperty("out_topic")
        private String outTopic;
    }

    private Map<String, PathManifest> paths;
    private List<StreamManifest> streams;
}
