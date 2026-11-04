package team.projectpulse.security.authorizationmanagers;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import team.projectpulse.student.StudentSecurityService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class StudentAdministratorAuthorizationManagerTest {

    @Mock
    StudentSecurityService studentSecurityService;

    @InjectMocks
    StudentAdministratorAuthorizationManager manager;


    private AuthorizationResult authorize(Map<String, String> variables) {
        return this.manager.authorize(() -> null, new RequestAuthorizationContext(mock(HttpServletRequest.class), variables));
    }

    @Test
    @DisplayName("Admits an instructor assigned to the student's course section")
    void admitsAssignedInstructor() {
        given(this.studentSecurityService.isCurrentUserInstructorOfStudentSection(4)).willReturn(true);

        assertThat(authorize(Map.of("studentId", "4")).isGranted()).isTrue();
    }

    @Test
    @DisplayName("Admits the owning course admin who is not assigned to the course section")
    void admitsOwningCourseAdmin() {
        given(this.studentSecurityService.isCurrentUserInstructorOfStudentSection(4)).willReturn(false);
        given(this.studentSecurityService.isCurrentUserAdminOfStudentCourse(4)).willReturn(true);

        assertThat(authorize(Map.of("studentId", "4")).isGranted()).isTrue();
    }

    @Test
    @DisplayName("Denies everyone else, the student herself included: self is never asked")
    void deniesEveryoneElse() {
        given(this.studentSecurityService.isCurrentUserInstructorOfStudentSection(4)).willReturn(false);
        given(this.studentSecurityService.isCurrentUserAdminOfStudentCourse(4)).willReturn(false);

        assertThat(authorize(Map.of("studentId", "4")).isGranted()).isFalse();
        verify(this.studentSecurityService, never()).isStudentSelf(4);
    }

    @Test
    @DisplayName("Denies when the route bound no studentId")
    void deniesWithoutStudentId() {
        assertThat(authorize(Map.of()).isGranted()).isFalse();
        verifyNoInteractions(this.studentSecurityService);
    }

}
