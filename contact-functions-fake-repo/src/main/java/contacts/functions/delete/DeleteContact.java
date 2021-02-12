package contacts.functions.delete;

import contacts.functions.Acl;
import contacts.functions.AuthZClient;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Function;

@Slf4j
public class DeleteContact implements Function<DeleteContactArgs, DeleteContactResult> {
    @SneakyThrows
    @Override
    public DeleteContactResult apply(DeleteContactArgs args) {
        log.info("Delete user id={}", args.getId());

        String tuple = String.format("%s:%s#%s@%s",
            "contact",
            args.getId(),
            "owner",
            "group:contactusers#member");

        AuthZClient.delete(Acl.create(tuple));

        return new DeleteContactResult(args.getId());
    }
}
