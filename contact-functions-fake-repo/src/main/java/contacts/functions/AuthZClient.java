package contacts.functions;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;

@Slf4j
public class AuthZClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final String AUTHZ_HOST = System.getenv("AUTHZ_HOST");

    @SneakyThrows
    public static void create(Acl acl) {
        if (AUTHZ_HOST == null || AUTHZ_HOST.isEmpty()) {
            log.warn("Environment variable AUTHZ_HOST is not set. Suppressing call to /acl/create.");
            return;
        }

        byte[] responseBody = Request.Post(AUTHZ_HOST + "/acl/create")
            .addHeader("Authorization", "Bearer acl_admin")
            .addHeader("Accept", "application/json")
            .connectTimeout(100)
            .socketTimeout(100)
            .bodyByteArray(MAPPER.writeValueAsBytes(acl), ContentType.APPLICATION_JSON)
            .execute()
            .returnContent().asBytes();

        if (responseBody == null)
            throw new Exception("AuthZ response body is null");
    }

    @SneakyThrows
    public static void delete(Acl acl) {
        if (AUTHZ_HOST == null || AUTHZ_HOST.isEmpty()) {
            log.warn("Environment variable AUTHZ_HOST is not set. Suppressing call to /acl/delete.");
            return;
        }

        log.info("TODO: endpoint {} is not implemented yet", AUTHZ_HOST + "/acl/delete");

//        byte[] responseBody = Request.Post(AUTHZ_HOST + "/acl/delete")
//            .addHeader("Authorization", "Bearer acl_admin")
//            .addHeader("Accept", "application/json")
//            .connectTimeout(100)
//            .socketTimeout(100)
//            .bodyByteArray(MAPPER.writeValueAsBytes(acl), ContentType.APPLICATION_JSON)
//            .execute()
//            .returnContent().asBytes();
//
//        if (responseBody == null)
//            throw new IOException("Response body is null");
    }
}
