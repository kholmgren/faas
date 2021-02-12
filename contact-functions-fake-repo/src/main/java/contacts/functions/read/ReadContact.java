package contacts.functions.read;

import lombok.extern.slf4j.Slf4j;

import java.util.function.Function;

@Slf4j
public class ReadContact implements Function<ReadContactArgs, ReadContactResult> {
    @Override
    public ReadContactResult apply(ReadContactArgs args) {
        log.info("Read user id={}", args.getId());

        return new ReadContactResult(args.getId());
    }
}
