package team.projectpulse.student;

/**
 * UC-STU-deactivate-student extension 3b: the student is already deactivated, so nothing changes.
 * Mapped to {@code 409} / {@code StatusCode.CONFLICT} by {@code ExceptionHandlerAdvice}.
 */
public class StudentAlreadyDeactivatedException extends RuntimeException {

    public StudentAlreadyDeactivatedException() {
        super("The student is already deactivated.");
    }

}
