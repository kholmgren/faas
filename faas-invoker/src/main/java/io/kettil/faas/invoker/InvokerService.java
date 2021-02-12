package io.kettil.faas.invoker;

import io.kettil.faas.Manifest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.util.StringUtils.uncapitalize;
import static org.springframework.util.StringUtils.unqualify;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvokerService {
    private final Manifest manifest;
    private final FunctionCatalog catalog;

    @Bean
    public RouterFunction<ServerResponse> routeRequest() {
        return RouterFunctions.route(RequestPredicates.POST("/**"), request -> {
            String authorization = request.headers().asHttpHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            log.info("Authorization: {}", authorization);

            Manifest.PathManifest pathManifest = manifest.getPaths().get(request.uri().getPath());
            if (pathManifest == null)
                return ServerResponse.notFound().build();

            SimpleFunctionRegistry.FunctionInvocationWrapper wrapper =
                catalog.lookup(uncapitalize(unqualify(pathManifest.getHandler())));

            if (wrapper == null)
                return ServerResponse.notFound().build();

            return request.bodyToMono(wrapper.getRawInputType())
                .flatMap(input ->
                    ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(wrapper.apply(input)));
        });
    }
}
