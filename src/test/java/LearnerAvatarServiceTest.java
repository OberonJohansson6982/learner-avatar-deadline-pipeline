import java.time.Instant;

public final class LearnerAvatarServiceTest {
    public static void main(String[] args) {
        Instant deadline = Instant.parse("2026-09-21T12:00:00Z");
        assertEquals(LearnerAvatarService.DeadlineState.ON_TIME,
                LearnerAvatarService.deadlineState(deadline, Instant.parse("2026-09-21T12:00:00Z")));
        assertEquals(LearnerAvatarService.DeadlineState.LATE,
                LearnerAvatarService.deadlineState(deadline, Instant.parse("2026-09-21T12:00:01Z")));
        System.out.println("PASS: exact deadline is on time; one second later enters the educator report");
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("expected " + expected + " but got " + actual);
    }
}
