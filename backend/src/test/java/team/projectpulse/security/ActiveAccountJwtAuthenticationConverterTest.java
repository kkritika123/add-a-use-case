package team.projectpulse.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import team.projectpulse.student.Student;
import team.projectpulse.user.UserRepository;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ActiveAccountJwtAuthenticationConverterTest {

    @Mock
    UserRepository userRepository;

    @Mock
    Converter<Jwt, AbstractAuthenticationToken> delegate;

    private Jwt jwtFor(Object userId) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "RS256")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).subject("someone");
        if (userId != null) builder.claim("userId", userId);
        else builder.claim("other", "x");
        return builder.build();
    }

    private ActiveAccountJwtAuthenticationConverter converter() {
        return new ActiveAccountJwtAuthenticationConverter(this.delegate, this.userRepository);
    }

    @Test
    @DisplayName("An enabled account is converted by the wrapped converter, unchanged")
    void enabledAccountDelegates() {
        Jwt jwt = jwtFor(4L);
        JwtAuthenticationToken expected = new JwtAuthenticationToken(jwt);
        given(this.userRepository.findById(4)).willReturn(Optional.of(new Student("j", "John", "Smith", "j@x", "p", true, "student")));
        given(this.delegate.convert(jwt)).willReturn(expected);

        assertThat(converter().convert(jwt)).isSameAs(expected);
    }

    @Test
    @DisplayName("A disabled account is refused")
    void disabledAccountIsRefused() {
        given(this.userRepository.findById(4)).willReturn(Optional.of(new Student("j", "John", "Smith", "j@x", "p", false, "student")));

        assertThatThrownBy(() -> converter().convert(jwtFor(4L))).isInstanceOf(DisabledException.class);
        verifyNoInteractions(this.delegate);
    }

    @Test
    @DisplayName("An account that no longer exists is refused")
    void missingAccountIsRefused() {
        given(this.userRepository.findById(4)).willReturn(Optional.empty());

        assertThatThrownBy(() -> converter().convert(jwtFor(4L))).isInstanceOf(DisabledException.class);
    }

    @Test
    @DisplayName("A token without a userId claim is refused")
    void tokenWithoutUserIdIsRefused() {
        assertThatThrownBy(() -> converter().convert(jwtFor(null))).isInstanceOf(DisabledException.class);
        verifyNoInteractions(this.userRepository);
    }

}
