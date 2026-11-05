package team.projectpulse.security.authorizationmanagers;

import org.jspecify.annotations.Nullable;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import team.projectpulse.student.StudentSecurityService;

import java.util.function.Supplier;

/**
 * Guards changing a student's account status (UC-STU-deactivate-student), admitting an instructor assigned to the
 * student's course section or the course admin who owns its course (BR-student-lifecycle). Either alone would
 * shut out the other. The student herself is not admitted.
 *
 * <p>Reads {@code studentId}; both checks in {@link StudentSecurityService} fail closed, so an id that names no
 * student is denied.
 */
@Component
public class StudentAdministratorAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final StudentSecurityService studentSecurityService;


    public StudentAdministratorAuthorizationManager(StudentSecurityService studentSecurityService) {
        this.studentSecurityService = studentSecurityService;
    }

    @Override
    public @Nullable AuthorizationResult authorize(Supplier<? extends @Nullable Authentication> authentication, RequestAuthorizationContext context) {
        Integer studentId = PathVariables.readId(context, "studentId");
        if (studentId == null) {
            return new AuthorizationDecision(false);
        }
        return new AuthorizationDecision(
                this.studentSecurityService.isCurrentUserInstructorOfStudentSection(studentId)
                        || this.studentSecurityService.isCurrentUserAdminOfStudentCourse(studentId));
    }

}
