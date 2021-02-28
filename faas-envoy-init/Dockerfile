FROM amazoncorretto:11
COPY target/faas-envoy-init.jar /
CMD java -jar faas-envoy-init.jar "$MANIFEST_FILE" "$ENVOY_CONFIG_FILE"
