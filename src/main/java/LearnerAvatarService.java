import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

final class LearnerAvatarService {
    enum CourseDelivery { ACTIVE, CLOSED }
    enum DeadlineState { ON_TIME, LATE }
    record AvatarCommand(String userId, String courseId, Instant deadline, byte[] file, String filename) {}
    record AvatarResult(String userId, String courseId, String imageId, DeadlineState deadlineState,
                        boolean educatorReportIncremented) {}

    private final InfraiAvatarClient client;
    private final Clock clock;

    LearnerAvatarService(InfraiAvatarClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    AvatarResult process(AvatarCommand command, CourseDelivery delivery) throws IOException, InterruptedException {
        validate(command, delivery);
        String operation = "avatar-" + UUID.randomUUID();
        String uploaded = client.upload(command.file(), command.filename(), operation + "-upload");
        String cropped = client.smartCrop(uploaded, operation + "-crop");
        String optimized = client.optimize(cropped, operation + "-optimize");
        client.attachToUser(command.userId(), optimized, operation + "-attach");

        DeadlineState state = deadlineState(command.deadline(), clock.instant());
        return new AvatarResult(command.userId(), command.courseId(), optimized, state, state == DeadlineState.LATE);
    }

    static DeadlineState deadlineState(Instant deadline, Instant receivedAt) {
        return receivedAt.isAfter(deadline) ? DeadlineState.LATE : DeadlineState.ON_TIME;
    }

    private static void validate(AvatarCommand command, CourseDelivery delivery) {
        Objects.requireNonNull(command.deadline(), "deadline");
        if (delivery != CourseDelivery.ACTIVE) throw new IllegalArgumentException("Course delivery is closed");
        if (command.file() == null || command.file().length == 0) throw new IllegalArgumentException("Avatar is empty");
        if (command.userId() == null || command.userId().isBlank()) throw new IllegalArgumentException("User id is required");
        if (command.courseId() == null || command.courseId().isBlank()) throw new IllegalArgumentException("Course id is required");
        if (command.filename() == null || command.filename().isBlank()) throw new IllegalArgumentException("Filename is required");
    }
}
