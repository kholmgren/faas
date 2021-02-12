package contacts.functions.create;

import contacts.functions.Acl;
import contacts.functions.AuthZClient;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;
import java.util.function.Function;

@Slf4j
public class CreateContact implements Function<CreateContactArgs, CreateContactResult> {
    @Override
    public CreateContactResult apply(CreateContactArgs args) {
        log.info("Create user name={}", args.getName());

        String id = UUID.randomUUID().toString();

        String tuple = String.format("%s:%s#%s@%s",
            "contact",
            id,
            "owner",
            "group:contactusers#member");

        AuthZClient.create(Acl.create(tuple));

        return new CreateContactResult(id);
    }
}
