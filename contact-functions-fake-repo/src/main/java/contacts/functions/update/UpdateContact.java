package contacts.functions.update;

import lombok.extern.slf4j.Slf4j;

import java.util.function.Function;

@Slf4j
public class UpdateContact implements Function<UpdateContactArgs, UpdateContactResult> {
    @Override
    public UpdateContactResult apply(UpdateContactArgs args) {
        log.info("Update user id={} with name={}", args.getId(), args.getName());

        return new UpdateContactResult(args.getId());
    }
}
